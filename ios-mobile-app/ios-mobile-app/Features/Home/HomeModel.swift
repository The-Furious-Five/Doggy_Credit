import Foundation

/// Inicio: las evaluaciones recientes, con el cliente de cada una.
@Observable
final class HomeModel {
    private(set) var recent: Loadable<[AssessmentSummary]> = .idle
    private(set) var applicantsById: [UUID: Applicant] = [:]

    private let service: any DoggyCreditService

    init(service: any DoggyCreditService) {
        self.service = service
    }

    func loadIfNeeded() async {
        guard case .idle = recent else { return }
        await refresh()
    }

    func refresh() async {
        if recent.value == nil { recent = .loading }
        async let recentPage = service.assessments(applicantId: nil, since: nil, page: 1, size: 6)
        async let applicantsPage = service.applicants(page: 1, size: 50, segment: nil, document: nil)

        do {
            recent = .loaded(try await recentPage.items)
        } catch {
            let error = APIError(error)
            if !error.isCancellation { recent = .failed(error) }
        }
        if let applicants = try? await applicantsPage.items {
            applicantsById = Dictionary(applicants.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        }
    }
}
