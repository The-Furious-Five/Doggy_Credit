import Foundation

// Enumeraciones del contrato `contracts/openapi/doggycredit-api.v1.yaml`.
// Los valores crudos son los del JSON; los nombres en Swift siguen la convención
// del repositorio: código en inglés, textos visibles en español (ver DisplayText).

nonisolated enum Segment: String, Codable, CaseIterable, Identifiable, Sendable {
    case person = "PERSONA"
    case entrepreneur = "EMPRENDEDOR"
    case smallBusiness = "PYME"

    var id: String { rawValue }
}

nonisolated enum Currency: String, Codable, CaseIterable, Sendable {
    case bob = "BOB"
    case usd = "USD"
}

/// Fuentes que un titular puede autorizar en un consentimiento y que componen el Perfil 360.
nonisolated enum DataSource: String, Codable, CaseIterable, Identifiable, Sendable {
    case bureau = "BURO"
    case bank = "BANCO"
    case wallet = "WALLET"

    var id: String { rawValue }
}

/// Los 6 conectores del catálogo de integraciones.
nonisolated enum IntegrationSource: String, Codable, CaseIterable, Identifiable, Sendable {
    case bureau = "BURO"
    case bank = "BANCO"
    case wallet = "WALLET"
    case openBanking = "OPEN_BANKING"
    case invoicing = "FACTURACION"
    case collateral = "GARANTIAS"

    var id: String { rawValue }
}

nonisolated enum IntegrationStatus: String, Codable, Sendable {
    case connected = "CONECTADO"
    case available = "DISPONIBLE"
    case requiresPro = "REQUIERE_PRO"
    case comingSoon = "PROXIMAMENTE"
}

nonisolated enum AssessmentStatus: String, Codable, Sendable {
    case pending = "PENDIENTE"
    case partial = "PARCIAL"
    case preApproved = "PREAPROBADO"
    case manualReview = "REVISION_MANUAL"
    case failed = "FALLIDA"
}

/// Decisión de las políticas del tenant. Nunca hay rechazo automático: un rechazo
/// siempre lo decide una persona en la bandeja de revisión.
nonisolated enum PolicyDecision: String, Codable, Sendable {
    case preApproved = "PREAPROBADO"
    case manualReview = "REVISION_MANUAL"
    case requestData = "SOLICITAR_DATOS"
}

/// Bandas del producto: A ≥ 750 · B 600–749 · C 450–599 · D 300–449 · E < 300.
nonisolated enum ScoreBand: String, Codable, CaseIterable, Comparable, Sendable {
    case a = "A", b = "B", c = "C", d = "D", e = "E"

    init(score: Int) {
        switch score {
        case 750...: self = .a
        case 600..<750: self = .b
        case 450..<600: self = .c
        case 300..<450: self = .d
        default: self = .e
        }
    }

    static func < (lhs: ScoreBand, rhs: ScoreBand) -> Bool {
        // A es la mejor banda: "menor" significa peor riesgo.
        lhs.rawValue > rhs.rawValue
    }
}

nonisolated enum ProductKind: String, Codable, CaseIterable, Identifiable, Sendable {
    case microcredit = "MICROCREDITO"
    case green = "VERDE"
    case smallBusiness = "PYME"

    var id: String { rawValue }
}

nonisolated enum Plan: String, Codable, Sendable {
    case free = "FREE"
    case pro = "PRO"
    case enterprise = "ENTERPRISE"

    /// Analítica avanzada, webhooks y scoring avanzado (billing.plans).
    var includesAdvancedAnalytics: Bool { self != .free }
}

nonisolated enum InstitutionKind: String, Codable, Sendable {
    case microfinance = "IMF"
    case fintech = "FINTECH"
    case bank = "BANCO"
    case cooperative = "COOPERATIVA"
}

/// Roles del realm (seed/tenants.yaml). Idénticos en Keycloak, backend y UI.
nonisolated enum AppRole: String, Codable, Sendable {
    case tenantAdmin = "TENANT_ADMIN"
    case analyst = "ANALYST"
    case apiClient = "API_CLIENT"
    case platformAdmin = "PLATFORM_ADMIN"
}
