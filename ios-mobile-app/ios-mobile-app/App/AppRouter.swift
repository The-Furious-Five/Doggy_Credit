import SwiftUI

extension EnvironmentValues {
    /// Falso dentro de otra hoja (p. ej. el perfil abierto desde "Resolver caso"): SwiftUI
    /// no presenta una segunda hoja encima, así que la acción no se ofrece ahí.
    @Entry var allowsStartingEvaluation = true
}

/// Destinos de navegación compartidos por todas las pestañas.
enum Route: Hashable {
    case client(UUID)
    case assessment(UUID)
}

/// Navegación de una sesión: pestaña activa, pila de cada pestaña y hojas globales.
@Observable
final class AppRouter {
    enum Tab: Hashable {
        case home, review, clients, analytics, search
    }

    var selectedTab: Tab = .home
    var homePath: [Route] = []
    var reviewPath: [Route] = []
    var clientsPath: [Route] = []
    var searchPath: [Route] = []

    var isEvaluationPresented = false
    var isAccountPresented = false
    /// Resolución en curso; la hoja vive en la raíz para abrirse desde Inicio o Revisión.
    var resolution: ResolveCaseModel?
    /// Cuenta resoluciones guardadas para confirmar con háptica tras cerrar la hoja.
    var completedResolutions = 0

    /// Abre el perfil del cliente en la pestaña Clientes (p. ej. desde el resultado de una evaluación).
    func showClient(_ id: UUID) {
        selectedTab = .clients
        clientsPath = [.client(id)]
    }
}
