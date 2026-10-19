import SwiftUI

/// Bandeja de revisión manual: casos que las políticas dejaron para una persona.
/// Desliza para aprobar o rechazar; siempre con justificación.
struct ReviewQueueView: View {
    @Environment(SessionStore.self) private var session
    @Environment(AppRouter.self) private var router

    private var queue: ReviewQueueModel { session.reviewQueue }

    var body: some View {
        @Bindable var router = router
        @Bindable var queue = queue
        NavigationStack(path: $router.reviewPath) {
            content
                .navigationTitle("Revisión")
                .dcNavigationSubtitle(subtitle)
                .toolbar {
                    SessionToolbar()
                    ToolbarItem(placement: .topBarTrailing) {
                        Menu {
                            Picker("Mostrar", selection: $queue.filter) {
                                ForEach(ReviewQueueModel.Filter.allCases) { filter in
                                    Text(filter.rawValue).tag(filter)
                                }
                            }
                        } label: {
                            Label("Filtrar", systemImage: queue.filter == .all
                                  ? "line.3.horizontal.decrease"
                                  : "line.3.horizontal.decrease.circle.fill")
                        }
                    }
                }
                .navigationDestination(for: Route.self) { RouteDestination(route: $0) }
                .refreshable { await queue.refresh() }
                .task { await queue.loadIfNeeded() }
        }
    }

    private var subtitle: String? {
        guard queue.phase == .loaded else { return nil }
        let total = queue.total == 1 ? "1 caso pendiente" : "\(Format.number(queue.total)) casos pendientes"
        guard queue.filter != .all else { return total }
        return "\(queue.filter.rawValue): \(Format.number(queue.visibleCases.count)) de \(total)"
    }

    @ViewBuilder
    private var content: some View {
        switch queue.phase {
        case .idle:
            LoadingView(label: "Cargando casos…")
        case .loading where queue.cases.isEmpty:
            LoadingView(label: "Cargando casos…")
        case let .failed(error) where queue.cases.isEmpty:
            if error.isForbidden {
                ContentUnavailableView(
                    "Tu rol no resuelve casos en revisión",
                    systemImage: "lock.fill",
                    description: Text("Pide a una persona analista o a quien administra tu institución que los revise.")
                )
            } else {
                ErrorStateView(error: error, subject: "la bandeja") { await queue.refresh() }
            }
        default:
            if queue.cases.isEmpty {
                ContentUnavailableView(
                    "Sin casos pendientes",
                    systemImage: "checkmark.seal",
                    description: Text("Cuando una política deje una evaluación en revisión manual, aparecerá aquí.")
                )
            } else {
                list
            }
        }
    }

    private var list: some View {
        List {
            Section {
                if queue.visibleCases.isEmpty {
                    ContentUnavailableView(
                        "Sin casos con este filtro",
                        systemImage: "line.3.horizontal.decrease.circle",
                        description: Text("Elige otro filtro para ver los demás casos pendientes.")
                    )
                }
                ForEach(queue.visibleCases) { reviewCase in
                    Button {
                        session.resolve(reviewCase)
                    } label: {
                        ReviewCaseRow(reviewCase: reviewCase)
                    }
                    .tint(.primary)
                    // Deslizar abre la hoja con la decisión elegida: no hay acción "completa" sin justificación.
                    .swipeActions(edge: .leading, allowsFullSwipe: false) {
                        Button {
                            session.resolve(reviewCase, preset: .approve)
                        } label: {
                            Label("Aprobar", systemImage: "checkmark")
                        }
                        .tint(Tone.success.color)
                    }
                    .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                        Button {
                            session.resolve(reviewCase, preset: .reject)
                        } label: {
                            Label("Rechazar", systemImage: "xmark")
                        }
                        .tint(Tone.danger.color)
                    }
                    .contextMenu {
                        Button("Resolver caso", systemImage: "checkmark.circle") { session.resolve(reviewCase) }
                        Button("Ver perfil del cliente", systemImage: "person.text.rectangle") {
                            router.reviewPath.append(.client(reviewCase.applicantId))
                        }
                        Button("Ver evaluación", systemImage: "doc.text.magnifyingglass") {
                            router.reviewPath.append(.assessment(reviewCase.assessmentId))
                        }
                    } preview: {
                        ReviewCaseRow(reviewCase: reviewCase)
                            .padding()
                            .frame(width: 360)
                            .environment(session)
                    }
                    .task { await queue.loadMoreIfNeeded(after: reviewCase) }
                }
                if queue.isLoadingMore {
                    ProgressView().frame(maxWidth: .infinity)
                }
            } footer: {
                Text("Del más antiguo al más reciente. Aprobar o rechazar lo decide una persona y queda en la auditoría.")
            }
        }
        .listStyle(.insetGrouped)
        .animation(.default, value: queue.visibleCases)
    }
}

/// Fila de la bandeja: score, cliente y antigüedad, motivo y solicitud.
/// Siempre las mismas tres líneas, como una conversación pendiente.
struct ReviewCaseRow: View {
    let reviewCase: ReviewCase
    @Environment(SessionStore.self) private var session
    @ScaledMetric(relativeTo: .body) private var ringSize: CGFloat = 44

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            ScoreRing(score: reviewCase.score, band: reviewCase.band, diameter: min(ringSize, 72), showsBand: false)
            VStack(alignment: .leading, spacing: 3) {
                RowHeadline(title: session.privacy.document(reviewCase.document),
                            detail: Format.relative(reviewCase.createdAt), isDocument: true)
                StatusLine(text: reviewCase.reviewReason, tone: reviewCase.reviewReasonTone)
                Text(request)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .combine)
        .accessibilityHint("Abre la resolución del caso")
    }

    /// "Banda D · Bs 20.000 · 36 meses" (el monto se omite si está oculto).
    private var request: String {
        var parts = ["Banda \(reviewCase.band.rawValue)"]
        if let amount = reviewCase.requestedAmount,
           let visible = session.privacy.visibleAmount(Format.bolivianos(amount)) {
            parts.append(visible)
        }
        parts.append(Format.months(reviewCase.termMonths))
        return parts.joined(separator: " · ")
    }
}
