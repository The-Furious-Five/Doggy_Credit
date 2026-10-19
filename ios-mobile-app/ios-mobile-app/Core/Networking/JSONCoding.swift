import Foundation

/// Zona horaria del negocio: el contrato define las fechas de analytics en America/La_Paz.
nonisolated extension TimeZone {
    static let laPaz = TimeZone(identifier: "America/La_Paz") ?? .gmt
}

nonisolated enum APIDate {
    /// `format: date` del contrato ("2026-10-01"), en hora de Bolivia.
    static func dayString(_ date: Date) -> String {
        date.formatted(Date.ISO8601FormatStyle(timeZone: .laPaz).year().month().day())
    }

    /// `format: date-time` del contrato.
    static func dateTimeString(_ date: Date) -> String {
        date.formatted(.iso8601)
    }

    /// Acepta instantes ISO 8601 con o sin fracciones (Jackson emite hasta microsegundos)
    /// y fechas sin hora.
    static func parse(_ string: String) -> Date? {
        if string.count == 10 {
            return try? Date.ISO8601FormatStyle(timeZone: .laPaz).year().month().day().parse(string)
        }
        guard let dot = string.firstIndex(of: ".") else {
            return parseWholeSeconds(string)
        }
        let afterDot = string[string.index(after: dot)...]
        let digits = afterDot.prefix { $0.isNumber }
        let zone = afterDot.dropFirst(digits.count)
        guard let whole = parseWholeSeconds(String(string[..<dot]) + zone) else {
            return nil
        }
        let fraction = Double("0." + digits) ?? 0
        return whole.addingTimeInterval(fraction)
    }

    /// "Z", "-04:00" o "-0400" como zona.
    private static func parseWholeSeconds(_ string: String) -> Date? {
        if let date = try? Date.ISO8601FormatStyle().parse(string) { return date }
        return try? Date.ISO8601FormatStyle(timeZoneSeparator: .colon).parse(string)
    }
}

nonisolated extension JSONDecoder {
    static var doggyCredit: JSONDecoder {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .custom { decoder in
            let container = try decoder.singleValueContainer()
            let string = try container.decode(String.self)
            guard let date = APIDate.parse(string) else {
                throw DecodingError.dataCorruptedError(in: container, debugDescription: "Fecha no válida: \(string)")
            }
            return date
        }
        return decoder
    }
}

nonisolated extension JSONEncoder {
    static var doggyCredit: JSONEncoder {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .custom { date, encoder in
            var container = encoder.singleValueContainer()
            try container.encode(APIDate.dateTimeString(date))
        }
        return encoder
    }
}
