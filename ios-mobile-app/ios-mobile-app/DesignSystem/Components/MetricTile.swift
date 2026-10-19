import SwiftUI

/// Indicador con etiqueta, valor y detalle (como `ds-stat`): no calcula ni interpreta.
struct MetricTile: View {
    let label: String
    let value: String
    var detail: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(.secondary)
            Text(value)
                .font(.title2.weight(.bold))
                .fontDesign(.rounded)
                .monospacedDigit()
                .lineLimit(1)
                .minimumScaleFactor(0.6)
                .contentTransition(.numericText())
            if let detail {
                Text(detail)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }
        }
        // Ocupa todo el alto de su fila: los indicadores vecinos miden lo mismo.
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .dcCard(padding: 14)
        .accessibilityElement(children: .combine)
    }
}

/// Rejilla de indicadores: 2 columnas en iPhone, 4 en iPad. Todos los de una fila
/// tienen la misma altura aunque una etiqueta ocupe dos líneas.
struct MetricGrid<Content: View>: View {
    @ViewBuilder var content: Content
    @Environment(\.horizontalSizeClass) private var sizeClass

    var body: some View {
        let columns = sizeClass == .regular ? 4 : 2
        Group(subviews: content) { subviews in
            VStack(spacing: 12) {
                ForEach(Array(stride(from: 0, to: subviews.count, by: columns)), id: \.self) { start in
                    // Cada fila toma la altura del indicador más alto (fixedSize) y todos la llenan.
                    HStack(alignment: .top, spacing: 12) {
                        ForEach(subviews[start..<min(start + columns, subviews.count)]) { $0 }
                        // Una fila incompleta conserva el ancho de columna.
                        ForEach(0..<(columns - min(columns, subviews.count - start)), id: \.self) { _ in
                            Color.clear.frame(maxWidth: .infinity, maxHeight: 1)
                        }
                    }
                    .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }
}
