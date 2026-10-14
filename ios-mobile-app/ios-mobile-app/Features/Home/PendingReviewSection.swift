import SwiftUI

/// Carrusel de casos pendientes, como las sugerencias destacadas de Música: cada
/// tarjeta es un momento de marca (pino sólido) con lo justo para decidir.
struct PendingReviewSection: View {
    @Environment(SessionStore.self) private var session

    private var queue: ReviewQueueModel { session.reviewQueue }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Button {
                session.router.selectedTab = .review
            } label: {
                HStack(spacing: 4) {
                    Text("Pendientes de revisión")
                        .font(.title2.bold())
                        .foregroundStyle(.primary)
                    Image(systemName: "chevron.right")
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(.secondary)
                }
            }
            .buttonStyle(.plain)
            .accessibilityAddTraits(.isHeader)
            .accessibilityHint("Abre la bandeja de revisión")

            content
        }
    }

    @ViewBuilder
    private var content: some View {
        switch queue.phase {
        case .idle, .loading:
            FeaturedCaseCard(reviewCase: nil)
                .redacted(reason: .placeholder)
        case let .failed(error) where queue.cases.isEmpty:
            NoticeCard(
                title: error.isForbidden ? "Tu rol no resuelve casos en revisión" : "No pudimos cargar la bandeja",
                message: error.isForbidden
                    ? "Pide a una persona analista o a quien administra tu institución que los revise."
                    : error.message,
                tone: .warning
            )
            .dcCard()
        default:
            if queue.cases.isEmpty {
                allClear
            } else {
                ScrollView(.horizontal) {
                    // HStack (no Lazy) con altura fija a la del contenido: todas las tarjetas
                    // miden lo que la más alta, también con texto de accesibilidad.
                    HStack(alignment: .top, spacing: 12) {
                        ForEach(queue.cases.prefix(8)) { reviewCase in
                            FeaturedCaseCard(reviewCase: reviewCase)
                                .containerRelativeFrame(.horizontal, count: 10, span: 9, spacing: 12)
                        }
                    }
                    .fixedSize(horizontal: false, vertical: true)
                    .scrollTargetLayout()
                }
                .scrollTargetBehavior(.viewAligned)
                .scrollIndicators(.hidden)
                .scrollClipDisabled()
                .accessibilityIdentifier("home.pendingCarousel")
            }
        }
    }

    private var allClear: some View {
        HStack(spacing: 14) {
            Image(systemName: "checkmark.seal.fill")
                .font(.largeTitle)
                .foregroundStyle(Tone.success.color)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                Text("Todo al día")
                    .font(.headline)
                Text("Cuando una política deje una evaluación en revisión manual, aparecerá aquí.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .dcCard()
        .accessibilityElement(children: .combine)
    }
}

/// Tarjeta de un caso: siempre las mismas líneas, en el mismo orden, para que todas
/// midan igual y se lean de un vistazo. Motivo y fecha; cliente y score; destino;
/// solicitud; la razón principal del score (un score nunca va sin su explicación).
struct FeaturedCaseCard: View {
    let reviewCase: ReviewCase?
    @Environment(SessionStore.self) private var session

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @ScaledMetric(relativeTo: .title3) private var ringSize: CGFloat = 70

    private var lineLimit: Int? { dynamicTypeSize.prefersVerticalLayout ? nil : 1 }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            AdaptiveStack(verticalAlignment: .firstTextBaseline, spacing: 8) {
                Label(reviewCase?.reviewReason ?? "Revisión manual",
                      systemImage: reviewCase?.reviewReasonSymbol ?? "person.badge.clock.fill")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Brand.ivory)
                if !dynamicTypeSize.prefersVerticalLayout { Spacer(minLength: 8) }
                Text(reviewCase.map { Format.relative($0.createdAt) } ?? "ahora")
                    .font(.footnote)
                    .foregroundStyle(Brand.ivory.opacity(0.7))
            }
            .lineLimit(lineLimit)

            AdaptiveStack(spacing: 16) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(reviewCase.map { session.privacy.document($0.document) } ?? "Cargando cliente…")
                        .font(.title3.bold())
                        .documentStyle()
                        .foregroundStyle(Brand.ivory)
                    Text(reviewCase?.purpose ?? "Destino no declarado")
                        .font(.subheadline)
                        .foregroundStyle(Brand.ivory.opacity(0.85))
                    Text(request)
                        .font(.subheadline)
                        .foregroundStyle(Brand.ivory.opacity(0.7))
                }
                .lineLimit(lineLimit)
                if !dynamicTypeSize.prefersVerticalLayout { Spacer(minLength: 0) }
                ScoreRing(score: reviewCase?.score ?? 0, band: reviewCase?.band ?? .c,
                          diameter: min(ringSize, 120), onBrandSurface: true)
            }

            Label(reviewCase?.topReasons.first?.label ?? "Sin razones registradas",
                  systemImage: reviewCase?.topReasons.first?.symbol ?? "list.bullet")
                .font(.footnote)
                .foregroundStyle(Brand.ivory.opacity(0.85))
                .lineLimit(lineLimit)

            // El botón queda abajo aunque otra tarjeta del carrusel sea más alta.
            Spacer(minLength: 0)

            Button {
                if let reviewCase { session.resolve(reviewCase) }
            } label: {
                Text("Resolver caso")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Brand.pine)
                    .frame(maxWidth: .infinity)
            }
            .dcProminentButtonStyle()
            .tint(Brand.ivory)
        }
        .padding(18)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(Brand.pine, in: .rect(cornerRadius: Radius.card, style: .continuous))
        .environment(\.colorScheme, .dark)
        .contextMenu {
            if let reviewCase {
                Button("Resolver caso", systemImage: "checkmark.circle") { session.resolve(reviewCase) }
                Button("Ver perfil del cliente", systemImage: "person.text.rectangle") {
                    session.router.homePath.append(.client(reviewCase.applicantId))
                }
            }
        }
        .accessibilityElement(children: .contain)
    }

    private var request: String {
        guard let reviewCase else { return "Bs 00.000 · 00 meses" }
        let term = Format.months(reviewCase.termMonths)
        guard let amount = reviewCase.requestedAmount,
              let visible = session.privacy.visibleAmount(Format.bolivianos(amount)) else { return term }
        return "\(visible) · \(term)"
    }
}
