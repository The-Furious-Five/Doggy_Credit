import Foundation

/// La app habla el español de Bolivia en cifras y fechas, igual que la consola web (es-BO).
nonisolated extension Locale {
    static let bolivia = Locale(identifier: "es_BO")
}

nonisolated enum Format {
    /// Espacio que no se parte entre una cifra y su unidad ("Bs 9.800", "11,5 %").
    static let unitSpace = "\u{00A0}"

    /// "Bs 9.800"; "—" si el dato es desconocido (la fuente no respondió).
    static func money(_ money: Money?) -> String {
        guard let money, let value = money.decimal else { return "—" }
        return amount(value, currency: money.currency)
    }

    static func amount(_ value: Decimal, currency: Currency = .bob) -> String {
        "\(symbol(currency))\(unitSpace)\(value.formatted(.number.precision(.fractionLength(0)).locale(.bolivia)))"
    }

    /// Montos de la API sin moneda (la evaluación guarda el número): se muestra solo la cifra.
    static func plainAmount(_ apiString: String?) -> String {
        guard let apiString, let value = Decimal(apiString: apiString) else { return "—" }
        return value.formatted(.number.precision(.fractionLength(0...2)).locale(.bolivia))
    }

    /// "Bs 20.000" para montos de la API sin moneda (las solicitudes son en bolivianos).
    static func bolivianos(_ apiString: String?) -> String {
        "Bs\(unitSpace)\(plainAmount(apiString))"
    }

    static func symbol(_ currency: Currency) -> String {
        switch currency {
        case .bob: "Bs"
        case .usd: "US$"
        }
    }

    static func number(_ value: Int) -> String {
        value.formatted(.number.locale(.bolivia))
    }

    static func number(_ value: Double?, fractionDigits: Int = 0) -> String {
        guard let value else { return "—" }
        return value.formatted(.number.precision(.fractionLength(0...fractionDigits)).locale(.bolivia))
    }

    /// Porcentaje que la API envía como texto decimal ("20.3").
    static func percent(apiString: String?) -> String {
        guard let apiString, let value = Decimal(apiString: apiString) else { return "—" }
        return "\(value.formatted(.number.precision(.fractionLength(0...1)).locale(.bolivia)))\(unitSpace)%"
    }

    /// "1 – 11 oct 2026" o "15 sept – 11 oct 2026".
    static func range(from: Date, to: Date) -> String {
        (from..<max(from, to)).formatted(
            Date.IntervalFormatStyle(date: .abbreviated, time: .omitted, locale: .bolivia, timeZone: .laPaz)
        )
    }

    static func percent(_ value: Double?) -> String {
        guard let value else { return "—" }
        return "\(value.formatted(.number.precision(.fractionLength(0...1)).locale(.bolivia)))\(unitSpace)%"
    }

    static func day(_ date: Date?) -> String {
        guard let date else { return "—" }
        return date.formatted(Date.FormatStyle(date: .abbreviated, time: .omitted, locale: .bolivia, timeZone: .laPaz))
    }

    static func dayAndTime(_ date: Date?) -> String {
        guard let date else { return "—" }
        return date.formatted(Date.FormatStyle(date: .abbreviated, time: .shortened, locale: .bolivia, timeZone: .laPaz))
    }

    /// "hace 2 horas", "ayer", "hace 2 meses": unidades completas, sin la ambigüedad de "2 m".
    static func relative(_ date: Date, now: Date = .now) -> String {
        if now.timeIntervalSince(date) < 60 { return "ahora" }
        return date.formatted(Date.RelativeFormatStyle(presentation: .named, unitsStyle: .wide, locale: .bolivia))
    }

    /// "1 de noviembre": primer día del mes siguiente en Bolivia, cuando se reinicia la cuota.
    static func nextMonthStart(after now: Date = .now) -> String {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = .laPaz
        let start = calendar.dateInterval(of: .month, for: now)?.end ?? now
        return start.formatted(Date.FormatStyle(locale: .bolivia, timeZone: .laPaz).day().month(.wide))
    }

    static func months(_ value: Int?) -> String {
        guard let value else { return "—" }
        return value == 1 ? "1 mes" : "\(value) meses"
    }

    /// "8 meses" o "3 años".
    static func seniority(months: Int?) -> String {
        guard let months else { return "—" }
        if months < 12 { return Self.months(months) }
        let years = months / 12
        return years == 1 ? "1 año" : "\(years) años"
    }

    /// "domingo, 11 de octubre" → "Domingo, 11 de octubre" (en español no se capitaliza cada palabra).
    static func sentenceCase(_ text: String) -> String {
        text.prefix(1).uppercased(with: .bolivia) + text.dropFirst()
    }

    static func duration(milliseconds: Double?) -> String {
        guard let milliseconds else { return "—" }
        if milliseconds < 1000 { return "\(Int(milliseconds)) ms" }
        return "\((milliseconds / 1000).formatted(.number.precision(.fractionLength(1)).locale(.bolivia))) s"
    }
}
