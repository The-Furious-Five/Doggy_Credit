import Foundation
import Testing
@testable import ios_mobile_app

/// Las respuestas del contrato OpenAPI v1 se decodifican tal como las emite el backend.
@Suite("Contrato OpenAPI v1")
struct ContractDecodingTests {
    private func decode<T: Decodable>(_ type: T.Type, _ json: String) throws -> T {
        try JSONDecoder.doggyCredit.decode(T.self, from: Data(json.utf8))
    }

    @Test("Evaluación: `simulado` vale true por defecto y las razones se interpretan")
    func assessment() throws {
        let assessment = try decode(Assessment.self, """
        {"id":"5f0c3f5e-1c1d-4e2b-9a55-0c4a0e2f8b11","estado":"PARCIAL","decision":"REVISION_MANUAL",
         "score":612,"banda":"B","razones":["MORA_1_30_DIAS (-80)","CON_REGISTRO_BURO","PLAZO_HASTA_12_MESES: +20"],
         "modelVersion":"scorecard-v1.3.0","correlationId":"abc"}
        """)
        #expect(assessment.simulated)
        #expect(assessment.isPartial)
        #expect(assessment.decision == .manualReview)
        #expect(assessment.topReasons == [
            ScoreReason(code: "MORA_1_30_DIAS", points: -80),
            ScoreReason(code: "CON_REGISTRO_BURO", points: nil),
            ScoreReason(code: "PLAZO_HASTA_12_MESES", points: 20),
        ])
    }

    @Test("Evaluación vacía: todo desconocido, nada inventado")
    func emptyAssessment() throws {
        let assessment = try decode(Assessment.self, "{}")
        #expect(assessment.score == nil)
        #expect(assessment.band == nil)
        #expect(assessment.topReasons.isEmpty)
    }

    @Test("Solicitante con Perfil 360 (allOf plano) y fechas con microsegundos")
    func applicantDetail() throws {
        let detail = try decode(ApplicantDetail.self, """
        {"id":"0b9a6f3e-77b7-4a63-a2b5-3d6f8f0e2c10","documento":"CI-DEMO-0001001","actividad":"Venta de abarrotes",
         "antiguedadActividadMeses":60,"ingresosMensuales":"4800.00","moneda":"BOB","destinoCredito":"Capital de trabajo",
         "segmento":"PERSONA","creadoEn":"2026-10-01T14:03:22.123456Z",
         "perfil":{"id":"1c9a6f3e-77b7-4a63-a2b5-3d6f8f0e2c10","hash":"ab12","creadoEn":"2026-10-02T09:00:00-04:00",
           "ingresoMensual":{"amount":"5000.00","currency":"BOB"},"origenIngreso":"BANCO",
           "endeudamientoPorcentaje":"20.3",
           "fuentes":[{"fuente":"BURO","estado":"COMPLETA","consultadaEn":"2026-10-02T09:00:00Z","sha256":"ff"},
                      {"fuente":"WALLET","estado":"NO_DISPONIBLE"}]}}
        """)
        #expect(detail.applicant.document == "CI-DEMO-0001001")
        #expect(detail.applicant.resolvedSegment == .person)
        let profile = try #require(detail.profile)
        #expect(profile.monthlyIncome.decimal == Decimal(5000))
        // Ausente significa desconocido, nunca cero.
        #expect(profile.outstandingDebt == nil)
        #expect(profile.sources.map(\.state) == [.complete, .unavailable])
        let created = detail.applicant.createdAt.timeIntervalSince1970
        let expected = try Date.ISO8601FormatStyle().parse("2026-10-01T14:03:22Z").timeIntervalSince1970 + 0.123456
        #expect(abs(created - expected) < 0.000_01)
    }

    @Test("Perfil 360 acepta los nuevos campos opcionales de comparación de ingresos")
    func profileAcceptsOptionalIncomeComparisonFields() throws {
        let profile = try decode(FinancialProfile.self, """
        {"id":"1c9a6f3e-77b7-4a63-a2b5-3d6f8f0e2c10","hash":"ab12","creadoEn":"2026-10-01T14:03:22Z",
         "ingresoMensual":{"amount":"9500.00","currency":"BOB"},"origenIngreso":"BANCO",
         "ingresoDeclarado":{"amount":"5000.00","currency":"BOB"},"discrepanciaIngreso":true,
         "fuentes":[{"fuente":"BANCO","estado":"COMPLETA"}]}
        """)
        #expect(profile.monthlyIncome.decimal == Decimal(9500))
        #expect(profile.sources.map(\.state) == [.complete])
        #expect(profile.outstandingDebt == nil)
    }

    @Test("Bandeja de revisión con productos para aprobar")
    func reviewPage() throws {
        let page = try decode(ReviewCasePage.self, """
        {"items":[{"evaluacionId":"5f0c3f5e-1c1d-4e2b-9a55-0c4a0e2f8b11","solicitanteId":"0b9a6f3e-77b7-4a63-a2b5-3d6f8f0e2c10",
          "documento":"CI-DEMO-0001003","creadoEn":"2026-10-06T10:00:00Z","score":412,"banda":"D",
          "motivo":"SOLICITAR_DATOS","resultadoParcial":false,"razones":["MORA_31_90_DIAS (-200)"]}],
         "total":1,
         "productos":[{"id":"7a1e2b3c-4d5e-4f60-8a9b-0c1d2e3f4a5b","tipo":"VERDE","moneda":"BOB","montoMinimo":"10000.00",
           "montoMaximo":"150000.00","plazoMinimoMeses":12,"plazoMaximoMeses":60,"tasaAnual":"11.50","bandaMinima":"C","activo":true}]}
        """)
        #expect(page.total == 1)
        #expect(page.items.first?.motive == .requestData)
        #expect(page.products.first?.kind == .green)
    }

    @Test("Analítica sin bloque Pro y con fechas sin hora")
    func analyticsWithoutPro() throws {
        let summary = try decode(AnalyticsSummary.self, """
        {"desde":"2026-10-01","hasta":"2026-10-11","plan":"FREE","moneda":"BOB","analiticaAvanzada":false,
         "evaluaciones":0,"scorePromedio":null,"integracionesActivas":1,"porcentajeConRecomendacion":0,"necesidades":[]}
        """)
        #expect(summary.pro == nil)
        #expect(summary.averageScore == nil)
        #expect(APIDate.dayString(summary.from) == "2026-10-01")
    }

    @Test("Errores RFC 9457 con código del catálogo")
    func problemDetail() throws {
        let problem = try decode(ProblemDetail.self, """
        {"type":"https://docs.doggycredit.example/errors/quota-exceeded","title":"Cuota mensual agotada","status":429,
         "detail":"El plan FREE alcanzó su límite mensual de evaluaciones.","code":"QUOTA_EXCEEDED","correlationId":"c-1"}
        """)
        let error = APIError.problem(problem)
        #expect(error.code == .quotaExceeded)
        #expect(error.status == 429)
        #expect(error.message == "El plan FREE alcanzó su límite mensual de evaluaciones.")
    }

    @Test("Las peticiones usan los nombres del contrato")
    func encodesContractNames() throws {
        let body = NewAssessment(holderDocument: "CI-DEMO-1", consentId: UUID(), purpose: "Capital de trabajo",
                                 requestedAmount: Decimal(12500).apiString, termMonths: 12)
        let json = try #require(String(data: JSONEncoder.doggyCredit.encode(body), encoding: .utf8))
        #expect(json.contains(#""titularDocumento":"CI-DEMO-1""#))
        #expect(json.contains(#""montoSolicitado":"12500.00""#))
        #expect(json.contains(#""plazoMeses":12"#))
    }

    @Test("Una resolución de rechazo omite el producto")
    func rejectionOmitsProduct() throws {
        let body = NewResolution(decision: .reject, productId: nil, justification: "Ingresos no verificables")
        let json = try #require(String(data: JSONEncoder.doggyCredit.encode(body), encoding: .utf8))
        #expect(!json.contains("productoId"))
        #expect(json.contains(#""decision":"RECHAZADO""#))
    }
}
