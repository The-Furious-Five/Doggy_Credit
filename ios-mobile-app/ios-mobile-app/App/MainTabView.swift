import SwiftUI

/// Las cuatro áreas del día a día y la búsqueda. En iPad la barra se vuelve barra lateral.
struct MainTabView: View {
    @Environment(SessionStore.self) private var session
    @Environment(AppRouter.self) private var router

    var body: some View {
        @Bindable var router = router
        TabView(selection: $router.selectedTab) {
            Tab("Inicio", systemImage: "house.fill", value: AppRouter.Tab.home) {
                HomeView()
            }
            Tab("Revisión", systemImage: "tray.full.fill", value: AppRouter.Tab.review) {
                ReviewQueueView()
            }
            .badge(session.reviewQueue.total)
            Tab("Clientes", systemImage: "person.2.fill", value: AppRouter.Tab.clients) {
                ClientsView()
            }
            Tab("Analítica", systemImage: "chart.bar.xaxis", value: AppRouter.Tab.analytics) {
                AnalyticsView()
            }
            Tab("Buscar", systemImage: "magnifyingglass", value: AppRouter.Tab.search, role: .search) {
                ClientSearchView()
            }
        }
        .tabViewStyle(.sidebarAdaptable)
        .dcMinimizeTabBarOnScroll()
        .modifier(EvaluationAccessoryModifier(isEnabled: session.evaluation.isInProgress && !router.isEvaluationPresented))
        .sheet(isPresented: $router.isEvaluationPresented) {
            EvaluationFlowView()
        }
        .sheet(item: $router.resolution) { model in
            ResolveCaseView(model: model)
        }
        .sheet(isPresented: $router.isAccountPresented) {
            AccountView()
        }
        .sensoryFeedback(.success, trigger: router.completedResolutions)
        .task {
            #if DEBUG
            await DebugLaunchOptions.apply(to: session)
            #endif
            await session.bootstrap()
        }
    }
}

/// Toolbar común de las pestañas: cuenta a la izquierda y nueva evaluación a la derecha.
struct SessionToolbar: ToolbarContent {
    @Environment(SessionStore.self) private var session

    var body: some ToolbarContent {
        if #available(iOS 26, *) {
            // Como en Música o GitHub: el avatar se ve como tal, sin la cápsula de vidrio.
            ToolbarItem(placement: .topBarLeading) { accountButton }
                .sharedBackgroundVisibility(.hidden)
        } else {
            ToolbarItem(placement: .topBarLeading) { accountButton }
        }
        ToolbarItem(placement: .topBarTrailing) {
            Button {
                session.startEvaluation()
            } label: {
                Label("Nueva evaluación", systemImage: "plus")
                    .fontWeight(.semibold)
            }
            .dcProminentButtonStyle()
            .buttonBorderShape(.circle)
            .accessibilityHint("Evalúa a un cliente: datos, consentimiento, fuentes y resultado.")
        }
    }

    private var accountButton: some View {
        Button {
            session.router.isAccountPresented = true
        } label: {
            AvatarView(initials: session.user.initials, size: 36)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Cuenta de \(session.user.name)")
    }
}

/// Accesorio sobre la barra de pestañas, como el mini reproductor de Música (iOS 26.1+).
/// En versiones anteriores Inicio muestra el mismo borrador como tarjeta.
private struct EvaluationAccessoryModifier: ViewModifier {
    let isEnabled: Bool

    func body(content: Content) -> some View {
        if #available(iOS 26.1, *) {
            content.tabViewBottomAccessory(isEnabled: isEnabled) {
                PlacementAwareEvaluationAccessory()
            }
        } else {
            content
        }
    }
}

@available(iOS 26.1, *)
private struct PlacementAwareEvaluationAccessory: View {
    @Environment(\.tabViewBottomAccessoryPlacement) private var placement

    var body: some View {
        EvaluationAccessory(isCompact: placement == .inline)
    }
}

/// "Evaluación en curso": retoma el borrador sin perder lo que el cliente ya dictó.
struct EvaluationAccessory: View {
    var isCompact = false
    @Environment(SessionStore.self) private var session
    @State private var isConfirmingDiscard = false

    private var flow: EvaluationFlowModel { session.evaluation }

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "doc.text.magnifyingglass")
                .font(.title3)
                .foregroundStyle(Color.accentColor)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 1) {
                Text(flow.trimmedDocument.isEmpty ? "Evaluación en curso" : session.privacy.document(flow.trimmedDocument))
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
                if !isCompact {
                    Text("Paso \(flow.step.rawValue + 1) de \(EvaluationFlowModel.Step.allCases.count) · \(flow.step.title)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }
            Spacer(minLength: 4)
            Button {
                session.router.isEvaluationPresented = true
            } label: {
                Image(systemName: "arrow.up.forward")
                    .fontWeight(.semibold)
            }
            .accessibilityLabel("Continuar evaluación")
            if !isCompact {
                Button {
                    isConfirmingDiscard = true
                } label: {
                    Image(systemName: "xmark")
                }
                .accessibilityLabel("Descartar evaluación")
            }
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 16)
        .contentShape(.rect)
        .onTapGesture { session.router.isEvaluationPresented = true }
        .confirmationDialog("¿Descartar la evaluación en curso?", isPresented: $isConfirmingDiscard, titleVisibility: .visible) {
            Button("Descartar evaluación", role: .destructive) { flow.restart() }
        } message: {
            Text("Se borran los datos escritos. Lo que ya se registró (solicitante y consentimiento) se conserva en el servidor.")
        }
    }
}
