import SwiftUI

/// Estado escrito con un punto del tono (como `ds-badge`): siempre texto, nunca solo color.
struct ToneBadge: View {
    let text: String
    var tone: Tone = .neutral
    var symbol: String?

    var body: some View {
        HStack(spacing: 5) {
            if let symbol {
                Image(systemName: symbol)
                    .imageScale(.small)
                    .foregroundStyle(tone.color)
            } else if tone != .neutral {
                Circle()
                    .fill(tone.color)
                    .frame(width: 7, height: 7)
                    .accessibilityHidden(true)
            }
            Text(text)
                .foregroundStyle(.primary)
        }
        .font(.caption.weight(.semibold))
        .lineLimit(1)
        .padding(.horizontal, 9)
        .padding(.vertical, 4)
        .background(.fill.tertiary, in: Capsule())
        .accessibilityElement(children: .combine)
    }
}

extension View {
    /// Documentos (CI, NIT): cifras tabulares y, si no caben, se recorta el inicio para
    /// que los últimos dígitos, los que identifican al cliente, sigan visibles.
    func documentStyle() -> some View {
        monospacedDigit()
            .lineLimit(1)
            .truncationMode(.head)
    }
}

/// Fila de una lista de opciones con marca de selección, como en Ajustes. A diferencia
/// de `Picker`, admite "nada elegido" (el Design System no preselecciona decisiones).
struct SelectableRow: View {
    let title: String
    var subtitle: String?
    let symbol: String
    var tint: Color = .accentColor
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Label {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(title)
                            .foregroundStyle(.primary)
                        if let subtitle {
                            Text(subtitle)
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }
                } icon: {
                    Image(systemName: symbol)
                        .foregroundStyle(tint)
                }
                Spacer(minLength: 8)
                if isSelected {
                    Image(systemName: "checkmark")
                        .fontWeight(.semibold)
                        .foregroundStyle(Color.accentColor)
                        .accessibilityHidden(true)
                }
            }
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

/// Estado en una línea de texto con el punto del tono (filas de listas).
struct StatusLine: View {
    let text: String
    var tone: Tone = .neutral
    var font: Font = .subheadline.weight(.medium)

    var body: some View {
        HStack(spacing: 6) {
            Circle()
                .fill(tone.color)
                .frame(width: 8, height: 8)
                .accessibilityHidden(true)
            Text(text)
        }
        .font(font)
    }
}

/// "Datos simulados" debe verse siempre donde corresponda (patrón Evaluación del Design System).
struct SimulatedDataBadge: View {
    var body: some View {
        HStack(spacing: 5) {
            Image(systemName: "flask.fill")
                .imageScale(.small)
                .foregroundStyle(Brand.amber)
            Text("Datos simulados")
                .foregroundStyle(.primary)
        }
        .font(.caption.weight(.semibold))
        .lineLimit(1)
        .fixedSize()
        .padding(.horizontal, 9)
        .padding(.vertical, 4)
        .background(.fill.tertiary, in: Capsule())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Datos simulados")
    }
}

/// Icono en un cuadrado redondeado de color sólido, como las listas de Ajustes o GitHub.
struct IconTile: View {
    let symbol: String
    var tint: Color = .accentColor
    var size: CGFloat = 30

    var body: some View {
        Image(systemName: symbol)
            .font(.system(size: size * 0.5, weight: .semibold))
            .foregroundStyle(.white)
            .frame(width: size, height: size)
            .background(tint, in: .rect(cornerRadius: size * 0.27, style: .continuous))
            .accessibilityHidden(true)
    }
}

/// Encabezado de sección estilo Música: título grande y, si navega, un chevron.
struct SectionHeader<Trailing: View>: View {
    let title: String
    var subtitle: String?
    @ViewBuilder var trailing: Trailing

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.title2.bold())
                if let subtitle {
                    Text(subtitle)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer(minLength: 8)
            trailing
        }
        .accessibilityAddTraits(.isHeader)
    }
}

extension SectionHeader where Trailing == EmptyView {
    init(title: String, subtitle: String? = nil) {
        self.init(title: title, subtitle: subtitle) { EmptyView() }
    }
}

/// Aviso con icono del tono sobre superficie neutra (como `ds-alert`): qué ocurrió y qué hacer.
struct NoticeCard<Actions: View>: View {
    let title: String
    let message: String
    var tone: Tone = .info
    @ViewBuilder var actions: Actions

    private var symbol: String {
        switch tone {
        case .success: "checkmark.circle.fill"
        case .warning: "exclamationmark.triangle.fill"
        case .danger: "xmark.octagon.fill"
        case .info, .brand, .neutral: "info.circle.fill"
        }
    }

    var body: some View {
        AdaptiveStack(horizontalAlignment: .leading, verticalAlignment: .top, spacing: 12) {
            Image(systemName: symbol)
                .font(.title3)
                .foregroundStyle(tone.color)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 6) {
                Text(title)
                    .font(.headline)
                Text(message)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
                actions
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .accessibilityElement(children: .contain)
    }
}

extension NoticeCard where Actions == EmptyView {
    init(title: String, message: String, tone: Tone = .info) {
        self.init(title: title, message: message, tone: tone) { EmptyView() }
    }
}
