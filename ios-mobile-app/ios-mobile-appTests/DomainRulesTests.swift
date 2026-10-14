import Foundation
import Testing
@testable import ios_mobile_app

@Suite("Reglas de dominio y presentación")
struct DomainRulesTests {
    @Test("Bandas del producto", arguments: [
        (900, ScoreBand.a), (750, .a), (749, .b), (600, .b), (599, .c), (450, .c), (449, .d), (300, .d), (299, .e), (0, .e),
    ])
    func bands(score: Int, band: ScoreBand) {
        #expect(ScoreBand(score: score) == band)
    }

    @Test("A es la mejor banda")
    func bandOrdering() {
        #expect(ScoreBand.e < .d)
        #expect(ScoreBand.b < .a)
        #expect(ScoreBand(score: 320) <= .d)
    }

    @Test("Políticas por defecto: nunca hay rechazo automático")
    func defaultPolicies() {
        #expect(DemoPolicy.decide(score: 700, amount: 10_000, partial: false).decision == .preApproved)
        // MONTO_ALTO
        #expect(DemoPolicy.decide(score: 800, amount: 250_000, partial: false).decision == .manualReview)
        // BANDA_BAJA
        #expect(DemoPolicy.decide(score: 410, amount: 5_000, partial: false).decision == .manualReview)
        // Un resultado parcial nunca se preaprueba.
        let partial = DemoPolicy.decide(score: 820, amount: 5_000, partial: true)
        #expect(partial.status == .partial)
        #expect(partial.decision == .manualReview)
    }

    @Test("Cuota: avisos al 80 % y al 100 %")
    func quotaLevels() {
        #expect(TenantUsage(monthlyAssessments: 399, monthlyLimit: 500).level == .ok)
        #expect(TenantUsage(monthlyAssessments: 400, monthlyLimit: 500).level == .warning)
        #expect(TenantUsage(monthlyAssessments: 500, monthlyLimit: 500).level == .exhausted)
        // 0 significa sin límite.
        let unlimited = TenantUsage(monthlyAssessments: 9_999, monthlyLimit: 0)
        #expect(unlimited.level == .ok)
        #expect(unlimited.fractionUsed == nil)
    }

    @Test("Enmascarado: solo administración ve datos completos, y falla cerrado")
    func sensitiveData() {
        let analyst = UserSession(name: "A", username: "a", tenantId: "t", roles: [.analyst])
        let admin = UserSession(name: "B", username: "b", tenantId: "t", roles: [.tenantAdmin])
        let masking = TenantPreferences(maskSensitiveData: true)

        let analystPolicy = SensitiveDataPolicy(preferences: masking, user: analyst)
        #expect(analystPolicy.document("CI-DEMO-0001001") == "CI •••• 1001")
        #expect(analystPolicy.document("NIT-DEMO-9001001") == "NIT •••• 1001")
        #expect(analystPolicy.document("4455667") == "•••• 5667")
        #expect(analystPolicy.amount("Bs 9.800") == "••••••")
        #expect(SensitiveDataPolicy(preferences: nil, user: analyst).shouldMask)
        #expect(!SensitiveDataPolicy(preferences: masking, user: admin).shouldMask)
        #expect(!SensitiveDataPolicy(preferences: TenantPreferences(maskSensitiveData: false), user: analyst).shouldMask)
    }

    @Test("Montos del contrato: dos decimales, punto y sin separador de miles")
    func apiDecimals() {
        #expect(Decimal(12500).apiString == "12500.00")
        #expect(Decimal(string: "1234.567")!.apiString == "1234.57")
        #expect(Decimal(apiString: "9800.50") == Decimal(string: "9800.5"))
        #expect(Decimal(apiString: "") == nil)
    }

    @Test("Formatos en español de Bolivia")
    func bolivianFormats() {
        #expect(Format.money(Money(amount: "9800.00", currency: .bob)) == "Bs\u{00A0}9.800")
        #expect(Format.money(nil) == "—")
        #expect(Format.percent(apiString: "20.3") == "20,3\u{00A0}%")
        #expect(Format.months(1) == "1 mes")
        #expect(Format.seniority(months: 30) == "2 años")
        #expect(Format.sentenceCase("domingo, 11 de octubre") == "Domingo, 11 de octubre")
    }

    @Test("Razones del scorecard en lenguaje directo")
    func reasonLabels() {
        #expect(ScoreReason(raw: "SIN_MORA_12M").label == "Sin mora en los últimos 12 meses")
        #expect(ScoreReason(raw: "MORA_31_90_DIAS (-200)").tone == .warning)
        #expect(ScoreReason(raw: "CODIGO_NUEVO").label == "Codigo Nuevo")
    }
}
