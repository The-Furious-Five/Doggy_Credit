import SwiftUI

/// Estado de una carga remota. Cada pantalla distingue carga, error con reintento y vacío.
enum Loadable<Value> {
    case idle
    case loading
    case loaded(Value)
    case failed(APIError)

    var value: Value? {
        if case let .loaded(value) = self { value } else { nil }
    }

    var error: APIError? {
        if case let .failed(error) = self { error } else { nil }
    }

    var isLoading: Bool {
        if case .loading = self { true } else { false }
    }
}

/// Estado de un listado paginado: los elementos viven aparte para conservarlos al recargar.
enum LoadPhase: Equatable {
    case idle
    case loading
    case loaded
    case failed(APIError)

    var error: APIError? {
        if case let .failed(error) = self { error } else { nil }
    }
}

/// Error a pantalla completa con la acción de reintentar (patrón Listado del Design System).
struct ErrorStateView: View {
    let error: APIError
    var subject = "la información"
    let retry: () async -> Void

    var body: some View {
        ContentUnavailableView {
            Label(error.isForbidden ? error.title : "No pudimos cargar \(subject)", systemImage: symbol)
        } description: {
            Text(error.message)
        } actions: {
            if !error.isForbidden {
                Button("Reintentar") { Task { await retry() } }
                    .dcSecondaryButtonStyle()
            }
        }
    }

    private var symbol: String {
        switch error {
        case .transport(.notConnectedToInternet), .transport(.networkConnectionLost): "wifi.slash"
        case _ where error.isForbidden: "lock.fill"
        default: "exclamationmark.icloud"
        }
    }
}

/// Indicador de carga con texto legible para VoiceOver.
struct LoadingView: View {
    var label = "Cargando…"

    var body: some View {
        ProgressView(label)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .padding(.vertical, 40)
    }
}

/// Estado de actualización que acompaña contenido conservado, sin bloquearlo.
struct RefreshStatusView: View {
    let isRefreshing: Bool
    let lastUpdatedAt: Date?
    let error: APIError?
    let retry: () async -> Void
    @Environment(\.locale) private var locale

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let error {
                NoticeCard(title: "No pudimos actualizar", message: error.message, tone: .warning) {
                    Button("Reintentar actualización") { Task { await retry() } }
                        .disabled(isRefreshing)
                }
            }
            if isRefreshing {
                ProgressView("Actualizando…")
            } else if let lastUpdatedAt {
                Text("Última actualización: \(lastUpdatedAt.formatted(.dateTime.day().month(.abbreviated).hour().minute().locale(locale)))")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

/// Reintento explícito para páginas incompletas; no depende de una fila visible.
struct PaginationFooter: View {
    let isLoading: Bool
    let error: APIError?
    var title = "Cargar más"
    let load: () async -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let error {
                NoticeCard(title: "No pudimos cargar más resultados", message: error.message, tone: .warning)
            }
            if isLoading {
                ProgressView("Cargando más resultados…")
            } else {
                Button(error == nil ? title : "Reintentar carga") { Task { await load() } }
                    .dcSecondaryButtonStyle()
                    .controlSize(.large)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
