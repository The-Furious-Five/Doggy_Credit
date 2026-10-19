import Foundation

/// Todo lo que existe mientras hay una sesión: la persona, su institución, el servicio
/// y los modelos de cada pestaña. Al cerrar sesión se descarta entero.
@Observable
final class SessionStore {
    let id = UUID()
    let user: UserSession
    let configuration: AuthConfiguration
    let service: any DoggyCreditService
    let router = AppRouter()

    private(set) var tenant: Tenant?
    private(set) var usage: TenantUsage?
    private(set) var preferences: TenantPreferences?
    private(set) var tenantError: APIError?
    private(set) var preferencesError: APIError?
    private(set) var usageError: APIError?

    var dataError: APIError? { tenantError ?? preferencesError ?? usageError }

    let home: HomeModel
    let reviewQueue: ReviewQueueModel
    let clients: ClientsModel
    let analytics: AnalyticsModel
    let search: ClientSearchModel
    let evaluation: EvaluationFlowModel

    init(user: UserSession, configuration: AuthConfiguration, service: any DoggyCreditService) {
        self.user = user
        self.configuration = configuration
        self.service = service
        home = HomeModel(service: service)
        reviewQueue = ReviewQueueModel(service: service)
        clients = ClientsModel(service: service)
        analytics = AnalyticsModel(service: service)
        search = ClientSearchModel(service: service)
        evaluation = EvaluationFlowModel(service: service)
        evaluation.onAssessmentCreated = { [weak self] in
            await self?.assessmentCreated()
        }
    }

    var privacy: SensitiveDataPolicy {
        SensitiveDataPolicy(preferences: preferences, user: user)
    }

    var webConsoleURL: URL {
        configuration.webConsoleURL
    }

    var environmentDescription: String {
        configuration.server.host() ?? configuration.server.absoluteString
    }

    /// Datos de la institución y la bandeja, en paralelo, al entrar.
    func bootstrap() async {
        async let tenant: Void = refreshTenant()
        async let preferences: Void = refreshPreferences()
        async let queue: Void = reviewQueue.loadIfNeeded()
        async let usage: Void = refreshUsage()
        _ = await (tenant, preferences, queue, usage)
    }

    /// Abre la hoja de resolución de un caso, opcionalmente con la decisión elegida.
    func resolve(_ reviewCase: ReviewCase, preset: ResolutionDecision? = nil) {
        router.resolution = reviewQueue.makeResolution(for: reviewCase, preset: preset)
    }

    /// Abre la evaluación; con documento, la precarga (búsqueda sin resultados, reevaluar).
    func startEvaluation(document: String? = nil) {
        if let document { evaluation.prefill(document: document) }
        router.isEvaluationPresented = true
    }

    func refreshUsage() async {
        do {
            usage = try await service.usage()
            usageError = nil
        } catch {
            let error = APIError(error)
            if !error.isCancellation { usageError = error }
        }
    }

    func refreshTenant() async {
        do {
            tenant = try await service.currentTenant()
            tenantError = nil
        } catch {
            let error = APIError(error)
            if !error.isCancellation { tenantError = error }
        }
    }

    func refreshPreferences() async {
        do {
            preferences = try await service.preferences()
            preferencesError = nil
        } catch {
            let error = APIError(error)
            if !error.isCancellation {
                preferences = nil
                preferencesError = error
            }
        }
    }

    /// Una evaluación nueva consume cuota, aparece en Inicio y puede entrar a la bandeja.
    private func assessmentCreated() async {
        async let usage: Void = refreshUsage()
        async let home: Void = home.refresh()
        async let queue: Void = reviewQueue.refresh()
        async let clients: Void = clients.refresh()
        _ = await (usage, home, queue, clients)
    }
}
