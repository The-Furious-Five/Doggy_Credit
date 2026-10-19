import Foundation

/// Resolución de un caso (override) con justificación obligatoria y, al aprobar, el
/// producto que se otorga. Queda en la auditoría con el usuario que resolvió.
@Observable
final class ResolveCaseModel: Identifiable {
    /// Errores del contrato que la hoja explica por sí misma.
    enum Problem: Equatable {
        case invalid
        /// Otra persona lo resolvió mientras se revisaba (CONCURRENT_UPDATE o ya no existe).
        case alreadyResolved
        /// El producto se desactivó mientras se revisaba.
        case productUnavailable
        case forbidden
        case failed(APIError)
    }

    let reviewCase: ReviewCase
    var id: UUID { reviewCase.id }
    private(set) var products: [Product]
    var decision: ResolutionDecision?
    var productId: UUID?
    var justification = ""
    private(set) var isSaving = false
    private(set) var problem: Problem?

    private let service: any DoggyCreditService
    private let onResolved: (UUID) -> Void
    private let reloadProducts: () async -> [Product]

    init(
        reviewCase: ReviewCase, products: [Product], preset: ResolutionDecision?, service: any DoggyCreditService,
        onResolved: @escaping (UUID) -> Void, reloadProducts: @escaping () async -> [Product]
    ) {
        self.reviewCase = reviewCase
        self.products = products
        self.decision = preset
        self.service = service
        self.onResolved = onResolved
        self.reloadProducts = reloadProducts
        if products.count == 1 { productId = products.first?.id }
    }

    var trimmedJustification: String {
        justification.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var missingCharacters: Int {
        max(0, NewResolution.minimumJustificationLength - trimmedJustification.count)
    }

    var isApproving: Bool { decision == .approve }
    var canApprove: Bool { !products.isEmpty }

    /// Nada se envía si falta la decisión, el producto al aprobar o la justificación.
    var canSubmit: Bool {
        guard let decision, !isSaving, problem != .alreadyResolved, missingCharacters == 0 else { return false }
        return decision == .reject || (productId != nil && canApprove)
    }

    func submit() async -> Resolution? {
        guard canSubmit, let decision else { return nil }
        isSaving = true
        problem = nil
        defer { isSaving = false }
        do {
            let resolution = try await service.resolveCase(
                assessmentId: reviewCase.assessmentId,
                resolution: NewResolution(decision: decision, productId: isApproving ? productId : nil,
                                          justification: trimmedJustification)
            )
            onResolved(reviewCase.id)
            return resolution
        } catch {
            let error = APIError(error)
            switch error.code {
            case .concurrentUpdate, .assessmentNotFound:
                problem = .alreadyResolved
                onResolved(reviewCase.id)
            case .productNotFound:
                problem = .productUnavailable
                productId = nil
                products = await reloadProducts()
            case .validationError:
                problem = .invalid
            case _ where error.isForbidden:
                problem = .forbidden
            default:
                problem = .failed(error)
            }
            return nil
        }
    }

    func product(for id: UUID?) -> Product? {
        products.first { $0.id == id }
    }
}
