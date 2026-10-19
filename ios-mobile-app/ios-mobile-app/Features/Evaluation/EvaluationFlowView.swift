import SwiftUI

/// Nueva evaluación en campo. Cerrar la hoja no pierde nada: el borrador queda en curso.
struct EvaluationFlowView: View {
    @Environment(SessionStore.self) private var session
    @Environment(\.dismiss) private var dismiss
    @FocusState private var focusedField: EvaluationField?

    private var flow: EvaluationFlowModel { session.evaluation }

    var body: some View {
        NavigationStack {
            Group {
                switch flow.step {
                case .applicant: ApplicantStepView(flow: flow, focusedField: $focusedField)
                case .consent: ConsentStepView(flow: flow, institution: session.tenant?.name)
                case .sources: SourcesStepView(flow: flow)
                case .result: resultStep
                }
            }
            .dcTopBar {
                StepProgressView(current: flow.step)
                    .padding(.horizontal)
                    .padding(.vertical, 10)
            }
            // Mientras se escribe, el teclado manda: la barra de acciones vuelve al terminar.
            .dcBottomBar {
                if focusedField == nil { actionBar }
            }
            .navigationTitle("Nueva evaluación")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    SheetCloseButton { dismiss() }
                        .disabled(flow.isBusy)
                        .accessibilityHint("La evaluación queda en curso para retomarla")
                }
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("Listo") { focusedField = nil }
                        .fontWeight(.semibold)
                }
            }
            .interactiveDismissDisabled(flow.isBusy)
            .animation(.smooth, value: flow.step)
            .sensoryFeedback(.success, trigger: flow.result?.id)
            .sensoryFeedback(.warning, trigger: flow.problem) { _, problem in problem != nil }
        }
        .onDisappear {
            // Una evaluación terminada no queda "en curso".
            if flow.result != nil { flow.restart() }
        }
    }

    private var resultStep: some View {
        Group {
            if let result = flow.result {
                AssessmentResultList(
                    assessment: result,
                    paymentCapacity: session.privacy.amount(Format.money(flow.paymentCapacity)),
                    need: flow.purpose?.rawValue
                ) {
                    if let applicantId = flow.applicantId {
                        Section {
                            Button {
                                dismiss()
                                session.router.showClient(applicantId)
                            } label: {
                                Label("Ver perfil completo del cliente", systemImage: "person.text.rectangle")
                            }
                        }
                    }
                }
            }
        }
    }

    // MARK: Acciones

    private var actionBar: some View {
        VStack(spacing: 8) {
            if let requirement = flow.pendingRequirement {
                Text(requirement)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            actionButtons
        }
        .padding(.horizontal)
        .padding(.vertical, 10)
    }

    private var actionButtons: some View {
        DCGlassGroup(spacing: 12) {
            HStack(spacing: 12) {
                if flow.step == .result {
                    Button {
                        flow.restart()
                    } label: {
                        Text("Nueva evaluación").frame(maxWidth: .infinity)
                    }
                    .dcSecondaryButtonStyle()

                    Button {
                        dismiss()
                    } label: {
                        Text("Listo").fontWeight(.semibold).frame(maxWidth: .infinity)
                    }
                    .dcProminentButtonStyle()
                } else {
                    if flow.canGoBack {
                        Button {
                            flow.back()
                        } label: {
                            Label("Atrás", systemImage: "chevron.left")
                                .labelStyle(.iconOnly)
                                .frame(width: 24)
                        }
                        .dcSecondaryButtonStyle()
                        .disabled(flow.isBusy)
                        .accessibilityLabel("Atrás")
                    }
                    Button {
                        focusedField = nil
                        Task { await flow.next() }
                    } label: {
                        HStack(spacing: 8) {
                            if flow.isBusy { ProgressView() }
                            Text(busyTitle ?? flow.primaryActionTitle)
                                .fontWeight(.semibold)
                        }
                        .frame(maxWidth: .infinity)
                    }
                    .dcProminentButtonStyle()
                    .disabled(flow.isBusy || flow.pendingRequirement != nil)
                    .accessibilityIdentifier("evaluation.primary")
                }
            }
            .controlSize(.large)
        }
    }

    private var busyTitle: String? {
        guard flow.isBusy else { return nil }
        switch flow.step {
        case .applicant: return "Registrando…"
        case .consent: return "Guardando consentimiento…"
        case .sources: return "Consultando fuentes…"
        case .result: return nil
        }
    }
}

enum EvaluationField: Hashable {
    case document, activity, seniority, income, amount
}

/// Progreso del flujo: pasos completados, actual y pendientes.
struct StepProgressView: View {
    let current: EvaluationFlowModel.Step

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                ForEach(EvaluationFlowModel.Step.allCases) { step in
                    Capsule()
                        .fill(step.rawValue <= current.rawValue ? Color.accentColor : Color.primary.opacity(0.12))
                        .frame(height: 4)
                }
            }
            HStack {
                Text("Paso \(current.rawValue + 1) de \(EvaluationFlowModel.Step.allCases.count)")
                    .foregroundStyle(.secondary)
                Text(current.title)
                    .fontWeight(.semibold)
                Spacer()
            }
            .font(.footnote)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Paso \(current.rawValue + 1) de \(EvaluationFlowModel.Step.allCases.count): \(current.title)")
    }
}
