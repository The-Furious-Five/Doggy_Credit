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
    var filter: Filter = .all

    private let service: any DoggyCreditService
    private let pageSize = 20
    private var loadedPages = 0

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
        if cases.isEmpty { phase = .loading }
        do {
            let page = try await service.reviewCases(page: 1, size: pageSize)
            cases = page.items
            products = page.products
            total = page.total
            loadedPages = 1
            phase = .loaded
        } catch {
            let error = APIError(error)
            guard !error.isCancellation else { return }
            phase = .failed(error)
        }
    }

    func loadMoreIfNeeded(after item: ReviewCase) async {
        guard hasMore, !isLoadingMore, item.id == cases.last?.id else { return }
        isLoadingMore = true
        defer { isLoadingMore = false }
        do {
            let page = try await service.reviewCases(page: loadedPages + 1, size: pageSize)
            let known = Set(cases.map(\.id))
            cases.append(contentsOf: page.items.filter { !known.contains($0.id) })
            products = page.products
            total = page.total
            loadedPages += 1
        } catch {
            // La página siguiente se reintenta al volver a llegar al final.
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
