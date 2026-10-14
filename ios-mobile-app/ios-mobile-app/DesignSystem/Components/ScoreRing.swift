import SwiftUI

/// Score en escala 0–1000 con su banda. El anillo comunica posición; la explicación
/// vive siempre al lado (un score nunca reemplaza una explicación).
struct ScoreRing: View {
    let score: Int
    let band: ScoreBand
    var diameter: CGFloat = 64
    var lineWidth: CGFloat?
    /// Sobre la superficie de marca el anillo es marfil.
    var onBrandSurface = false
    var showsBand = true

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var progress: Double = 0

    private var stroke: CGFloat { lineWidth ?? max(4, diameter * 0.09) }
    private var target: Double { min(1, max(0, Double(score) / 1000)) }
    private var ringColor: Color { onBrandSurface ? Brand.ivory : band.tone.color }
    private var trackColor: Color { onBrandSurface ? Brand.ivory.opacity(0.2) : Color.primary.opacity(0.1) }

    var body: some View {
        ZStack {
            Circle()
                .stroke(trackColor, lineWidth: stroke)
            Circle()
                .trim(from: 0, to: progress)
                .stroke(ringColor, style: StrokeStyle(lineWidth: stroke, lineCap: .round))
                .rotationEffect(.degrees(-90))
            VStack(spacing: -2) {
                Text("\(score)")
                    .font(.system(size: diameter * 0.3, weight: .bold, design: .rounded))
                    .monospacedDigit()
                    .contentTransition(.numericText(value: Double(score)))
                if showsBand, diameter >= 56 {
                    Text("Banda \(band.rawValue)")
                        .font(.system(size: max(9, diameter * 0.12), weight: .semibold, design: .rounded))
                        .foregroundStyle(onBrandSurface ? Brand.ivory.opacity(0.8) : .secondary)
                }
            }
            .minimumScaleFactor(0.6)
            .padding(stroke)
        }
        .frame(width: diameter, height: diameter)
        .onAppear { animate(to: target) }
        .onChange(of: score) { animate(to: target) }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Score \(score) de 1000, banda \(band.rawValue), \(band.riskLabel.lowercased())")
    }

    private func animate(to value: Double) {
        if reduceMotion {
            progress = value
        } else {
            withAnimation(.smooth(duration: 0.8)) { progress = value }
        }
    }
}

#Preview("Score") {
    HStack(spacing: 24) {
        ScoreRing(score: 742, band: .b, diameter: 96)
        ScoreRing(score: 412, band: .d, diameter: 64)
    }
    .padding()
}
