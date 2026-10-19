import Foundation
@testable import ios_mobile_app

/// Personas de demostración tomadas de `seed/tenants.yaml`. Todos los datos son
/// sintéticos (RN-20): ninguna institución, persona o documento es real.
nonisolated enum DemoScenario: String, Codable, CaseIterable, Identifiable, Sendable {
    /// Analista de una IMF con plan Gratis: datos enmascarados y cuota mensual de 500.
    case analyst
    /// Administradora de una fintech con plan Pro: analítica avanzada y sin límite mensual.
    case administrator

    var id: String { rawValue }

    var user: UserSession {
        switch self {
        case .analyst:
            UserSession(name: "Marcelo Quispe (demo)", username: "analista.progreso",
                        tenantId: tenant.id.uuidString.lowercased(), roles: [.analyst])
        case .administrator:
            UserSession(name: "Paola Fernández (demo)", username: "admin.vecina",
                        tenantId: tenant.id.uuidString.lowercased(), roles: [.tenantAdmin])
        }
    }

    var tenant: Tenant {
        switch self {
        case .analyst:
            Tenant(id: UUID(uuidString: "9c7e7c1a-0000-4000-8000-000000000001")!, name: "Progreso Andino",
                   taxId: "1000000010", kind: .microfinance, plan: .free, country: "BO", currency: "BOB", active: true)
        case .administrator:
            Tenant(id: UUID(uuidString: "9c7e7c1a-0000-4000-8000-000000000002")!, name: "Fintech Vecina",
                   taxId: "1000000020", kind: .fintech, plan: .pro, country: "BO", currency: "BOB", active: true)
        }
    }

    var title: String {
        switch self {
        case .analyst: "Analista · Progreso Andino"
        case .administrator: "Administradora · Fintech Vecina"
        }
    }

    var subtitle: String {
        switch self {
        case .analyst: "Plan Gratis, datos enmascarados"
        case .administrator: "Plan Pro, analítica avanzada"
        }
    }
}
