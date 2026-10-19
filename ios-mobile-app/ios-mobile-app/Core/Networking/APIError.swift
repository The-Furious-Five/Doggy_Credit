import Foundation

/// Error RFC 9457. El `code` sale siempre de `contracts/errors/catalog.yaml`.
nonisolated struct ProblemDetail: Codable, Hashable, Sendable {
    let type: String?
    let title: String
    let status: Int
    let detail: String?
    let code: String
    let correlationId: String?

    var errorCode: ErrorCode? { ErrorCode(rawValue: code) }
}

/// Códigos del catálogo único que la app trata de forma específica.
nonisolated enum ErrorCode: String, Sendable {
    case consentRequired = "CONSENT_REQUIRED"
    case quotaExceeded = "QUOTA_EXCEEDED"
    case rateLimited = "RATE_LIMITED"
    case featureNotInPlan = "FEATURE_NOT_IN_PLAN"
    case planLimitReached = "PLAN_LIMIT_REACHED"
    case providerUnavailable = "PROVIDER_UNAVAILABLE"
    case idempotencyKeyReused = "IDEMPOTENCY_KEY_REUSED"
    case unauthenticated = "UNAUTHENTICATED"
    case validationError = "VALIDATION_ERROR"
    case applicantAlreadyExists = "APPLICANT_ALREADY_EXISTS"
    case applicantNotFound = "APPLICANT_NOT_FOUND"
    case assessmentNotFound = "ASSESSMENT_NOT_FOUND"
    case consentNotFound = "CONSENT_NOT_FOUND"
    case unknownApplicant = "UNKNOWN_APPLICANT"
    case insufficientRole = "INSUFFICIENT_ROLE"
    case productNotFound = "PRODUCT_NOT_FOUND"
    case concurrentUpdate = "CONCURRENT_UPDATE"
}

nonisolated enum APIError: Error, Hashable, Sendable {
    /// Error de negocio con su código del catálogo.
    case problem(ProblemDetail)
    /// La sesión venció y no se pudo renovar.
    case unauthenticated
    /// Sin conexión, tiempo agotado o certificado no confiable.
    case transport(URLError.Code)
    /// Respuesta HTTP sin cuerpo RFC 9457.
    case http(status: Int)
    case decoding

    var code: ErrorCode? {
        if case let .problem(problem) = self { problem.errorCode } else { nil }
    }

    var status: Int? {
        switch self {
        case let .problem(problem): problem.status
        case let .http(status): status
        case .unauthenticated: 401
        default: nil
        }
    }

    var correlationId: String? {
        if case let .problem(problem) = self { problem.correlationId } else { nil }
    }

    var isForbidden: Bool { status == 403 }
    var isNotFound: Bool { status == 404 }
    /// La tarea se canceló (la vista desapareció o se reemplazó la carga): no es un fallo que mostrar.
    var isCancellation: Bool { self == .transport(.cancelled) }

    /// Normaliza cualquier error lanzado por la capa de red o por el sistema.
    init(_ error: any Error) {
        switch error {
        case let apiError as APIError: self = apiError
        case let urlError as URLError: self = .transport(urlError.code)
        case is CancellationError: self = .transport(.cancelled)
        case is DecodingError: self = .decoding
        default: self = .transport(.unknown)
        }
    }
}

nonisolated extension APIError {
    /// Qué ocurrió y qué se puede hacer, sin culpar a la persona (Design System, "Error").
    var title: String {
        switch self {
        case .transport(.notConnectedToInternet), .transport(.networkConnectionLost):
            "Sin conexión"
        case .transport(.timedOut):
            "El servidor tardó demasiado"
        case .transport(.serverCertificateUntrusted), .transport(.serverCertificateHasUnknownRoot),
             .transport(.secureConnectionFailed):
            "Conexión no segura"
        case .unauthenticated:
            "Tu sesión venció"
        case let .problem(problem):
            problem.title
        case .http(status: 403):
            "Tu rol no tiene acceso"
        case .http(status: 404):
            "No encontrado"
        case .http, .transport, .decoding:
            "Algo salió mal"
        }
    }

    var message: String {
        switch self {
        case .transport(.notConnectedToInternet), .transport(.networkConnectionLost):
            "Revisa tu conexión a internet y vuelve a intentarlo. Tus datos se conservan."
        case .transport(.timedOut):
            "Vuelve a intentarlo en unos segundos. Tus datos se conservan."
        case .transport(.serverCertificateUntrusted), .transport(.serverCertificateHasUnknownRoot),
             .transport(.secureConnectionFailed):
            "El dispositivo no confía en el certificado del servidor. Revisa la fecha del dispositivo y vuelve a intentarlo. Si persiste, avisa a soporte."
        case .unauthenticated:
            "Inicia sesión de nuevo para continuar."
        case let .problem(problem):
            problem.detail ?? "Vuelve a intentarlo. Si persiste, comparte el código \(problem.code) con soporte."
        case .http(status: 403):
            "Pide a quien administra tu institución que revise tus permisos."
        case .http, .transport, .decoding:
            "Vuelve a intentarlo. Si persiste, avisa a soporte."
        }
    }
}
