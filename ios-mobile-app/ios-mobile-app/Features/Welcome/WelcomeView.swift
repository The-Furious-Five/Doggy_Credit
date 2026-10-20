import AuthenticationServices
import SwiftUI

/// Momento de marca: qué es DoggyCredit en el iPhone y cómo entrar.
/// Explica a dónde lleva el botón; las credenciales solo se escriben en Keycloak.
struct WelcomeView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.webAuthenticationSession) private var webAuthenticationSession
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    private var usesInlineActions: Bool {
        dynamicTypeSize.isAccessibilitySize || horizontalSizeClass == .regular
    }

    var body: some View {
        @Bindable var app = app
        ZStack {
            Color.brandSurface.ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: 28) {
                    header
                        .padding(.horizontal, 24)
                    if dynamicTypeSize.isAccessibilitySize { actions }
                    highlights
                        .padding(.horizontal, 24)
                    if usesInlineActions && !dynamicTypeSize.isAccessibilitySize { actions }
                }
                .padding(.top, 24)
                .padding(.bottom, 24)
                .frame(maxWidth: 560, alignment: .leading)
                .frame(maxWidth: .infinity)
            }
            .scrollBounceBehavior(.basedOnSize)
            .safeAreaInset(edge: .bottom, spacing: 0) {
                if !usesInlineActions {
                    actions
                        .frame(maxWidth: .infinity)
                        .background(Brand.pine)
                }
            }
        }
        .environment(\.colorScheme, .dark)
        .alert("No pudimos continuar", isPresented: Binding(
            get: { app.notice != nil },
            set: { if !$0 { app.notice = nil } }
        )) {
            Button("Entendido", role: .cancel) {}
        } message: {
            Text(app.notice ?? "")
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 18) {
            BrandMark(size: 76)
            Wordmark(size: 30, onBrandSurface: true)
            Text("Tu oficina de crédito, en el bolsillo.")
                .font(.largeTitle.bold())
                .foregroundStyle(Brand.ivory)
                .fixedSize(horizontal: false, vertical: true)
            Text("Evalúa a tus clientes donde estén y resuelve los casos en revisión entre visitas.")
                .font(.body)
                .foregroundStyle(Brand.ivory.opacity(0.78))
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var highlights: some View {
        VStack(alignment: .leading, spacing: 18) {
            HighlightRow(symbol: "figure.walk.motion", title: "Evaluación en campo",
                         detail: "Consentimiento, fuentes y score con sus razones.")
            HighlightRow(symbol: "tray.full.fill", title: "Bandeja de revisión",
                         detail: "Aprueba o rechaza con justificación.")
            HighlightRow(symbol: "person.text.rectangle.fill", title: "Perfil Financiero 360",
                         detail: "Buró, banco y billetera en una sola vista.")
        }
    }

    private var actions: some View {
        DCGlassGroup(spacing: 12) {
            VStack(spacing: 12) {
                Button {
                    Task {
                        await app.signIn { url in
                            try await webAuthenticationSession.authenticate(
                                using: url,
                                callbackURLScheme: AuthConfiguration.callbackScheme,
                                preferredBrowserSession: .ephemeral
                            )
                        }
                    }
                } label: {
                    HStack {
                        if app.isSigningIn { ProgressView().tint(Brand.pine) }
                        Text(app.isSigningIn ? "Abriendo acceso seguro…" : "Iniciar sesión con tu institución")
                    }
                    .font(.headline)
                    .foregroundStyle(Brand.pine)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
                }
                .dcProminentButtonStyle()
                .tint(Brand.ivory)
                .controlSize(.large)
                .disabled(app.isSigningIn)
                .accessibilityIdentifier("welcome.sign-in")

                if dynamicTypeSize.isAccessibilitySize {
                    DisclosureGroup {
                        serverLabel
                            .padding(.top, 4)
                    } label: {
                        Label("Servidor", systemImage: "server.rack")
                    }
                    .font(.footnote)
                    .tint(Brand.ivory)
                    .foregroundStyle(Brand.ivory.opacity(0.7))
                    .accessibilityIdentifier("welcome.connection")
                } else {
                    Label { serverLabel } icon: { Image(systemName: "server.rack") }
                        .font(.footnote)
                        .foregroundStyle(Brand.ivory.opacity(0.7))
                        .padding(.top, 4)
                }
            }
        }
        .padding(.horizontal, 24)
        .padding(.top, 16)
        .padding(.bottom, 12)
        .frame(maxWidth: 560)
    }

    private var serverLabel: some View {
        Text(app.server.host() ?? app.server.absoluteString)
            .font(.footnote)
            .foregroundStyle(Brand.ivory.opacity(0.7))
            .fixedSize(horizontal: false, vertical: true)
    }
}

private struct HighlightRow: View {
    let symbol: String
    let title: String
    let detail: String
    @ScaledMetric(relativeTo: .title3) private var iconWidth: CGFloat = 30

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            Image(systemName: symbol)
                .font(.title3)
                .foregroundStyle(Brand.honey)
                .frame(width: iconWidth)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.headline)
                    .foregroundStyle(Brand.ivory)
                Text(detail)
                    .font(.subheadline)
                    .foregroundStyle(Brand.ivory.opacity(0.7))
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .accessibilityElement(children: .combine)
    }
}

