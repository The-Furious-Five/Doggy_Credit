import Foundation

/// KPIs del tenant por período (fechas inclusivas, hora de Bolivia).
@Observable
final class AnalyticsModel {
    enum Period: String, CaseIterable, Identifiable {
        case month = "Este mes"
        case last30 = "30 días"
        case quarter = "90 días"
        case year = "Este año"

        var id: String { rawValue }

        func interval(now: Date = .now) -> (from: Date, to: Date) {
            var calendar = Calendar(identifier: .gregorian)
            calendar.timeZone = .laPaz
            switch self {
            case .month:
                return (calendar.dateInterval(of: .month, for: now)?.start ?? now, now)
            case .last30:
                return (calendar.date(byAdding: .day, value: -29, to: now) ?? now, now)
            case .quarter:
                return (calendar.date(byAdding: .day, value: -89, to: now) ?? now, now)
            case .year:
                return (calendar.dateInterval(of: .year, for: now)?.start ?? now, now)
            }
        }
    }

    private(set) var summary: Loadable<AnalyticsSummary> = .idle
    private(set) var period: Period = .month
    /// Recarga con datos ya visibles (cambio de período): se atenúan en vez de desaparecer.
    private(set) var isRefreshing = false
    private let service: any DoggyCreditService

    init(service: any DoggyCreditService) {
        self.service = service
    }

    func loadIfNeeded() async {
        guard case .idle = summary else { return }
        await refresh()
    }

    func select(_ period: Period) async {
        guard period != self.period else { return }
        self.period = period
        await refresh()
    }

    func refresh() async {
        if summary.value == nil { summary = .loading }
        isRefreshing = true
        defer { isRefreshing = false }
        let interval = period.interval()
        do {
            summary = .loaded(try await service.analyticsSummary(from: interval.from, to: interval.to))
        } catch {
            let error = APIError(error)
            // El período elegido se conserva; solo cambia el estado.
            if !error.isCancellation { summary = .failed(error) }
        }
    }
}
