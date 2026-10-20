import SwiftUI
import UIKit

/// Una evaluación: score, decisión de las políticas y sus razones. Un score nunca
/// reemplaza una explicación, así que las razones van junto al número.
struct AssessmentDetailView: View {
    @Environment(SessionStore.self) private var session
    @State private var model: AssessmentDetailModel?

    let assessmentId: UUID

    var body: some View {
        Group {
            switch model?.assessment ?? .idle {
            case .idle, .loading:
                LoadingView(label: "Cargando evaluación…")
            case let .failed(error):
                ErrorStateView(error: error, subject: "la evaluación") { await model?.load() }
            case let .loaded(assessment):
                AssessmentResultList(assessment: assessment)
            }
        }
        .navigationTitle("Evaluación")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            if model == nil { model = AssessmentDetailModel(assessmentId: assessmentId, service: session.service) }
            await model?.load()
        }
    }
}

/// Presentación del resultado, compartida con el último paso de la evaluación.
struct AssessmentResultList<Footer: View>: View {
    let assessment: Assessment
    var paymentCapacity: String?
    var need: String?
    @ViewBuilder var footer: Footer

    var body: some View {
        List {
            Section {
                AssessmentResultHeader(assessment: assessment)
            }
            .listRowBackground(Color.clear)

            if assessment.isPartial {
                Section {
                    NoticeCard(
                        title: "Una fuente no respondió a tiempo",
                        message: "No se inventó ningún dato. Un resultado parcial nunca se preaprueba: queda en revisión.",
                        tone: .warning
                    )
                }
            }

            Section {
                if let decision = assessment.decision {
                    LabeledContent("Decisión") {
                        StatusLine(text: decision.label, tone: decision.tone, font: .body)
                    }
                } else if let status = assessment.status {
                    LabeledContent("Estado", value: status.label)
                }
                if let paymentCapacity {
                    LabeledContent("Capacidad de pago", value: paymentCapacity)
                }
                if let need {
                    LabeledContent("Necesidad", value: need)
                }
            } header: {
                Text("Resumen")
            } footer: {
                Text("Es una recomendación: la decisión final es de la política de crédito de tu institución.")
            }

            if !assessment.topReasons.isEmpty {
                Section {
                    ForEach(assessment.topReasons) { ReasonRow(reason: $0) }
                } header: {
                    Text("Principales razones")
                } footer: {
                    Text("Ordenadas de mayor a menor impacto en el score.")
                }
            }

            Section("Trazabilidad") {
                LabeledContent("Modelo", value: assessment.modelVersion ?? "—")
                if let correlationId = assessment.correlationId {
                    LabeledContent("Correlación") {
                        Text(correlationId)
                            .fontDesign(.monospaced)
                            .lineLimit(1)
                            .truncationMode(.middle)
                            .textSelection(.enabled)
                            .contextMenu {
                                Button("Copiar identificador", systemImage: "doc.on.doc") {
                                    UIPasteboard.general.string = correlationId
                                }
                            }
                            .accessibilityAction(named: "Copiar identificador") {
                                UIPasteboard.general.string = correlationId
                            }
                    }
                }
            }

            footer
        }
        .listStyle(.insetGrouped)
    }
}

extension AssessmentResultList where Footer == EmptyView {
    init(assessment: Assessment, paymentCapacity: String? = nil, need: String? = nil) {
        self.init(assessment: assessment, paymentCapacity: paymentCapacity, need: need) { EmptyView() }
    }
}

struct AssessmentResultHeader: View {
    let assessment: Assessment

    var body: some View {
        VStack(spacing: 14) {
            if let score = assessment.score, let band = assessment.band {
                ScoreRing(score: score, band: band, diameter: 150, lineWidth: 12)
                Text(band.riskLabel)
                    .font(.headline)
                    .foregroundStyle(.secondary)
            } else {
                Image(systemName: "questionmark.circle")
                    .font(.system(size: 64))
                    .foregroundStyle(.secondary)
                Text("Sin score")
                    .font(.headline)
            }
            if assessment.simulated { SimulatedDataBadge() }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
    }
}
