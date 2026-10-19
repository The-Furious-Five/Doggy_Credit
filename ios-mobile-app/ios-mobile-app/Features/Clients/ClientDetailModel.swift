import Foundation

/// Perfil de un cliente: datos declarados, Perfil Financiero 360, historial de
/// evaluaciones, fuentes consultadas y consentimientos.
@Observable
final class ClientDetailModel {
    let applicantId: UUID
    private(set) var detail: Loadable<ApplicantDetail> = .idle
    private(set) var consents: Loadable<[Consent]> = .idle
    private(set) var history: Loadable<[AssessmentSummary]> = .idle
    private(set) var historyTotal = 0
    private(set) var isRevoking = false
    var revokeError: APIError?

    private let service: any DoggyCreditService

    init(applicantId: UUID, service: any DoggyCreditService) {
        self.applicantId = applicantId
        self.service = service
    }

    var activeConsents: [Consent] {
        (consents.value ?? []).filter { $0.status == .active }
    }

    func load() async {
        if detail.value == nil { detail = .loading }
        async let detailRequest = service.applicant(id: applicantId)
        async let consentsRequest = service.consents(applicantId: applicantId)
        async let historyRequest = service.assessments(applicantId: applicantId, since: nil, page: 1, size: 20)

        do {
            detail = .loaded(try await detailRequest)
        } catch {
            let error = APIError(error)
            if !error.isCancellation { detail = .failed(error) }
        }
        do {
            consents = .loaded(try await consentsRequest)
        } catch {
            consents = .failed(APIError(error))
        }
        do {
            let page = try await historyRequest
            history = .loaded(page.items)
            historyTotal = page.total
        } catch {
            history = .failed(APIError(error))
        }
    }

    /// Revoca todos los consentimientos vigentes. Desde ese momento no se consulta
    /// ninguna fuente; el perfil ya consolidado se conserva.
    func revokeConsents() async -> Bool {
        guard !isRevoking else { return false }
        isRevoking = true
        revokeError = nil
        defer { isRevoking = false }
        do {
            for consent in activeConsents {
                _ = try await service.revokeConsent(id: consent.id)
            }
            consents = .loaded(try await service.consents(applicantId: applicantId))
            return true
        } catch {
            revokeError = APIError(error)
            return false
        }
    }
}

/// Una evaluación con sus razones, versión del modelo y correlación para soporte.
@Observable
final class AssessmentDetailModel {
    let assessmentId: UUID
    private(set) var assessment: Loadable<Assessment> = .idle
    private let service: any DoggyCreditService

    init(assessmentId: UUID, service: any DoggyCreditService) {
        self.assessmentId = assessmentId
        self.service = service
    }

    func load() async {
        if assessment.value == nil { assessment = .loading }
        do {
            assessment = .loaded(try await service.assessment(id: assessmentId))
        } catch {
            let error = APIError(error)
            if !error.isCancellation { assessment = .failed(error) }
        }
    }
}
