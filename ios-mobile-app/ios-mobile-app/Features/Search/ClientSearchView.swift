import SwiftUI

/// "El cliente llega y dicta su CI": búsqueda exacta por documento. Si no existe,
/// se evalúa como cliente nuevo con el documento ya escrito.
///
/// Las búsquedas no se guardan en el dispositivo: un documento es un dato personal.
struct ClientSearchView: View {
    static let prompt = Text("CI o NIT exacto")

    @Environment(SessionStore.self) private var session
    @Environment(AppRouter.self) private var router

    private var search: ClientSearchModel { session.search }

    var body: some View {
        @Bindable var router = router
        @Bindable var search = search
        NavigationStack(path: $router.searchPath) {
            content
                .navigationTitle("Buscar")
                .navigationDestination(for: Route.self) { RouteDestination(route: $0) }
                .searchable(text: $search.query, prompt: Self.prompt)
                .textInputAutocapitalization(.characters)
                .autocorrectionDisabled()
                .onSubmit(of: .search) { Task { await search.search() } }
        }
    }

    @ViewBuilder
    private var content: some View {
        switch search.state {
        case .idle:
            ContentUnavailableView {
                Label("Busca por documento", systemImage: "person.text.rectangle")
            } description: {
                Text("Escribe el CI o NIT completo del cliente.")
            }
        case .searching:
            LoadingView(label: "Buscando…")
        case let .found(applicant):
            List {
                Section("Resultado") {
                    NavigationLink(value: Route.client(applicant.id)) {
                        ClientRow(applicant: applicant)
                    }
                }
                Section {
                    Button {
                        session.startEvaluation(document: search.normalizedQuery, applicant: applicant)
                    } label: {
                        Label("Reevaluar a este cliente", systemImage: "arrow.clockwise")
                    }
                }
            }
            .listStyle(.insetGrouped)
        case let .notFound(document):
            ContentUnavailableView {
                Label("Sin cliente con ese documento", systemImage: "person.crop.circle.badge.questionmark")
            } description: {
                Text("No hay ningún cliente registrado como \(session.privacy.document(document)) en tu institución.")
            } actions: {
                Button("Evaluar como cliente nuevo") {
                    session.startEvaluation(document: document)
                }
                .dcProminentButtonStyle()
            }
        case let .failed(error):
            ErrorStateView(error: error, subject: "la búsqueda") { await search.search() }
        }
    }
}

