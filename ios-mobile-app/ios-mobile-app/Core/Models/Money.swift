import Foundation

/// Importe del contrato: el decimal viaja como texto, nunca como número flotante.
nonisolated struct Money: Codable, Hashable, Sendable {
    let amount: String
    let currency: Currency

    var decimal: Decimal? { Decimal(apiString: amount) }
}

nonisolated extension Decimal {
    /// Interpreta un decimal del contrato ("12500.00"), independiente de la región del dispositivo.
    init?(apiString: String) {
        let trimmed = apiString.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty, let value = Decimal(string: trimmed, locale: Locale(identifier: "en_US_POSIX")) else {
            return nil
        }
        self = value
    }

    /// "12500.00": dos decimales, punto como separador (patrón `^[0-9]+(\.[0-9]{1,2})?$`).
    var apiString: String {
        var value = self
        var rounded = Decimal()
        NSDecimalRound(&rounded, &value, 2, .plain)
        return rounded.formatted(
            .number
                .precision(.fractionLength(2))
                .grouping(.never)
                .locale(Locale(identifier: "en_US_POSIX"))
        )
    }
}
