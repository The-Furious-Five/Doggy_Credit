import Foundation

/// Datos del tenant autenticado (extraídos del JWT por el servidor).
nonisolated struct Tenant: Codable, Hashable, Identifiable, Sendable {
    let id: UUID
    let name: String
    let taxId: String
    let kind: InstitutionKind
    let plan: Plan
    let country: String
    let currency: String
    let active: Bool

    enum CodingKeys: String, CodingKey {
        case id
        case name = "nombre"
        case taxId = "nit"
        case kind = "tipo"
        case plan, active = "activo"
        case country = "pais"
        case currency = "moneda"
    }
}

nonisolated struct TenantUsage: Codable, Hashable, Sendable {
    let monthlyAssessments: Int
    /// 0 significa sin límite.
    let monthlyLimit: Int

    var isUnlimited: Bool { monthlyLimit == 0 }

    /// Fracción usada del límite mensual; nil cuando el plan no tiene límite.
    var fractionUsed: Double? {
        isUnlimited ? nil : min(1, Double(monthlyAssessments) / Double(monthlyLimit))
    }

    /// Avisos de cuota del 80 % y 100 % (E-11, RF-PLN-06).
    var level: QuotaLevel {
        guard !isUnlimited else { return .ok }
        if monthlyAssessments >= monthlyLimit { return .exhausted }
        return monthlyAssessments * 100 >= monthlyLimit * 80 ? .warning : .ok
    }

    enum QuotaLevel: Sendable {
        case ok, warning, exhausted
    }

    enum CodingKeys: String, CodingKey {
        case monthlyAssessments = "evaluacionesMes"
        case monthlyLimit = "limiteMensual"
    }
}

nonisolated struct TenantPreferences: Codable, Hashable, Sendable {
    let maskSensitiveData: Bool

    enum CodingKeys: String, CodingKey {
        case maskSensitiveData = "enmascararDatos"
    }
}

/// KPIs agregados del tenant, sin datos personales.
nonisolated struct AnalyticsSummary: Codable, Hashable, Sendable {
    let from: Date
    let to: Date
    let plan: String
    let currency: String
    let advancedAnalytics: Bool
    let assessments: Int
    /// Escala 0–1000. Nil cuando no hay evaluaciones.
    let averageScore: Double?
    let activeIntegrations: Int
    let recommendationPercent: Double
    let needs: [NeedBreakdown]
    /// Se omite si el plan no habilita analítica (actualmente Pro y Enterprise).
    let pro: AdvancedAnalytics?

    enum CodingKeys: String, CodingKey {
        case from = "desde"
        case to = "hasta"
        case plan
        case currency = "moneda"
        case advancedAnalytics = "analiticaAvanzada"
        case assessments = "evaluaciones"
        case averageScore = "scorePromedio"
        case activeIntegrations = "integracionesActivas"
        case recommendationPercent = "porcentajeConRecomendacion"
        case needs = "necesidades"
        case pro
    }
}

nonisolated struct NeedBreakdown: Codable, Hashable, Identifiable, Sendable {
    let category: Category
    let count: Int
    let percent: Double

    var id: Category { category }

    enum Category: String, Codable, CaseIterable, Sendable {
        case workingCapital = "CAPITAL_TRABAJO"
        case productiveInvestment = "INVERSION_PRODUCTIVA"
        case energyEfficiency = "EFICIENCIA_ENERGETICA"
        case consumption = "CONSUMO_CONSOLIDACION"
    }

    enum CodingKeys: String, CodingKey {
        case category = "categoria"
        case count = "cantidad"
        case percent = "porcentaje"
    }
}

nonisolated struct AdvancedAnalytics: Codable, Hashable, Sendable {
    /// Evaluaciones PREAPROBADO / total × 100. No mide desembolsos.
    let conversionPercent: Double
    let medianDecisionTimeMs: Double?
    let recommendedAmount: String
    let products: [ProductBreakdown]
    let models: [ModelMonitor]

    enum CodingKeys: String, CodingKey {
        case conversionPercent = "conversionPorcentaje"
        case medianDecisionTimeMs = "tiempoDecisionMedianaMs"
        case recommendedAmount = "montoRecomendado"
        case products = "recomendacionesPorProducto"
        case models = "modelos"
    }
}

nonisolated struct ProductBreakdown: Codable, Hashable, Identifiable, Sendable {
    let kind: ProductKind
    let count: Int
    let percent: Double
    let recommendedAmount: String

    var id: ProductKind { kind }

    enum CodingKeys: String, CodingKey {
        case kind = "tipo"
        case count = "cantidad"
        case percent = "porcentaje"
        case recommendedAmount = "montoRecomendado"
    }
}

/// Último monitoreo semanal de deriva (PSI) por versión de modelo.
nonisolated struct ModelMonitor: Codable, Hashable, Identifiable, Sendable {
    let version: String
    let psi: Double?
    let state: State
    let from: Date
    let to: Date
    let currentSamples: Int
    let baselineSamples: Int

    var id: String { version }

    enum State: String, Codable, Sendable {
        case stable = "ESTABLE"
        case watch = "VIGILAR"
        case alert = "ALERTA"
        case insufficient = "INSUFICIENTE"
    }

    enum CodingKeys: String, CodingKey {
        case version, psi
        case state = "estado"
        case from = "desde"
        case to = "hasta"
        case currentSamples = "muestrasActuales"
        case baselineSamples = "muestrasBase"
    }
}

/// Página de un listado paginado por número de página (desde 1).
nonisolated struct Page<Item: Codable & Sendable & Hashable>: Codable, Hashable, Sendable {
    let items: [Item]
    let total: Int
}
