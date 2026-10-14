import SwiftUI

/// Coloca elementos en fila y pasa a la siguiente cuando no caben, en vez de truncarlos.
/// Pensado para insignias: con texto grande (Dynamic Type) se reacomodan solas.
struct FlowLayout: Layout {
    var spacing: CGFloat = 6
    var lineSpacing: CGFloat = 6

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = arrange(subviews, width: proposal.width ?? .infinity)
        let width = rows.map(\.width).max() ?? 0
        let height = rows.map(\.height).reduce(0, +) + lineSpacing * CGFloat(max(0, rows.count - 1))
        return CGSize(width: proposal.width.map { min(width, $0) } ?? width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var y = bounds.minY
        for row in arrange(subviews, width: bounds.width) {
            var x = bounds.minX
            for index in row.indices {
                let size = subviews[index].sizeThatFits(ProposedViewSize(width: bounds.width, height: nil))
                subviews[index].place(at: CGPoint(x: x, y: y + (row.height - size.height) / 2),
                                      proposal: ProposedViewSize(width: min(size.width, bounds.width), height: size.height))
                x += min(size.width, bounds.width) + spacing
            }
            y += row.height + lineSpacing
        }
    }

    private struct Row {
        var indices: [Int] = []
        var width: CGFloat = 0
        var height: CGFloat = 0
    }

    private func arrange(_ subviews: Subviews, width: CGFloat) -> [Row] {
        var rows: [Row] = []
        var current = Row()
        for index in subviews.indices {
            let size = subviews[index].sizeThatFits(ProposedViewSize(width: width, height: nil))
            let itemWidth = min(size.width, width)
            let needed = current.indices.isEmpty ? itemWidth : current.width + spacing + itemWidth
            if needed > width, !current.indices.isEmpty {
                rows.append(current)
                current = Row()
            }
            current.width = current.indices.isEmpty ? itemWidth : current.width + spacing + itemWidth
            current.height = max(current.height, size.height)
            current.indices.append(index)
        }
        if !current.indices.isEmpty { rows.append(current) }
        return rows
    }
}

extension DynamicTypeSize {
    /// Con tamaños de accesibilidad las filas pasan de horizontales a verticales (HIG).
    var prefersVerticalLayout: Bool { isAccessibilitySize }
}

/// Primera línea de una fila de lista: título a la izquierda y un dato breve (fecha) a la
/// derecha, como Mail. Con texto de accesibilidad el dato pasa debajo para no truncar.
struct RowHeadline: View {
    let title: String
    let detail: String
    var isDocument = false
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        if dynamicTypeSize.prefersVerticalLayout {
            VStack(alignment: .leading, spacing: 2) {
                titleText
                detailText
            }
        } else {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                titleText.layoutPriority(1)
                Spacer(minLength: 0)
                detailText
            }
        }
    }

    @ViewBuilder
    private var titleText: some View {
        if isDocument {
            Text(title).font(.body.weight(.semibold)).documentStyle()
        } else {
            Text(title).font(.body.weight(.semibold)).lineLimit(2)
        }
    }

    private var detailText: some View {
        Text(detail)
            .font(.subheadline)
            .foregroundStyle(.secondary)
            .lineLimit(1)
    }
}

/// Fila que se apila en vertical con tamaños de texto de accesibilidad.
struct AdaptiveStack<Content: View>: View {
    var horizontalAlignment: HorizontalAlignment = .leading
    var verticalAlignment: VerticalAlignment = .center
    var spacing: CGFloat = 12
    @ViewBuilder var content: Content
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        let layout = dynamicTypeSize.prefersVerticalLayout
            ? AnyLayout(VStackLayout(alignment: horizontalAlignment, spacing: spacing))
            : AnyLayout(HStackLayout(alignment: verticalAlignment, spacing: spacing))
        layout { content }
    }
}
