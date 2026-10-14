import SwiftUI

// Textos, tonos y símbolos de cada concepto del dominio, compartidos por todas las
// pantallas (equivalente a client-format.ts y product-format.ts de la consola web).

extension Segment {
    var label: String {
        switch self {
        case .person: "Persona"
        case .entrepreneur: "Emprendedor"
        case .smallBusiness: "PYME"
        }
    }

    var symbol: String {
        switch self {
        case .person: "person.fill"
        case .entrepreneur: "storefront.fill"
        case .smallBusiness: "building.2.fill"
        }
    }

    var tint: Color {
        switch self {
        case .person: Brand.sage
        case .entrepreneur: Brand.amber
        case .smallBusiness: Tone.info.color
        }
    }
}

extension DataSource {
    var label: String {
        switch self {
        case .bureau: "Buró de crédito"
        case .bank: "Core bancario"
        case .wallet: "Billetera móvil"
        }
    }

    var dataDescription: String {
        switch self {
        case .bureau: "Historial y deudas"
        case .bank: "Cuentas y movimientos"
        case .wallet: "Flujos de ingresos"
        }
    }

    var symbol: String {
        IntegrationSource(rawValue: rawValue)?.symbol ?? "questionmark.circle"
    }
}

extension IntegrationSource {
    var label: String {
        switch self {
        case .bureau: "Buró de crédito"
        case .bank: "Core bancario"
        case .wallet: "Billetera móvil"
        case .openBanking: "Agregador open banking"
        case .invoicing: "Facturación electrónica"
        case .collateral: "Registro de garantías"
        }
    }

    var symbol: String {
        switch self {
        case .bureau: "doc.text.magnifyingglass"
        case .bank: "building.columns.fill"
        case .wallet: "wallet.bifold.fill"
        case .openBanking: "point.3.connected.trianglepath.dotted"
        case .invoicing: "doc.plaintext.fill"
        case .collateral: "shield.lefthalf.filled"
        }
    }
}

extension IntegrationStatus {
    var label: String {
        switch self {
        case .connected: "Conectado"
        case .available: "Disponible"
        case .requiresPro: "Plan Pro"
        case .comingSoon: "Próximamente"
        }
    }

    var tone: Tone {
        switch self {
        case .connected: .success
        case .requiresPro: .info
        case .available, .comingSoon: .neutral
        }
    }
}

extension ProfileSource.State {
    var label: String {
        switch self {
        case .complete: "Completa"
        case .noRecord: "Sin registro"
        case .unavailable: "No disponible"
        case .notQueried: "No consultada"
        }
    }

    var tone: Tone {
        switch self {
        case .complete: .success
        case .unavailable: .warning
        case .noRecord, .notQueried: .neutral
        }
    }
}

extension FinancialProfile.IncomeOrigin {
    var label: String {
        switch self {
        case .bank: "Promedio de 6 meses en el banco"
        case .wallet: "Promedio de 6 meses en la billetera"
        case .declared: "Declarado por el cliente"
        }
    }
}

extension Consent.Status {
    var label: String {
        switch self {
        case .active: "Vigente"
        case .revoked: "Revocado"
        case .expired: "Expirado"
        }
    }

    var tone: Tone {
        switch self {
        case .active: .success
        case .revoked: .danger
        case .expired: .neutral
        }
    }
}

extension PolicyDecision {
    /// Nunca hay rechazo automático: ninguna decisión es un rechazo.
    var label: String {
        switch self {
        case .preApproved: "Preaprobado"
        case .manualReview: "Revisión manual"
        case .requestData: "Solicitar datos"
        }
    }

    var tone: Tone {
        switch self {
        case .preApproved: .success
        case .manualReview: .warning
        case .requestData: .info
        }
    }

    var symbol: String {
        switch self {
        case .preApproved: "checkmark.seal.fill"
        case .manualReview: "person.badge.clock.fill"
        case .requestData: "doc.badge.plus"
        }
    }
}

extension AssessmentStatus {
    var label: String {
        switch self {
        case .pending: "Pendiente"
        case .partial: "Resultado parcial"
        case .preApproved: "Preaprobado"
        case .manualReview: "En revisión"
        case .failed: "Fallida"
        }
    }

    var tone: Tone {
        switch self {
        case .preApproved: .success
        case .partial, .manualReview: .warning
        case .failed: .danger
        case .pending: .neutral
        }
    }
}

extension ReviewCase.Motive {
    var label: String {
        switch self {
        case .manualReview: "Revisión manual"
        case .requestData: "Solicitar datos"
        }
    }

    var tone: Tone {
        switch self {
        case .manualReview: .info
        case .requestData: .neutral
        }
    }
}

extension ReviewCase {
    /// Por qué el caso está en revisión, en una sola etiqueta. Un resultado parcial
    /// siempre queda en revisión manual, así que es el motivo más preciso.
    var reviewReason: String { partialResult ? "Resultado parcial" : motive.label }
    var reviewReasonTone: Tone { partialResult ? .warning : motive.tone }
    var reviewReasonSymbol: String { partialResult ? "exclamationmark.triangle.fill" : "person.badge.clock.fill" }
}

extension ScoreBand {
    /// La banda resume riesgo, no aprueba ni rechaza: el color acompaña al texto.
    var tone: Tone {
        switch self {
        case .a, .b: .success
        case .c: .info
        case .d: .warning
        case .e: .danger
        }
    }

    var riskLabel: String {
        switch self {
        case .a: "Riesgo muy bajo"
        case .b: "Riesgo bajo"
        case .c: "Riesgo moderado"
        case .d: "Riesgo alto"
        case .e: "Riesgo muy alto"
        }
    }
}

extension ProductKind {
    var label: String {
        switch self {
        case .microcredit: "Microcrédito"
        case .green: "Crédito ecológico"
        case .smallBusiness: "Crédito PYME"
        }
    }

    var symbol: String {
        switch self {
        case .microcredit: "banknote.fill"
        case .green: "leaf.fill"
        case .smallBusiness: "building.2.fill"
        }
    }

    var tint: Color {
        switch self {
        case .microcredit: Brand.amber
        case .green: Brand.sage
        case .smallBusiness: Tone.info.color
        }
    }
}

extension Product {
    var rangeDescription: String {
        let minimum = Format.money(Money(amount: minimumAmount, currency: currency))
        let maximum = Format.money(Money(amount: maximumAmount, currency: currency))
        return "\(minimum) – \(maximum) · \(minimumTermMonths) a \(maximumTermMonths) meses · \(Format.percent(apiString: annualRate)) anual"
    }
}

extension NeedBreakdown.Category {
    var label: String {
        switch self {
        case .workingCapital: "Capital de trabajo"
        case .productiveInvestment: "Inversión productiva"
        case .energyEfficiency: "Eficiencia energética"
        case .consumption: "Consumo y consolidación"
        }
    }

    var tint: Color {
        switch self {
        case .workingCapital: Tone.info.color
        case .productiveInvestment: Brand.amber
        // Eficiencia energética es la puerta al crédito ecológico: el verde de la marca.
        case .energyEfficiency: Color.accentColor
        case .consumption: Color.gray
        }
    }
}

extension ModelMonitor.State {
    var label: String {
        switch self {
        case .stable: "Estable"
        case .watch: "Vigilar"
        case .alert: "Alerta"
        case .insufficient: "Muestras insuficientes"
        }
    }

    var tone: Tone {
        switch self {
        case .stable: .success
        case .watch: .warning
        case .alert: .danger
        case .insufficient: .neutral
        }
    }
}

extension Plan {
    /// "Gratis", para filas que ya dicen "Plan".
    var name: String {
        switch self {
        case .free: "Gratis"
        case .pro: "Pro"
        case .enterprise: "Enterprise"
        }
    }

    var label: String { "Plan \(name)" }
}

extension InstitutionKind {
    var label: String {
        switch self {
        case .microfinance: "Institución microfinanciera"
        case .fintech: "Fintech"
        case .bank: "Banco"
        case .cooperative: "Cooperativa"
        }
    }
}

extension AppRole {
    var label: String {
        switch self {
        case .tenantAdmin: "Administración de la institución"
        case .analyst: "Analista de crédito"
        case .apiClient: "Cliente de API"
        case .platformAdmin: "Administración de la plataforma"
        }
    }
}

extension ScoreReason {
    /// Explica cada razón del scorecard v1 en lenguaje directo.
    var label: String {
        switch code {
        case "SIN_MORA_12M": "Sin mora en los últimos 12 meses"
        case "MORA_1_30_DIAS": "Mora de 1 a 30 días"
        case "MORA_31_90_DIAS": "Mora de 31 a 90 días"
        case "MORA_MAYOR_90_DIAS": "Mora mayor a 90 días"
        case "MORA_DESCONOCIDA_BURO_NO_DISPONIBLE": "Mora desconocida: el buró no respondió"
        case "ACTIVIDAD_MENOR_6_MESES": "Actividad con menos de 6 meses"
        case "ACTIVIDAD_6_23_MESES": "Actividad de 6 a 23 meses"
        case "ACTIVIDAD_MAYOR_24_MESES": "Actividad de 2 años o más"
        case "HISTORIAL_MENOR_6_MESES": "Historial crediticio menor a 6 meses"
        case "HISTORIAL_6_23_MESES": "Historial crediticio de 6 a 23 meses"
        case "HISTORIAL_MAYOR_24_MESES": "Historial crediticio de 2 años o más"
        case "HISTORIAL_DESCONOCIDO_BURO_NO_DISPONIBLE": "Historial desconocido: el buró no respondió"
        case "MONTO_HASTA_3_INGRESOS": "Monto de hasta 3 ingresos mensuales"
        case "MONTO_4_6_INGRESOS": "Monto de 4 a 6 ingresos mensuales"
        case "MONTO_MAYOR_6_INGRESOS": "Monto mayor a 6 ingresos mensuales"
        case "PLAZO_HASTA_12_MESES": "Plazo de hasta 12 meses"
        case "PLAZO_13_36_MESES": "Plazo de 13 a 36 meses"
        case "PLAZO_MAYOR_36_MESES": "Plazo mayor a 36 meses"
        case "CON_REGISTRO_BURO": "Con registro en el buró"
        case "SIN_REGISTRO_BURO": "Sin registro en el buró"
        case "REGISTRO_DESCONOCIDO_BURO_NO_DISPONIBLE": "Registro desconocido: el buró no respondió"
        default: code.replacingOccurrences(of: "_", with: " ").capitalized(with: .bolivia)
        }
    }

    var tone: Tone {
        guard let points else { return .neutral }
        return points > 0 ? .success : (points < 0 ? .warning : .neutral)
    }

    var symbol: String {
        guard let points else { return "circle.fill" }
        return points > 0 ? "arrow.up.circle.fill" : (points < 0 ? "arrow.down.circle.fill" : "equal.circle.fill")
    }
}

extension TenantUsage {
    var summary: String {
        isUnlimited
            ? "\(Format.number(monthlyAssessments)) evaluaciones este mes · sin límite"
            : "\(Format.number(monthlyAssessments)) de \(Format.number(monthlyLimit)) evaluaciones este mes"
    }
}

/// Destinos del crédito que ofrece la consola (ayudan a detectar la necesidad real).
enum CreditPurpose: String, CaseIterable, Identifiable {
    case workingCapital = "Capital de trabajo"
    case consumption = "Consumo o consolidación"
    case energyEfficiency = "Eficiencia energética"
    case productiveInvestment = "Inversión productiva"

    var id: String { rawValue }

    var hint: String? {
        self == .energyEfficiency ? "Puede calificar a crédito ecológico" : nil
    }

    var symbol: String {
        switch self {
        case .workingCapital: "shippingbox.fill"
        case .consumption: "cart.fill"
        case .energyEfficiency: "sun.max.fill"
        case .productiveInvestment: "hammer.fill"
        }
    }

    /// Mismo color que su categoría en Analítica.
    var tint: Color {
        switch self {
        case .workingCapital: NeedBreakdown.Category.workingCapital.tint
        case .consumption: NeedBreakdown.Category.consumption.tint
        case .energyEfficiency: NeedBreakdown.Category.energyEfficiency.tint
        case .productiveInvestment: NeedBreakdown.Category.productiveInvestment.tint
        }
    }
}
