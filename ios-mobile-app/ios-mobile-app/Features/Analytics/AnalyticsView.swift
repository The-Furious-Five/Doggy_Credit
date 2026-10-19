import Charts
import SwiftUI

/// KPIs agregados de la institución, sin datos personales.
struct AnalyticsView: View {
    @Environment(SessionStore.self) private var session

    private var analytics: AnalyticsModel { session.analytics }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    Picker("Período", selection: Binding(
                        get: { analytics.period },
                        set: { period in Task { await analytics.select(period) } }
                    )) {
                        ForEach(AnalyticsModel.Period.allCases) { Text($0.rawValue).tag($0) }
                    }
                    .pickerStyle(.segmented)

                    content
                }
                .padding(.horizontal)
                .padding(.bottom, 24)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("Analítica")
            .toolbar { SessionToolbar() }
            .refreshable { await analytics.refresh() }
            .task { await analytics.loadIfNeeded() }
        }
    }

    @ViewBuilder
    private var content: some View {
        switch analytics.summary {
        case .idle, .loading:
            LoadingView(label: "Cargando indicadores…")
        case let .failed(error):
            ErrorStateView(error: error, subject: "los indicadores") { await analytics.refresh() }
        case let .loaded(summary):
            AnalyticsContent(summary: summary)
                .opacity(analytics.isRefreshing ? 0.5 : 1)
                .animation(.default, value: analytics.isRefreshing)
        }
    }
}

private struct AnalyticsContent: View {
    let summary: AnalyticsSummary
    @Environment(SessionStore.self) private var session

    var body: some View {
        VStack(alignment: .leading, spacing: 28) {
            HStack(spacing: 8) {
                Text(Format.range(from: summary.from, to: summary.to))
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                Spacer(minLength: 4)
            }

            MetricGrid {
                MetricTile(label: "Evaluaciones", value: Format.number(summary.assessments))
                MetricTile(label: "Score promedio", value: Format.number(summary.averageScore), detail: "De 0 a 1000")
                MetricTile(label: "Con producto recomendado", value: Format.percent(summary.recommendationPercent))
                MetricTile(label: "Integraciones activas", value: Format.number(summary.activeIntegrations))
            }

            if summary.assessments == 0 {
                ContentUnavailableView(
                    "Sin evaluaciones en este período",
                    systemImage: "chart.bar.xaxis",
                    description: Text("Elige otro período o registra una nueva evaluación para ver resultados.")
                )
                .dcCard()
            } else {
                needsChart
            }

            if let pro = summary.pro {
                advanced(pro)
            } else {
                proUpsell
            }

            Text("Fechas inclusivas, hora de Bolivia.")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
    }

    // MARK: Necesidades

    private var needsChart: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(title: "Necesidades detectadas", subtitle: "Destino del crédito de cada evaluación")
            Chart(summary.needs) { need in
                BarMark(
                    x: .value("Porcentaje", need.percent),
                    y: .value("Necesidad", need.category.label)
                )
                .foregroundStyle(need.category.tint)
                .cornerRadius(6)
                .annotation(position: .trailing, alignment: .leading) {
                    Text(Format.percent(need.percent))
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                }
            }
            .chartXAxis(.hidden)
            .chartXScale(domain: 0...max(100, (summary.needs.map(\.percent).max() ?? 0) * 1.25))
            .chartYAxis {
                AxisMarks { _ in
                    AxisValueLabel()
                        .font(.subheadline)
                }
            }
            .frame(height: CGFloat(max(1, summary.needs.count)) * 52)
            .dcCard()
            .accessibilityLabel("Necesidades detectadas")
        }
    }

    // MARK: Pro

    private func advanced(_ pro: AdvancedAnalytics) -> some View {
        VStack(alignment: .leading, spacing: 28) {
            VStack(alignment: .leading, spacing: 12) {
                SectionHeader(title: "Analítica avanzada")
                MetricGrid {
                    MetricTile(label: "Preaprobadas", value: Format.percent(pro.conversionPercent),
                               detail: "Del total de evaluaciones")
                    MetricTile(label: "Tiempo de decisión", value: Format.duration(milliseconds: pro.medianDecisionTimeMs),
                               detail: "Mediana")
                }
                MetricTile(label: "Monto recomendado",
                           value: session.privacy.amount(Format.money(Money(amount: pro.recommendedAmount, currency: .bob))),
                           detail: "Suma del primer producto de cada recomendación")
            }

            if !pro.products.isEmpty {
                VStack(alignment: .leading, spacing: 12) {
                    SectionHeader(title: "Recomendaciones por producto")
                    HStack(spacing: 20) {
                        Chart(pro.products) { product in
                            SectorMark(
                                angle: .value("Porcentaje", product.percent),
                                innerRadius: .ratio(0.62),
                                angularInset: 2
                            )
                            .foregroundStyle(product.kind.tint)
                            .cornerRadius(4)
                        }
                        .frame(width: 130, height: 130)
                        .accessibilityHidden(true)

                        VStack(alignment: .leading, spacing: 10) {
                            ForEach(pro.products) { product in
                                HStack(spacing: 8) {
                                    Circle().fill(product.kind.tint).frame(width: 10, height: 10)
                                    VStack(alignment: .leading, spacing: 1) {
                                        Text(product.kind.label)
                                            .font(.subheadline.weight(.semibold))
                                        Text("\(Format.percent(product.percent)) · \(Format.number(product.count))")
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    }
                                }
                                .accessibilityElement(children: .combine)
                            }
                        }
                    }
                    .dcCard()
                }
            }

            if !pro.models.isEmpty {
                VStack(alignment: .leading, spacing: 12) {
                    SectionHeader(title: "Monitoreo de modelos", subtitle: "Último control semanal de deriva (PSI)")
                    VStack(spacing: 0) {
                        ForEach(pro.models) { model in
                            HStack(spacing: 12) {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(model.version)
                                        .font(.subheadline.weight(.semibold))
                                        .fontDesign(.monospaced)
                                    Text("PSI \(Format.number(model.psi, fractionDigits: 3)) · \(Format.number(model.currentSamples)) muestras")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                                Spacer()
                                ToneBadge(text: model.state.label, tone: model.state.tone)
                            }
                            .padding(14)
                            .accessibilityElement(children: .combine)
                            if model.id != pro.models.last?.id { Divider().padding(.leading, 14) }
                        }
                    }
                    .dcCard(padding: 0)
                }
            }
        }
    }

    private var proUpsell: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Analítica avanzada")
                .font(.headline)
            Text("Preaprobadas, tiempo de decisión, productos y modelos están en el Plan Pro."
                 + (session.user.isAdministrator ? "" : " Pide a quien administra tu institución que revise el plan."))
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
            if session.user.isAdministrator {
                Link("Ver planes en la consola web", destination: session.webConsoleURL.appending(path: "configuracion"))
                    .font(.subheadline.weight(.semibold))
                    .padding(.top, 2)
            }
        }
        .dcCard()
    }
}
