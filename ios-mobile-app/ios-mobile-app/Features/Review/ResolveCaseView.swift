import SwiftUI

/// Resolver un caso: hechos, razones, decisión, producto y justificación.
/// La acción es irreversible: el botón dice exactamente qué hará.
struct ResolveCaseView: View {
    @Bindable var model: ResolveCaseModel
    @Environment(SessionStore.self) private var session
    @Environment(\.dismiss) private var dismiss
    @FocusState private var isJustificationFocused: Bool

    private var reviewCase: ReviewCase { model.reviewCase }

    var body: some View {
        NavigationStack {
            Form {
                factsSection
                if reviewCase.partialResult {
                    Section {
                        NoticeCard(
                            title: "Una fuente no respondió",
                            message: "El score se calculó sin ella. No se inventó ningún dato.",
                            tone: .warning
                        )
                    }
                }
                reasonsSection
                decisionSection
                if model.isApproving { productSection }
                justificationSection
                if let problem = model.problem { problemSection(problem) }
            }
            .navigationTitle("Resolver caso")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { toolbar }
            .navigationDestination(for: Route.self) { RouteDestination(route: $0) }
            // Dentro de esta hoja no se puede abrir otra (la evaluación).
            .environment(\.allowsStartingEvaluation, false)
            .interactiveDismissDisabled(model.isSaving)
            .sensoryFeedback(.warning, trigger: model.problem) { _, problem in problem != nil }
        }
    }

    // MARK: Secciones

    private var factsSection: some View {
        Section {
            AdaptiveStack(spacing: 16) {
                ScoreRing(score: reviewCase.score, band: reviewCase.band, diameter: 76)
                VStack(alignment: .leading, spacing: 6) {
                    Text(session.privacy.document(reviewCase.document))
                        .font(.headline)
                        .documentStyle()
                    Text(reviewCase.band.riskLabel)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    StatusLine(text: reviewCase.reviewReason, tone: reviewCase.reviewReasonTone)
                }
            }
            .padding(.vertical, 4)
            LabeledContent("Monto solicitado") {
                Text(reviewCase.requestedAmount.map { session.privacy.amount(Format.bolivianos($0)) } ?? "—")
            }
            LabeledContent("Plazo", value: Format.months(reviewCase.termMonths))
            LabeledContent("Destino declarado", value: reviewCase.purpose ?? "—")
            LabeledContent("Recibido", value: Format.dayAndTime(reviewCase.createdAt))
            NavigationLink(value: Route.client(reviewCase.applicantId)) {
                Label("Ver perfil del cliente", systemImage: "person.text.rectangle")
            }
        }
    }

    @ViewBuilder
    private var reasonsSection: some View {
        if !reviewCase.topReasons.isEmpty {
            Section("Principales razones") {
                ForEach(reviewCase.topReasons) { reason in
                    ReasonRow(reason: reason)
                }
            }
        }
    }

    private var decisionSection: some View {
        Section {
            SelectableRow(title: "Aprobar", subtitle: "Se otorga el producto que elijas",
                          symbol: "checkmark.circle.fill", tint: Tone.success.color,
                          isSelected: model.decision == .approve) { model.decision = .approve }
            SelectableRow(title: "Rechazar", subtitle: "La solicitud no continúa",
                          symbol: "xmark.circle.fill", tint: Tone.danger.color,
                          isSelected: model.decision == .reject) { model.decision = .reject }
        } header: {
            Text("Decisión")
        } footer: {
            if model.decision == nil {
                Text("Elige si apruebas o rechazas el caso.")
            }
        }
    }

    @ViewBuilder
    private var productSection: some View {
        if model.canApprove {
            Section {
                ForEach(model.products) { product in
                    SelectableRow(title: product.kind.label, subtitle: product.rangeDescription,
                                  symbol: product.kind.symbol, tint: product.kind.tint,
                                  isSelected: model.productId == product.id) { model.productId = product.id }
                }
            } header: {
                Text("Producto que se otorga")
            } footer: {
                if model.productId == nil { Text("Elige el producto que se otorga.") }
            }
        } else {
            Section {
                NoticeCard(
                    title: "No hay productos activos",
                    message: "Para aprobar hace falta al menos un producto activo en el catálogo. Puedes rechazar el caso o pedir a quien administra tu institución que active uno.",
                    tone: .warning
                )
            }
        }
    }

    private var justificationSection: some View {
        Section {
            TextField("Por qué tomas esta decisión", text: $model.justification, axis: .vertical)
                .lineLimit(4...10)
                .focused($isJustificationFocused)
                .accessibilityIdentifier("resolve.justification")
                .onChange(of: model.justification) { _, newValue in
                    if newValue.count > NewResolution.maximumJustificationLength {
                        model.justification = String(newValue.prefix(NewResolution.maximumJustificationLength))
                    }
                }
        } header: {
            Text("Justificación")
        } footer: {
            HStack(alignment: .top) {
                Text("Al menos \(NewResolution.minimumJustificationLength) caracteres. Queda registrada en la auditoría con tu usuario.")
                Spacer()
                Text(model.missingCharacters > 0 ? "Faltan \(model.missingCharacters)" : "\(model.trimmedJustification.count)/\(NewResolution.maximumJustificationLength)")
                    .monospacedDigit()
                    .foregroundStyle(model.missingCharacters > 0 ? Tone.warning.color : .secondary)
            }
        }
    }

    private func problemSection(_ problem: ResolveCaseModel.Problem) -> some View {
        Section {
            switch problem {
            case .invalid:
                NoticeCard(title: "La API rechazó la resolución",
                           message: "Revisa la decisión, el producto y la justificación. Tus datos se conservan.", tone: .warning)
            case .alreadyResolved:
                NoticeCard(title: "Este caso ya no está pendiente",
                           message: "Otra persona lo resolvió mientras lo revisabas. Vuelve a la bandeja para ver los casos que siguen pendientes.",
                           tone: .warning) {
                    Button("Volver a la bandeja") { dismiss() }
                        .font(.subheadline.weight(.semibold))
                }
            case .productUnavailable:
                NoticeCard(title: "Ese producto ya no está disponible",
                           message: "Se desactivó mientras revisabas el caso. Elige otro; la lista ya se actualizó.", tone: .warning)
            case .forbidden:
                NoticeCard(title: "Tu rol no puede resolver casos",
                           message: "Pide a una persona analista o a quien administra tu institución que lo resuelva.", tone: .warning)
            case let .failed(error):
                NoticeCard(title: "No pudimos guardar la resolución",
                           message: "Tu decisión y tu justificación se conservan. \(error.message)", tone: .danger)
            }
        }
    }

    // MARK: Toolbar

    @ToolbarContentBuilder
    private var toolbar: some ToolbarContent {
        ToolbarItem(placement: .cancellationAction) {
            SheetCancelButton { dismiss() }
                .disabled(model.isSaving)
        }
        ToolbarItem(placement: .confirmationAction) {
            SheetConfirmButton(title: confirmTitle, isBusy: model.isSaving) {
                Task {
                    isJustificationFocused = false
                    if await model.submit() != nil {
                        // La hoja se cierra: la confirmación háptica la emite la vista raíz.
                        session.router.completedResolutions += 1
                        dismiss()
                    }
                }
            }
            .disabled(!model.canSubmit)
        }
    }

    private var confirmTitle: String {
        switch model.decision {
        case .approve: "Aprobar"
        case .reject: "Rechazar"
        case nil: "Resolver"
        }
    }
}

/// Una razón del score con su impacto.
struct ReasonRow: View {
    let reason: ScoreReason

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: reason.symbol)
                .foregroundStyle(reason.tone.color)
                .font(.title3)
                .accessibilityHidden(true)
            Text(reason.label)
            Spacer()
            if let points = reason.points, points != 0 {
                Text(points > 0 ? "+\(points)" : "\(points)")
                    .font(.subheadline.weight(.semibold))
                    .monospacedDigit()
                    .foregroundStyle(reason.tone.color)
            }
        }
        .accessibilityElement(children: .combine)
    }
}
