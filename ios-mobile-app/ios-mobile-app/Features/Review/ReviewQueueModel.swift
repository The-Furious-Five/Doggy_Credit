import Foundation

/// Bandeja de revisión manual (RF-DEC-03): los casos que las políticas dejaron para una
/// persona, del más antiguo al más reciente. Alimenta también el carrusel de Inicio y
/// el contador de la pestaña.
@Observable
final class ReviewQueueModel {
    enum Filter: String, CaseIterable, Identifiable {
        case all = "Todos"
        case manualReview = "Revisión manual"
        case requestData = "Solicitar datos"
        case partial = "Resultado parcial"

        var id: String { rawValue }
    }

    private(set) var cases: [ReviewCase] = []
    private(set) var products: [Product] = []
    private(set) var total = 0
    private(set) var phase: LoadPhase = .idle
    private(set) var isLoadingMore = false
    private(set) var isRefreshing = false
    private(set) var loadMoreError: APIError?
    private(set) var lastUpdatedAt: Date?
    var filter: Filter = .all

    private let service: any DoggyCreditService
    private let pageSize = 20
    private var loadedPages = 0
    private var generation = UUID()

    init(service: any DoggyCreditService) {
        self.service = service
    }

    var visibleCases: [ReviewCase] {
        switch filter {
        case .all: cases
        case .manualReview: cases.filter { $0.motive == .manualReview }
        case .requestData: cases.filter { $0.motive == .requestData }
        case .partial: cases.filter(\.partialResult)
        }
    }

    var hasMore: Bool { cases.count < total }

    func loadIfNeeded() async {
        guard phase == .idle else { return }
        await refresh()
    }

    func refresh() async {
        let request = UUID()
        generation = request
        isRefreshing = true
        isLoadingMore = false
        loadMoreError = nil
        phase = cases.isEmpty ? .loading : .loaded
        defer { if generation == request { isRefreshing = false } }
        do {
            let page = try await service.reviewCases(page: 1, size: pageSize)
            guard generation == request else { return }
            cases = page.items
            products = page.products
            total = page.total
            loadedPages = 1
            phase = .loaded
            lastUpdatedAt = .now
        } catch {
            guard generation == request else { return }
            let error = APIError(error)
            if error.isCancellation {
                phase = cases.isEmpty ? .idle : .loaded
                return
            }
            if error.isForbidden { cases = []; products = []; total = 0; lastUpdatedAt = nil }
            phase = .failed(error)
        }
    }

    func loadMoreIfNeeded(after item: ReviewCase) async {
        guard item.id == visibleCases.last?.id, loadMoreError == nil else { return }
        await loadMore()
    }

    /// Disponible incluso cuando el filtro no muestra ninguna fila de la página actual.
    func loadMore() async {
        guard hasMore, !isLoadingMore, !isRefreshing else { return }
        let request = generation
        let nextPage = loadedPages + 1
        isLoadingMore = true
        loadMoreError = nil
        defer { if generation == request { isLoadingMore = false } }
        do {
            let page = try await service.reviewCases(page: nextPage, size: pageSize)
            guard generation == request else { return }
            let known = Set(cases.map(\.id))
            cases.append(contentsOf: page.items.filter { !known.contains($0.id) })
            products = page.products
            total = page.total
            loadedPages = nextPage
        } catch {
            guard generation == request else { return }
            let error = APIError(error)
            if error.isForbidden {
                cases = []
                products = []
                total = 0
                lastUpdatedAt = nil
                phase = .failed(error)
            } else if !error.isCancellation {
                loadMoreError = error
            }
        }
    }

    /// El caso ya no está pendiente (resuelto aquí o por otra persona).
    func remove(_ id: UUID) {
        guard let index = cases.firstIndex(where: { $0.id == id }) else { return }
        cases.remove(at: index)
        total = max(0, total - 1)
    }

    func makeResolution(for reviewCase: ReviewCase, preset: ResolutionDecision?) -> ResolveCaseModel {
        ResolveCaseModel(
            reviewCase: reviewCase, products: products, preset: preset, service: service,
            onResolved: { [weak self] id in self?.remove(id) },
            reloadProducts: { [weak self] in
                await self?.refresh()
                return self?.products ?? []
            }
        )
    }
}
