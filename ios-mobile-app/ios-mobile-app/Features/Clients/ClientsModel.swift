import Foundation

/// Cartera del tenant, del cliente más reciente al más antiguo, con filtro por segmento.
@Observable
final class ClientsModel {
    private(set) var applicants: [Applicant] = []
    private(set) var total = 0
    private(set) var phase: LoadPhase = .idle
    private(set) var isLoadingMore = false
    private(set) var segment: Segment?

    private let service: any DoggyCreditService
    private let pageSize = 25
    private var loadedPages = 0

    init(service: any DoggyCreditService) {
        self.service = service
    }

    var hasMore: Bool { applicants.count < total }

    func loadIfNeeded() async {
        guard phase == .idle else { return }
        await refresh()
    }

    func select(_ segment: Segment?) async {
        guard segment != self.segment else { return }
        self.segment = segment
        applicants = []
        await refresh()
    }

    func refresh() async {
        if applicants.isEmpty { phase = .loading }
        do {
            let page = try await service.applicants(page: 1, size: pageSize, segment: segment, document: nil)
            applicants = page.items
            total = page.total
            loadedPages = 1
            phase = .loaded
        } catch {
            let error = APIError(error)
            guard !error.isCancellation else { return }
            phase = .failed(error)
        }
    }

    func loadMoreIfNeeded(after applicant: Applicant) async {
        guard hasMore, !isLoadingMore, applicant.id == applicants.last?.id else { return }
        isLoadingMore = true
        defer { isLoadingMore = false }
        guard let page = try? await service.applicants(page: loadedPages + 1, size: pageSize, segment: segment, document: nil)
        else { return }
        let known = Set(applicants.map(\.id))
        applicants.append(contentsOf: page.items.filter { !known.contains($0.id) })
        total = page.total
        loadedPages += 1
    }
}

/// Búsqueda exacta por documento (CI o NIT). El servidor compara por HMAC: no hay
/// coincidencias parciales, y el documento nunca viaja en la URL.
@Observable
final class ClientSearchModel {
    enum State: Equatable {
        case idle
        case searching
        case found(Applicant)
        case notFound(String)
        case failed(APIError)
    }

    var query = ""
    private(set) var state: State = .idle
    private let service: any DoggyCreditService

    init(service: any DoggyCreditService) {
        self.service = service
    }

    var normalizedQuery: String {
        query.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
    }

    var canSearch: Bool { normalizedQuery.count >= 3 }

    func search() async {
        let document = normalizedQuery
        guard canSearch else { return }
        state = .searching
        do {
            let page = try await service.applicants(page: 1, size: 1, segment: nil, document: document)
            state = page.items.first.map(State.found) ?? .notFound(document)
        } catch {
            let error = APIError(error)
            state = error.isCancellation ? .idle : .failed(error)
        }
    }

    func clear() {
        state = .idle
    }
}
