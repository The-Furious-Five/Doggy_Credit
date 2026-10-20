import SwiftUI

// MARK: - Paso 1: solicitante

struct ApplicantStepView: View {
    @Bindable var flow: EvaluationFlowModel
    var focusedField: FocusState<EvaluationField?>.Binding
    @Environment(SessionStore.self) private var session
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @State private var confirmsChange = false

    /// Plazos habituales de los productos del catálogo (3 a 120 meses).
    private static let terms = [3, 6, 9, 12, 18, 24, 36, 48, 60, 72, 84, 120]

    var body: some View {
        Form {
            if let problem = flow.problem {
                EvaluationProblemSection(problem: problem)
            }
            if let applicant = flow.existingApplicant {
                Section {
                    LabeledContent("Documento", value: session.privacy.document(flow.trimmedDocument))
                    LabeledContent("Segmento", value: applicant.resolvedSegment.label)
                    LabeledContent("Actividad", value: applicant.activity)
                    LabeledContent("Antigüedad", value: Format.months(applicant.activitySeniorityMonths))
                    LabeledContent("Ingreso registrado", value: session.privacy.amount(Format.money(
                        Money(amount: applicant.monthlyIncome, currency: applicant.currency))))
                    Button("Cambiar de cliente") { confirmsChange = true }
                } header: {
                    Text("Cliente registrado")
                } footer: {
                    Text("Esta evaluación utiliza la ficha registrada. Los datos del cliente no se modifican desde este formulario.")
                }
            } else {
                Section("Tipo de cliente") {
                    Picker("Segmento", selection: $flow.segment) {
                        ForEach(Segment.allCases) { Text($0.label).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    .labelsHidden()
                }

                Section("Identificación") {
                    TextField("Documento de identidad o NIT", text: $flow.document)
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                        .keyboardType(.asciiCapable)
                        .focused(focusedField, equals: .document)
                        .accessibilityIdentifier("evaluation.document")
                        .submitLabel(.next)
                        .onSubmit { focusedField.wrappedValue = .activity }
                    TextField("Actividad económica", text: $flow.activity)
                        .textInputAutocapitalization(.sentences)
                        .focused(focusedField, equals: .activity)
                        .accessibilityIdentifier("evaluation.activity")
                    LabeledContent("Antigüedad") {
                        HStack(spacing: 4) {
                            TextField("0", value: $flow.seniorityMonths, format: .number)
                                .keyboardType(.numberPad)
                                .multilineTextAlignment(.trailing)
                                .focused(focusedField, equals: .seniority)
                            Text("meses").foregroundStyle(.secondary)
                        }
                    }
                    .accessibilityLabel("Antigüedad de la actividad, en meses")
                }
            }

            Section {
                if flow.existingApplicant == nil {
                    LabeledContent("Ingreso mensual") {
                        HStack(spacing: 4) {
                            Spacer(minLength: 0)
                            Text("Bs").foregroundStyle(.secondary)
                            TextField("0", value: $flow.monthlyIncome, format: .number)
                                .keyboardType(.decimalPad)
                                .multilineTextAlignment(.trailing)
                                .focused(focusedField, equals: .income)
                                .accessibilityLabel("Ingreso mensual, en bolivianos")
                                .accessibilityIdentifier("evaluation.income")
                        }
                    }
                }
                LabeledContent("Monto solicitado") {
                    HStack(spacing: 4) {
                        Spacer(minLength: 0)
                        Text("Bs").foregroundStyle(.secondary)
                        TextField("0", value: $flow.requestedAmount, format: .number)
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.trailing)
                            .focused(focusedField, equals: .amount)
                            .accessibilityLabel("Monto solicitado, en bolivianos")
                            .accessibilityIdentifier("evaluation.amount")
                    }
                }
                Picker("Plazo", selection: $flow.termMonths) {
                    ForEach(Self.terms, id: \.self) { Text(Format.months($0)).tag(Int?.some($0)) }
                }
            } header: {
                Text(flow.existingApplicant == nil ? "Ingresos y solicitud" : "Nueva solicitud")
            } footer: {
                Text(flow.existingApplicant == nil
                    ? "El monto es opcional. El ingreso es el declarado por el cliente; si autoriza su banco, el perfil usa el promedio de 6 meses."
                    : "El monto es opcional. Elige el plazo y destino de esta nueva solicitud.")
            }

            Section {
                NavigationLink {
                    PurposeSelectionView(selection: $flow.purpose)
                } label: {
                    LabeledContent("Destino del crédito") {
                        Text(flow.purpose?.rawValue ?? "Elegir")
                    }
                }
                .accessibilityIdentifier("evaluation.purpose")
            } footer: {
                Text("Ayuda a detectar la necesidad real y el producto adecuado.")
                if dynamicTypeSize.isAccessibilitySize, let requirement = flow.pendingRequirement {
                    Text(requirement)
                }
            }

        }
        .confirmationDialog("¿Cambiar de cliente?", isPresented: $confirmsChange, titleVisibility: .visible) {
            Button("Descartar borrador y cambiar", role: .destructive) { flow.restart() }
        } message: {
            Text("Se borrarán los datos de esta solicitud. Lo ya registrado se conserva en el servidor.")
        }
    }
}

/// Destinos del crédito, como una lista de opciones de Ajustes.
struct PurposeSelectionView: View {
    @Binding var selection: CreditPurpose?
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        List {
            Section {
                ForEach(CreditPurpose.allCases) { purpose in
                    SelectableRow(title: purpose.rawValue, subtitle: purpose.hint, symbol: purpose.symbol,
                                  tint: purpose.tint, isSelected: selection == purpose) {
                        selection = purpose
                        dismiss()
                    }
                }
            } footer: {
                Text("Ayuda a detectar la necesidad real y el producto adecuado.")
            }
        }
        .navigationTitle("Destino del crédito")
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: - Paso 2: consentimiento

struct ConsentStepView: View {
    @Bindable var flow: EvaluationFlowModel
    let institution: String?
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        Form {
            if let problem = flow.problem {
                EvaluationProblemSection(problem: problem)
            }
            Section {
                VStack(alignment: .leading, spacing: 10) {
                    Label("Muestra esta pantalla al cliente", systemImage: "hand.raised.fill")
                        .font(.headline)
                        .foregroundStyle(Color.accentColor)
                    Text("El cliente autoriza a \(institution ?? "tu institución") a consultar sus datos financieros con la única finalidad de evaluar esta solicitud. La autorización vence en 90 días y puede revocarse en cualquier momento.")
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.vertical, 4)
            }

            Section {
                Toggle(isOn: $flow.consentBureau) {
                    Label("Consultar burós de crédito", systemImage: DataSource.bureau.symbol)
                }
                .accessibilityIdentifier("evaluation.consent.bureau")
            } header: {
                Text("Obligatorio")
            } footer: {
                Text("Sin la consulta a burós no podemos calcular el score.")
            }

            Section {
                Toggle(isOn: $flow.consentBank) {
                    Label("Consultar cuentas y movimientos bancarios", systemImage: DataSource.bank.symbol)
                }
                Toggle(isOn: $flow.consentWallet) {
                    Label("Consultar billeteras digitales", systemImage: DataSource.wallet.symbol)
                }
            } header: {
                Text("Opcional")
            } footer: {
                Text("Nada viene preseleccionado: el cliente elige cada permiso.")
                if dynamicTypeSize.isAccessibilitySize, let requirement = flow.pendingRequirement {
                    Text(requirement)
                }
            }

        }
        .tint(.accentColor)
    }
}

// MARK: - Paso 3: fuentes

struct SourcesStepView: View {
    @Bindable var flow: EvaluationFlowModel
    @Environment(SessionStore.self) private var session

    var body: some View {
        Form {
            if let problem = flow.problem {
                EvaluationProblemSection(problem: problem)
            }
            Section {
                switch flow.sources {
                case .idle, .loading:
                    ProgressView("Cargando fuentes…").frame(maxWidth: .infinity)
                case .failed:
                    Text("No pudimos cargar el estado de las fuentes; la evaluación igual consulta las conectadas.")
                        .foregroundStyle(.secondary)
                case let .loaded(integrations):
                    // Solo las fuentes que el cliente puede autorizar; las "próximamente" no suman.
                    ForEach(integrations.filter { DataSource(rawValue: $0.source.rawValue) != nil }) { integration in
                        HStack(spacing: 12) {
                            IconTile(symbol: integration.source.symbol,
                                     tint: integration.status == .connected ? Color.accentColor : .gray)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(integration.source.label)
                                if let note = authorizationNote(integration) {
                                    Text(note)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                            Spacer()
                            ToneBadge(text: integration.status.label, tone: integration.status.tone)
                        }
                        .accessibilityElement(children: .combine)
                    }
                }
            } header: {
                Text("Fuentes")
            } footer: {
                Text("Solo se consultan las fuentes conectadas y autorizadas por el cliente.")
            }

            Section("Solicitud") {
                LabeledContent("Cliente") {
                    Text(session.privacy.document(flow.trimmedDocument)).documentStyle()
                }
                LabeledContent("Monto") {
                    Text(flow.requestedAmount.map { session.privacy.amount(Format.amount($0)) } ?? "No indicado")
                }
                LabeledContent("Plazo", value: Format.months(flow.termMonths))
                LabeledContent("Destino", value: flow.purpose?.rawValue ?? "—")
            }

        }
        .refreshable { await flow.loadSources() }
    }

    private func authorizationNote(_ integration: Integration) -> String? {
        guard integration.status == .connected, let source = DataSource(rawValue: integration.source.rawValue) else {
            return nil
        }
        let authorized = switch source {
        case .bureau: flow.consentBureau
        case .bank: flow.consentBank
        case .wallet: flow.consentWallet
        }
        return authorized ? "Autorizada por el cliente" : "Sin autorización: no se consultará"
    }
}

// MARK: - Avisos

struct EvaluationProblemSection: View {
    let problem: EvaluationFlowModel.Problem
    @Environment(SessionStore.self) private var session

    var body: some View {
        Section {
            switch problem {
            case .incompleteApplicant:
                NoticeCard(title: "Faltan datos del solicitante",
                           message: "Completa documento (3 caracteres o más), actividad, ingreso mensual y destino del crédito.",
                           tone: .warning)
            case .existingApplicant:
                NoticeCard(title: "Este cliente ya está registrado",
                           message: "Revisa su ficha antes de continuar. La actividad y el ingreso que escribiste no se guardaron: se utilizarán los datos registrados.",
                           tone: .info)
            case .missingBureauConsent:
                NoticeCard(title: "Falta el consentimiento obligatorio",
                           message: "Sin la consulta a burós no podemos calcular el score.", tone: .warning)
            case .consentInvalid:
                NoticeCard(title: "El consentimiento no es válido",
                           message: "Venció o fue revocado. Registra un consentimiento nuevo para continuar.", tone: .warning)
            case .quotaExceeded:
                NoticeCard(title: "Llegaste al límite de evaluaciones de tu plan",
                           message: session.user.isAdministrator
                               ? "Pausamos las nuevas evaluaciones hasta el próximo mes. Puedes revisar el plan en la consola web."
                               : "Pausamos las nuevas evaluaciones hasta el próximo mes. Pide a quien administra tu institución que revise el plan.",
                           tone: .warning) {
                    if session.user.isAdministrator {
                        Link("Ver tu plan", destination: session.webConsoleURL.appending(path: "configuracion"))
                            .font(.subheadline.weight(.semibold))
                    }
                }
            case let .failed(error):
                NoticeCard(title: "No pudimos completar este paso",
                           message: "\(error.message) Tus datos se conservan y no se duplicará la evaluación.",
                           tone: .danger)
            }
        }
    }
}
