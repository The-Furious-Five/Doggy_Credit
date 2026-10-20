import Foundation

/// Cartera del tenant, del cliente más reciente al más antiguo, con filtro por segmento.
@Observable
final class ClientsModel {
    private(set) var applicants: [Applicant] = []
    private(set) var total = 0
    private(set) var phase: LoadPhase = .idle
    private(set) var isLoadingMore = false
    private(set) var isRefreshing = false
    private(set) var loadMoreError: APIError?
    private(set) var lastUpdatedAt: Date?
    private(set) var segment: Segment?

    private let service: any DoggyCreditService
    private let pageSize = 25
    private var loadedPages = 0
    private var generation = UUID()

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
        total = 0
        lastUpdatedAt = nil
        await refresh()
    }

    func refresh() async {
        let request = UUID()
        generation = request
        let requestedSegment = segment
        isRefreshing = true
        isLoadingMore = false
        loadMoreError = nil
        phase = applicants.isEmpty ? .loading : .loaded
        defer { if generation == request { isRefreshing = false } }
        do {
            let page = try await service.applicants(page: 1, size: pageSize, segment: requestedSegment, document: nil)
            guard generation == request else { return }
            applicants = page.items
            total = page.total
            loadedPages = 1
            phase = .loaded
            lastUpdatedAt = .now
        } catch {
            guard generation == request else { return }
            let error = APIError(error)
            if error.isCancellation {
                phase = applicants.isEmpty ? .idle : .loaded
                return
            }
            if error.isForbidden { applicants = []; total = 0; lastUpdatedAt = nil }
            phase = .failed(error)
        }
    }

    func loadMoreIfNeeded(after applicant: Applicant) async {
        guard applicant.id == applicants.last?.id, loadMoreError == nil else { return }
        await loadMore()
    }

    func loadMore() async {
        guard hasMore, !isLoadingMore, !isRefreshing else { return }
        let request = generation
        let nextPage = loadedPages + 1
        let requestedSegment = segment
        isLoadingMore = true
        loadMoreError = nil
        defer { if generation == request { isLoadingMore = false } }
        do {
            let page = try await service.applicants(page: nextPage, size: pageSize, segment: requestedSegment, document: nil)
            guard generation == request else { return }
            let known = Set(applicants.map(\.id))
            applicants.append(contentsOf: page.items.filter { !known.contains($0.id) })
            total = page.total
            loadedPages = nextPage
        } catch {
            guard generation == request else { return }
            let error = APIError(error)
            if error.isForbidden {
                applicants = []
                total = 0
                lastUpdatedAt = nil
                phase = .failed(error)
            } else if !error.isCancellation {
                loadMoreError = error
            }
        }
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

    var query = "" {
        didSet {
            if Self.normalize(oldValue) != normalizedQuery { clear() }
        }
    }
    private(set) var state: State = .idle
    private let service: any DoggyCreditService
    private var requestID = UUID()

    init(service: any DoggyCreditService) {
        self.service = service
    }

    var normalizedQuery: String {
        Self.normalize(query)
    }

    var canSearch: Bool { normalizedQuery.count >= 3 }

    func search() async {
        let document = normalizedQuery
        guard canSearch else { return }
        let request = UUID()
        requestID = request
        state = .searching
        do {
            let page = try await service.applicants(page: 1, size: 1, segment: nil, document: document)
            guard requestID == request, normalizedQuery == document else { return }
            state = page.items.first.map(State.found) ?? .notFound(document)
        } catch {
            guard requestID == request, normalizedQuery == document else { return }
            let error = APIError(error)
            state = error.isCancellation ? .idle : .failed(error)
        }
    }

    func clear() {
        requestID = UUID()
        state = .idle
    }

    private static func normalize(_ document: String) -> String {
        document.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
    }
}
