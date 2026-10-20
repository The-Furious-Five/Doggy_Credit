import SwiftUI

/// Cartera de la institución, del cliente más reciente al más antiguo.
struct ClientsView: View {
    @Environment(SessionStore.self) private var session
    @Environment(AppRouter.self) private var router

    private var clients: ClientsModel { session.clients }

    var body: some View {
        @Bindable var router = router
        NavigationStack(path: $router.clientsPath) {
            content
                .navigationTitle("Clientes")
                .dcNavigationSubtitle(clients.phase == .loaded ? "\(Format.number(clients.total)) en tu cartera" : nil)
                .toolbar {
                    SessionToolbar()
                    ToolbarItem(placement: .topBarTrailing) { segmentMenu }
                }
                .navigationDestination(for: Route.self) { RouteDestination(route: $0) }
                .refreshable { await clients.refresh() }
                .task { await clients.loadIfNeeded() }
        }
    }

    private var segmentMenu: some View {
        Menu {
            Picker("Segmento", selection: Binding(
                get: { clients.segment },
                set: { segment in Task { await clients.select(segment) } }
            )) {
                Label("Todos", systemImage: "person.2").tag(Segment?.none)
                ForEach(Segment.allCases) { segment in
                    Label(segment.label, systemImage: segment.symbol).tag(Segment?.some(segment))
                }
            }
        } label: {
            Label("Segmento", systemImage: clients.segment == nil
                  ? "line.3.horizontal.decrease"
                  : "line.3.horizontal.decrease.circle.fill")
        }
    }

    @ViewBuilder
    private var content: some View {
        switch clients.phase {
        case .idle:
            LoadingView(label: "Cargando clientes…")
        case .loading where clients.applicants.isEmpty:
            LoadingView(label: "Cargando clientes…")
        case let .failed(error) where clients.applicants.isEmpty:
            ErrorStateView(error: error, subject: "los clientes") { await clients.refresh() }
        default:
            if clients.applicants.isEmpty {
                ContentUnavailableView {
                    Label(clients.segment == nil ? "Todavía no hay clientes" : "Sin clientes en este segmento",
                          systemImage: "person.2.slash")
                } description: {
                    Text("Los clientes aparecen al registrarlos en una evaluación.")
                } actions: {
                    Button("Nueva evaluación") { session.startEvaluation() }
                        .dcProminentButtonStyle()
                }
            } else {
                List {
                    Section {
                        RefreshStatusView(isRefreshing: clients.isRefreshing, lastUpdatedAt: clients.lastUpdatedAt,
                                          error: clients.phase.error) { await clients.refresh() }
                    }
                    if let segment = clients.segment {
                        Section {
                            Label("Mostrando: \(segment.label)", systemImage: segment.symbol)
                                .foregroundStyle(.secondary)
                        }
                    }
                    Section {
                        ForEach(clients.applicants) { applicant in
                            NavigationLink(value: Route.client(applicant.id)) {
                                ClientRow(applicant: applicant)
                            }
                            .task { await clients.loadMoreIfNeeded(after: applicant) }
                        }
                        if clients.hasMore {
                            PaginationFooter(isLoading: clients.isLoadingMore, error: clients.loadMoreError) {
                                await clients.loadMore()
                            }
                            .disabled(clients.isRefreshing)
                        }
                    }
                }
                .listStyle(.insetGrouped)
            }
        }
    }
}

struct ClientRow: View {
    let applicant: Applicant
    @Environment(SessionStore.self) private var session
    @ScaledMetric(relativeTo: .body) private var iconSize: CGFloat = 40

    var body: some View {
        HStack(spacing: 14) {
            IconTile(symbol: applicant.resolvedSegment.symbol, tint: applicant.resolvedSegment.tint, size: min(iconSize, 64))
            VStack(alignment: .leading, spacing: 3) {
                RowHeadline(title: session.privacy.document(applicant.document),
                            detail: Format.relative(applicant.createdAt), isDocument: true)
                Text(applicant.activity)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }
        }
        .padding(.vertical, 2)
        .accessibilityElement(children: .combine)
        .accessibilityValue(applicant.resolvedSegment.label)
    }
}
