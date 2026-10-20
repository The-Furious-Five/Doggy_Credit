import SwiftUI

/// Perfil del cliente: lo que hay que saber antes de una visita o una decisión.
struct ClientDetailView: View {
    @Environment(SessionStore.self) private var session
    @State private var model: ClientDetailModel?
    @State private var isConfirmingRevoke = false
    @State private var revokedFeedback = false
    @State private var isHeaderHidden = false
    @ScaledMetric(relativeTo: .body) private var historyRingSize: CGFloat = 44
    @Environment(\.allowsStartingEvaluation) private var allowsStartingEvaluation

    let applicantId: UUID

    var body: some View {
        Group {
            if let model {
                content(model)
            } else {
                LoadingView(label: "Cargando cliente…")
            }
        }
        .background(Color(.systemGroupedBackground))
        .navigationBarTitleDisplayMode(.inline)
        .task {
            if model == nil { model = ClientDetailModel(applicantId: applicantId, service: session.service) }
            await model?.load()
        }
    }

    @ViewBuilder
    private func content(_ model: ClientDetailModel) -> some View {
        switch model.detail {
        case .idle, .loading:
            LoadingView(label: "Cargando cliente…")
        case let .failed(error):
            if error.isNotFound {
                ContentUnavailableView("Cliente no encontrado", systemImage: "person.crop.circle.badge.questionmark",
                                       description: Text("No existe o pertenece a otra institución."))
            } else {
                ErrorStateView(error: error, subject: "el cliente") { await model.load() }
            }
        case let .loaded(detail):
            loaded(detail, model: model)
        }
    }

    private func loaded(_ detail: ApplicantDetail, model: ClientDetailModel) -> some View {
        let applicant = detail.applicant
        return List {
            Section {
                header(applicant, model: model)
            }
            .listRowBackground(Color.clear)
            .listRowInsets(EdgeInsets())

            profileSection(detail.profile)
            historySection(model)
            sourcesSection(detail.profile)
            consentsSection(model)
            declaredSection(applicant)
        }
        .listStyle(.insetGrouped)
        .listSectionSpacing(.compact)
        .refreshable { await model.load() }
        // El documento es el encabezado; en la barra aparece solo cuando el encabezado sale de la vista.
        .navigationTitle(session.privacy.document(applicant.document))
        .toolbar {
            ToolbarItem(placement: .principal) {
                Text(session.privacy.document(applicant.document))
                    .font(.headline)
                    .documentStyle()
                    .opacity(isHeaderHidden ? 1 : 0)
                    .accessibilityHidden(!isHeaderHidden)
            }
        }
        .onScrollGeometryChange(for: Bool.self) { geometry in
            geometry.contentOffset.y + geometry.contentInsets.top > 110
        } action: { _, hidden in
            withAnimation(.easeInOut(duration: 0.2)) { isHeaderHidden = hidden }
        }
        .alert("No pudimos revocar el consentimiento", isPresented: Binding(
            get: { model.revokeError != nil }, set: { if !$0 { model.revokeError = nil } }
        )) {
            Button("Entendido", role: .cancel) {}
        } message: {
            Text("Sigue vigente. \(model.revokeError?.message ?? "")")
        }
        .sensoryFeedback(.success, trigger: revokedFeedback)
    }

    // MARK: Encabezado

    private func header(_ applicant: Applicant, model: ClientDetailModel) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            FlowLayout {
                ToneBadge(text: applicant.resolvedSegment.label, symbol: applicant.resolvedSegment.symbol)
                if model.consents.value != nil {
                    let active = !model.activeConsents.isEmpty
                    ToneBadge(text: active ? "Consentimiento vigente" : "Sin consentimiento vigente",
                              tone: active ? .success : .warning)
                }
            }
            Text(session.privacy.document(applicant.document))
                .font(.largeTitle.bold())
                .documentStyle()
                .minimumScaleFactor(0.7)
                .textSelection(.enabled)
                .accessibilityAddTraits(.isHeader)
            Text("\(applicant.activity) · Cliente desde \(Format.day(applicant.createdAt))")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            if allowsStartingEvaluation {
                Button {
                    session.startEvaluation(document: applicant.document, applicant: applicant)
                } label: {
                    Label("Reevaluar", systemImage: "arrow.clockwise")
                        .fontWeight(.semibold)
                        .frame(maxWidth: .infinity)
                }
                .dcProminentButtonStyle()
                .controlSize(.large)
                .padding(.top, 4)
            }
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 8)
    }

    // MARK: Perfil 360

    @ViewBuilder
    private func profileSection(_ profile: FinancialProfile?) -> some View {
        Section {
            if let profile {
                MetricGrid {
                    MetricTile(label: "Ingreso mensual", value: session.privacy.amount(Format.money(profile.monthlyIncome)),
                               detail: profile.incomeOrigin.label)
                    MetricTile(label: "Deuda vigente", value: session.privacy.amount(Format.money(profile.outstandingDebt)),
                               detail: obligations(profile))
                    MetricTile(label: "Endeudamiento", value: Format.percent(apiString: profile.debtRatioPercent),
                               detail: "Cuota sobre ingreso")
                    MetricTile(label: "Capacidad de pago", value: session.privacy.amount(Format.money(profile.paymentCapacity)),
                               detail: "Mensual, después de egresos")
                    MetricTile(label: "En el sistema financiero", value: Format.seniority(months: profile.financialSystemSeniorityMonths))
                }
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets())
            } else {
                ContentUnavailableView {
                    Label("Sin perfil consolidado", systemImage: "person.text.rectangle")
                } description: {
                    Text("El perfil se arma al evaluar al cliente con sus fuentes autorizadas.")
                }
            }
        } header: {
            Text("Perfil Financiero 360")
        } footer: {
            if let profile {
                Text("Actualizado \(Format.dayAndTime(profile.createdAt)). Un dato ausente es desconocido, nunca cero.")
            }
        }
    }

    private func obligations(_ profile: FinancialProfile) -> String {
        guard let count = profile.obligations else { return "Buró no disponible" }
        return count == 1 ? "1 obligación" : "\(count) obligaciones"
    }

    // MARK: Historial

    @ViewBuilder
    private func historySection(_ model: ClientDetailModel) -> some View {
        Section("Historial de evaluaciones") {
            switch model.history {
            case .idle, .loading:
                ProgressView().frame(maxWidth: .infinity)
            case .failed:
                Text("No pudimos cargar el historial.").foregroundStyle(.secondary)
            case let .loaded(items) where items.isEmpty:
                Text("Sin evaluaciones todavía.").foregroundStyle(.secondary)
            case let .loaded(items):
                ForEach(items) { summary in
                    NavigationLink(value: Route.assessment(summary.id)) {
                        HStack(spacing: 12) {
                            ScoreRing(score: summary.score, band: summary.band, diameter: min(historyRingSize, 72), showsBand: false)
                            VStack(alignment: .leading, spacing: 3) {
                                Text(Format.day(summary.createdAt))
                                    .font(.body.weight(.semibold))
                                StatusLine(text: summary.decision?.label ?? summary.status.label,
                                           tone: summary.decision?.tone ?? summary.status.tone)
                            }
                        }
                        .accessibilityElement(children: .combine)
                    }
                }
            }
        }
    }

    // MARK: Fuentes

    @ViewBuilder
    private func sourcesSection(_ profile: FinancialProfile?) -> some View {
        if let profile {
            Section {
                ForEach(profile.sources) { source in
                    HStack(spacing: 12) {
                        IconTile(symbol: source.source.symbol, tint: source.state == .complete ? Color.accentColor : .gray)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(source.source.label)
                            Text(source.queriedAt.map { "Consultada el \(Format.day($0))" } ?? source.source.dataDescription)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        ToneBadge(text: source.state.label, tone: source.state.tone)
                    }
                    .accessibilityElement(children: .combine)
                }
            } header: {
                Text("Fuentes")
            }
        }
    }

    // MARK: Consentimientos

    @ViewBuilder
    private func consentsSection(_ model: ClientDetailModel) -> some View {
        if let consents = model.consents.value, !consents.isEmpty {
            Section {
                ForEach(consents) { consent in
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text(consent.purpose)
                                .font(.subheadline.weight(.semibold))
                            Spacer()
                            ToneBadge(text: consent.status.label, tone: consent.status.tone)
                        }
                        Text(consent.sources.map(\.label).joined(separator: " · "))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Text(consentDates(consent))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 2)
                    .accessibilityElement(children: .combine)
                }
                if !model.activeConsents.isEmpty {
                    Button(role: .destructive) {
                        isConfirmingRevoke = true
                    } label: {
                        HStack {
                            Text(model.isRevoking ? "Revocando…" : "Revocar consentimiento")
                            Spacer()
                            if model.isRevoking { ProgressView() }
                        }
                    }
                    .disabled(model.isRevoking)
                    // Acción irreversible: el diálogo nace del botón y explica las consecuencias.
                    .confirmationDialog("¿Revocar el consentimiento?", isPresented: $isConfirmingRevoke, titleVisibility: .visible) {
                        Button("Revocar consentimiento", role: .destructive) {
                            Task {
                                if await model.revokeConsents() { revokedFeedback.toggle() }
                            }
                        }
                    } message: {
                        Text("Desde ahora no se consultará ninguna fuente de este cliente y sus evaluaciones nuevas pedirán un consentimiento nuevo. El perfil ya consolidado se conserva.")
                    }
                }
            } header: {
                Text("Consentimientos")
            } footer: {
                Text("Un consentimiento revocado o expirado no permite consultar fuentes. El cliente puede revocarlo en cualquier momento.")
            }
        }
    }

    private func consentDates(_ consent: Consent) -> String {
        switch consent.status {
        case .active: "Otorgado \(Format.day(consent.createdAt)) · vence \(Format.day(consent.expiresAt))"
        case .revoked: "Revocado \(Format.day(consent.revokedAt))"
        case .expired: "Venció \(Format.day(consent.expiresAt))"
        }
    }

    // MARK: Datos declarados

    private func declaredSection(_ applicant: Applicant) -> some View {
        Section("Datos declarados") {
            LabeledContent("Actividad", value: applicant.activity)
            LabeledContent("Antigüedad de la actividad", value: Format.seniority(months: applicant.activitySeniorityMonths))
            LabeledContent("Ingreso declarado") {
                Text(session.privacy.amount(Format.money(Money(amount: applicant.monthlyIncome, currency: applicant.currency))))
            }
            LabeledContent("Destino del crédito", value: applicant.creditPurpose)
        }
    }
}
