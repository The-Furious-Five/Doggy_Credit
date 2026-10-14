import SwiftUI

/// Paleta de DoggyCredit (docs/design-system/README.md), en tonos profundos y sólidos:
/// sin degradados, brillos ni colores pastel.
///
/// En iOS la marca se aplica como lo hace Apple con sus propias apps: superficies
/// del sistema, y el color como señal (acento, icono, punto de estado), nunca como
/// fondo teñido. La superficie de marca (pino) se reserva para momentos de marca.
/// En modo oscuro los tonos se aclaran solo lo necesario para el contraste AA.
enum Brand {
    static let pine = Color(.brandPine)
    static let sage = Color(.brandSage)
    /// Miel literal del logo: solo en el wordmark y sobre la superficie de marca.
    static let honey = Color(.brandHoney)
    static let ivory = Color(.brandIvory)
    /// Ámbar profundo (miel oscurecida) para iconos, categorías y gráficos.
    static let amber = Color(.toneAmber)
}

/// Roles de estado del Design System. Siempre acompañan un texto: nunca solo color.
enum Tone: Hashable, Sendable {
    case neutral, success, warning, danger, info, brand

    var color: Color {
        switch self {
        case .neutral: .secondary
        case .success: Color(.toneSuccess)
        case .warning: Color(.toneWarning)
        case .danger: Color(.toneDanger)
        case .info: Color(.toneInfo)
        case .brand: .accentColor
        }
    }
}

extension ShapeStyle where Self == Color {
    /// Fondo de los "momentos de marca": bienvenida, bloqueo y tarjetas destacadas.
    static var brandSurface: Color { Brand.pine }
}

/// Esquinas continuas como las del sistema.
nonisolated enum Radius {
    static let card: CGFloat = 22
    static let tile: CGFloat = 16
    static let control: CGFloat = 12
    static let icon: CGFloat = 8
}
