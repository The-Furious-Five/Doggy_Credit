import Foundation

/// Nueva evaluación en 4 pasos: solicitante → consentimiento → fuentes → resultado.
///
/// El borrador vive en la sesión: si la hoja se cierra a mitad de camino (el cliente
/// atiende a alguien, se corta la señal), la evaluación queda "en curso" y se retoma
/// desde el accesorio de la barra de pestañas.
@Observable
final class EvaluationFlowModel {
    enum Step: Int, CaseIterable, Identifiable {
        case applicant, consent, sources, result

        var id: Int { rawValue }

        var title: String {
            switch self {
            case .applicant: "Solicitante"
            case .consent: "Consentimiento"
            case .sources: "Fuentes"
            case .result: "Resultado"
            }
        }
    }

    enum Problem: Equatable {
        case incompleteApplicant
        case existingApplicant
        case missingBureauConsent
        /// El consentimiento venció o fue revocado: hay que registrar uno nuevo.
        case consentInvalid
        case quotaExceeded
        case failed(APIError)
    }

    // Paso 1: solicitante
    var segment: Segment = .person
    var document = "" {
        didSet {
            guard oldValue.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() != trimmedDocument.uppercased() else { return }
            // Cambiar de titular invalida toda autorización de la solicitud anterior.
            registeredApplicant = nil
            registeredDocument = ""
            applicantId = nil
            consentId = nil
            consentBureau = false
            consentBank = false
            consentWallet = false
            sources = .idle
            result = nil
            paymentCapacity = nil
            problem = nil
            step = .applicant
            idempotencyKey = UUID().uuidString
        }
    }
    var activity = ""
    var seniorityMonths: Int? = 0
    var monthlyIncome: Decimal?
    var requestedAmount: Decimal?
    var termMonths: Int? = 12
    var purpose: CreditPurpose?

    // Paso 2: nada preseleccionado, el cliente elige cada permiso.
    var consentBureau = false
    var consentBank = false
    var consentWallet = false

    private(set) var step: Step = .applicant
    private(set) var isBusy = false
    private(set) var problem: Problem?
    private(set) var applicantId: UUID?
    private var registeredApplicant: Applicant?
    private var registeredDocument = ""

    var existingApplicant: Applicant? {
        trimmedDocument.uppercased() == registeredDocument ? registeredApplicant : nil
    }
    private(set) var consentId: UUID?
    private(set) var sources: Loadable<[Integration]> = .idle
    private(set) var result: Assessment?
    private(set) var paymentCapacity: Money?

    /// Se conserva entre reintentos: un doble toque o un reintento no duplican la evaluación.
    private var idempotencyKey = UUID().uuidString
    private let service: any DoggyCreditService
    /// Avisa a la sesión que hay una evaluación nueva (cuota, Inicio, bandeja).
    @ObservationIgnored var onAssessmentCreated: () async -> Void

    init(service: any DoggyCreditService, onAssessmentCreated: @escaping () async -> Void = {}) {
        self.service = service
        self.onAssessmentCreated = onAssessmentCreated
    }

    var trimmedDocument: String { document.trimmingCharacters(in: .whitespacesAndNewlines) }

    /// Hay algo que retomar: datos escritos y aún sin resultado.
    var isInProgress: Bool {
        result == nil && (!trimmedDocument.isEmpty || !activity.isEmpty || monthlyIncome != nil
            || requestedAmount != nil || purpose != nil || segment != .person
            || (seniorityMonths ?? 0) != 0 || termMonths != 12 || step != .applicant)
    }

    var isApplicantValid: Bool {
        trimmedDocument.count >= 3
            && purpose != nil
            && (existingApplicant != nil || (activity.trimmingCharacters(in: .whitespacesAndNewlines).count >= 2
                && (monthlyIncome ?? 0) > 0 && (seniorityMonths ?? 0) >= 0))
            && (requestedAmount.map { $0 > 0 } ?? true)
            && (termMonths.map { (3...120).contains($0) } ?? true)
    }

    var primaryActionTitle: String {
        switch step {
        case .applicant, .consent: "Continuar"
        case .sources: "Evaluar"
        case .result: "Nueva evaluación"
        }
    }

    var canGoBack: Bool { step == .consent || step == .sources }

    /// Lo que falta para continuar, dicho en positivo. Nil cuando el paso está completo:
    /// el botón principal se habilita solo entonces, como en los formularios del sistema.
    var pendingRequirement: String? {
        switch step {
        case .applicant:
            if let requestedAmount, requestedAmount <= 0 {
                "El monto solicitado debe ser mayor que cero o quedar vacío."
            } else if let termMonths, !(3...120).contains(termMonths) {
                "Elige un plazo entre 3 y 120 meses."
            } else if existingApplicant != nil {
                isApplicantValid ? nil : "Elige el destino de esta nueva solicitud para continuar."
            } else {
                isApplicantValid ? nil : "Completa documento, actividad, ingreso positivo, antigüedad válida y destino del crédito para continuar."
            }
        case .consent:
            consentBureau ? nil : "El cliente debe autorizar la consulta a burós para continuar."
        case .sources, .result:
            nil
        }
    }

    func prefill(document: String, applicant: Applicant? = nil) {
        guard !isInProgress else { return }
        restart()
        self.document = document
        registeredApplicant = applicant
        registeredDocument = trimmedDocument.uppercased()
        applicantId = applicant?.id
    }

    func next() async {
        guard !isBusy else { return }
        isBusy = true
        defer { isBusy = false }
        switch step {
        case .applicant: await saveApplicant()
        case .consent: await saveConsent()
        case .sources: await evaluate()
        case .result: restart()
        }
    }

    func back() {
        guard canGoBack, let previous = Step(rawValue: step.rawValue - 1) else { return }
        problem = nil
        step = previous
    }

    func restart() {
        segment = .person
        document = ""
        activity = ""
        seniorityMonths = 0
        monthlyIncome = nil
        requestedAmount = nil
        termMonths = 12
        purpose = nil
        consentBureau = false
        consentBank = false
        consentWallet = false
        step = .applicant
        problem = nil
        applicantId = nil
        registeredApplicant = nil
        registeredDocument = ""
        consentId = nil
        sources = .idle
        result = nil
        paymentCapacity = nil
        idempotencyKey = UUID().uuidString
    }

    // MARK: Paso 1

    /// Reutiliza el solicitante si el documento ya existe; si no, lo crea.
    private func saveApplicant() async {
        guard isApplicantValid, let purpose else {
            problem = .incompleteApplicant
            return
        }
        problem = nil
        if let existingApplicant {
            applicantId = existingApplicant.id
            step = .consent
            return
        }
        do {
            if let existing = try await findExistingApplicant() {
                useExistingApplicant(existing)
            } else {
                guard let monthlyIncome else { return }
                let created = try await service.createApplicant(NewApplicant(
                    document: trimmedDocument, activity: activity.trimmingCharacters(in: .whitespacesAndNewlines),
                    activitySeniorityMonths: seniorityMonths ?? 0, monthlyIncome: monthlyIncome.apiString,
                    currency: .bob, creditPurpose: purpose.rawValue, segment: segment
                ))
                applicantId = created.id
                step = .consent
            }
        } catch let error as APIError where error.code == .applicantAlreadyExists {
            do {
                guard let existing = try await findExistingApplicant() else { throw error }
                useExistingApplicant(existing)
            } catch { fail(with: error) }
        } catch {
            fail(with: error)
        }
    }

    private func findExistingApplicant() async throws -> Applicant? {
        // El documento viaja en la cabecera X-Documento, nunca en la URL (B-08).
        try await service.applicants(page: 1, size: 1, segment: nil, document: trimmedDocument).items.first
    }

    private func useExistingApplicant(_ applicant: Applicant) {
        registeredApplicant = applicant
        registeredDocument = trimmedDocument.uppercased()
        applicantId = applicant.id
        problem = .existingApplicant
    }

    // MARK: Paso 2

    private func saveConsent() async {
        guard consentBureau else {
            problem = .missingBureauConsent
            return
        }
        var sources: [DataSource] = [.bureau]
        if consentBank { sources.append(.bank) }
        if consentWallet { sources.append(.wallet) }
        do {
            let consent = try await service.createConsent(NewConsent(
                holderDocument: trimmedDocument, sources: sources, purpose: "Evaluación de crédito", validityDays: 90
            ))
            consentId = consent.id
            problem = nil
            step = .sources
            await loadSources()
        } catch {
            fail(with: error)
        }
    }

    func loadSources() async {
        sources = .loading
        do {
            sources = .loaded(try await service.integrations())
        } catch {
            // Sin el estado de las fuentes la evaluación igual consulta las conectadas.
            sources = .failed(APIError(error))
        }
    }

    // MARK: Paso 3

    private func evaluate() async {
        guard let consentId else {
            step = .consent
            problem = .consentInvalid
            return
        }
        problem = nil
        do {
            let assessment = try await service.createAssessment(
                NewAssessment(
                    holderDocument: trimmedDocument, consentId: consentId, purpose: purpose?.rawValue,
                    requestedAmount: requestedAmount.map(\.apiString), termMonths: termMonths
                ),
                idempotencyKey: idempotencyKey
            )
            result = assessment
            step = .result
            await loadPaymentCapacity()
            await onAssessmentCreated()
        } catch {
            let error = APIError(error)
            switch error.code {
            case .quotaExceeded:
                problem = .quotaExceeded
                await onAssessmentCreated()
            case .consentRequired:
                self.consentId = nil
                step = .consent
                problem = .consentInvalid
            default:
                fail(with: error)
            }
        }
    }

    /// La capacidad de pago sale del Perfil 360 que la evaluación acaba de consolidar.
    private func loadPaymentCapacity() async {
        guard let applicantId else { return }
        paymentCapacity = try? await service.applicant(id: applicantId).profile?.paymentCapacity
    }

    private func fail(with error: any Error) {
        let error = APIError(error)
        guard !error.isCancellation else { return }
        problem = .failed(error)
    }
}
