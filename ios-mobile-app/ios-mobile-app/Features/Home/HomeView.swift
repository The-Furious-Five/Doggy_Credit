import SwiftUI

/// Inicio: solo lo que pide atención hoy. Los casos por resolver y las últimas
/// evaluaciones; los indicadores del mes viven en Analítica.
struct HomeView: View {
    @Environment(SessionStore.self) private var session
    @Environment(AppRouter.self) private var router
    @State private var dismissedQuotaLevel: TenantUsage.QuotaLevel?

    private var home: HomeModel { session.home }

    var body: some View {
        @Bindable var router = router
        NavigationStack(path: $router.homePath) {
            ScrollView {
                VStack(alignment: .leading, spacing: 30) {
                    greeting
                    if let error = session.dataError {
                        NoticeCard(title: "No pudimos actualizar los datos de tu institución", message: error.message, tone: .warning) {
                            Button("Reintentar") { Task { await session.bootstrap() } }
                        }
                        .dcCard()
                    }
                    quotaNotice
                    if !Platform.supportsTabBottomAccessory, session.evaluation.isInProgress {
                        EvaluationAccessory()
                            .padding(.vertical, 12)
                            .dcGlass(interactive: true, in: .rect(cornerRadius: Radius.card, style: .continuous))
                    }
                    PendingReviewSection()
                    recentSection
                }
                .padding(.horizontal)
                .padding(.bottom, 24)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("Inicio")
            .toolbar { SessionToolbar() }
            .refreshable {
                async let home: Void = home.refresh()
                async let queue: Void = session.reviewQueue.refresh()
                async let institution: Void = session.bootstrap()
                _ = await (home, queue, institution)
            }
            .navigationDestination(for: Route.self) { RouteDestination(route: $0) }
            .task { await home.loadIfNeeded() }
        }
    }

    // MARK: Saludo

    private var greeting: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(Format.sentenceCase(Date.now.formatted(.dateTime.weekday(.wide).day().month(.wide).locale(.bolivia))))
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.secondary)
            Text("\(salutation), \(session.user.firstName)")
                .font(.title3.weight(.semibold))
            if let tenant = session.tenant {
                Text("\(tenant.name) · \(tenant.plan.label)")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.top, 4)
        .accessibilityElement(children: .combine)
    }

    private var salutation: String {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = .laPaz
        switch calendar.component(.hour, from: .now) {
        case 5..<12: return "Buenos días"
        case 12..<19: return "Buenas tardes"
        default: return "Buenas noches"
        }
    }

    // MARK: Cuota (avisos del 80 % y 100 %, E-11)

    @ViewBuilder
    private var quotaNotice: some View {
        if let usage = session.usage, usage.level != .ok, usage.level != dismissedQuotaLevel {
            let exhausted = usage.level == .exhausted
            let limit = Format.number(usage.monthlyLimit)
            let resumes = Format.nextMonthStart()
            NoticeCard(
                title: exhausted
                    ? "Llegaste al límite de \(limit) evaluaciones"
                    : "Usaste \(Format.number(usage.monthlyAssessments)) de \(limit) evaluaciones",
                message: (exhausted
                    ? "Las nuevas evaluaciones se reanudan el \(resumes)."
                    : "Al llegar a \(limit) se pausan las nuevas evaluaciones hasta el \(resumes).")
                    + (session.user.isAdministrator ? "" : " Avisa a quien administra tu institución."),
                tone: exhausted ? .danger : .warning
            ) {
                HStack(spacing: 16) {
                    if session.user.isAdministrator {
                        Link("Ver tu plan", destination: session.webConsoleURL.appending(path: "configuracion"))
                    }
                    Button("Ocultar") { dismissedQuotaLevel = usage.level }
                }
                .font(.subheadline.weight(.semibold))
                .padding(.top, 2)
            }
            .dcCard()
        }
    }

    // MARK: Recientes

    private var recentSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(title: "Evaluaciones recientes")
            switch home.recent {
            case .idle, .loading:
                VStack(spacing: 0) {
                    ForEach(0..<3, id: \.self) { _ in
                        AssessmentRow(summary: nil, applicant: nil).redacted(reason: .placeholder)
                    }
                }
                .dcCard(padding: 0)
            case let .failed(error):
                NoticeCard(title: "No pudimos cargar las evaluaciones", message: error.message, tone: .warning) {
                    Button("Reintentar") { Task { await home.refresh() } }
                        .font(.subheadline.weight(.semibold))
                }
                .dcCard()
            case let .loaded(items) where items.isEmpty:
                ContentUnavailableView {
                    Label("Todavía no hay evaluaciones", systemImage: "doc.text.magnifyingglass")
                } description: {
                    Text("Las evaluaciones que registres aparecerán aquí.")
                } actions: {
                    Button("Nueva evaluación") { session.startEvaluation() }
                        .dcProminentButtonStyle()
                }
                .dcCard()
            case let .loaded(items):
                VStack(spacing: 0) {
                    ForEach(items) { summary in
                        NavigationLink(value: Route.client(summary.applicantId)) {
                            AssessmentRow(summary: summary, applicant: home.applicantsById[summary.applicantId])
                        }
                        .buttonStyle(.plain)
                        if summary.id != items.last?.id {
                            Divider().padding(.leading, 72)
                        }
                    }
                }
                .dcCard(padding: 0)
            }
        }
    }
}

/// Una evaluación en una lista: score, cliente y fecha, y la decisión de las políticas.
struct AssessmentRow: View {
    let summary: AssessmentSummary?
    let applicant: Applicant?
    @Environment(SessionStore.self) private var session
    @ScaledMetric(relativeTo: .body) private var ringSize: CGFloat = 44

    var body: some View {
        HStack(spacing: 14) {
            ScoreRing(score: summary?.score ?? 0, band: summary?.band ?? .c,
                      diameter: min(ringSize, 72), showsBand: false)
            VStack(alignment: .leading, spacing: 3) {
                RowHeadline(title: applicant.map { session.privacy.document($0.document) } ?? "Cliente",
                            detail: summary.map { Format.relative($0.createdAt) } ?? "hoy",
                            isDocument: applicant != nil)
                if let summary {
                    StatusLine(text: summary.decision?.label ?? summary.status.label,
                               tone: summary.decision?.tone ?? summary.status.tone)
                } else {
                    StatusLine(text: "Preaprobado")
                }
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .contentShape(.rect)
        .accessibilityElement(children: .combine)
    }
}

/// Destinos compartidos por las pilas de navegación de todas las pestañas.
struct RouteDestination: View {
    let route: Route

    var body: some View {
        switch route {
        case let .client(id): ClientDetailView(applicantId: id)
        case let .assessment(id): AssessmentDetailView(assessmentId: id)
        }
    }
}
