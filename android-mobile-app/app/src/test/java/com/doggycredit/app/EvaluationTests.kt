package com.doggycredit.app

import com.doggycredit.app.data.*
import com.doggycredit.app.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class EvaluationTests {
    private fun draft() = EvaluationDraft().apply { document = " CLIENT-001 "; activity = "Comercio"; income = "5000"; amount = "12000"; purpose = "Capital de trabajo" }
    @Test fun noConsentIsPreselected() { val draft = draft(); assertFalse(draft.bureau); assertFalse(draft.bank); assertFalse(draft.wallet) }
    @Test fun invalidApplicantDoesNotReachApi() = runTest { val draft = EvaluationDraft(); val api = RecordingApi(); draft.next(api) {}; assertEquals(0, api.calls); assertEquals(0, draft.step) }
    @Test fun completeFlowUsesConsentSourcesAndServerResult() = runTest {
        val api = RecordingApi(); val draft = draft(); var notified = false
        draft.next(api) {}; assertEquals(1, draft.step); assertEquals("a1", draft.applicantId)
        draft.bureau = true; draft.wallet = true; draft.next(api) {}; assertEquals(2, draft.step); assertEquals(listOf("BURO", "WALLET"), api.authorized)
        draft.next(api) { notified = true }; assertEquals(3, draft.step); assertEquals(710, draft.result?.score); assertFalse(draft.result!!.simulated); assertTrue(notified); assertFalse(draft.inProgress)
    }
    @Test fun consentRequiredBeforeSources() = runTest { val draft = draft(); val api = RecordingApi(); draft.next(api) {}; val before = api.calls; draft.next(api) {}; assertEquals(1, draft.step); assertEquals(before, api.calls) }
    @Test fun revokedConsentReturnsToAuthorizationStep() = runTest {
        val draft = draft(); val api = RecordingApi().apply { assessmentFailure = ApiException(403, "CONSENT_REQUIRED", "Revocado") }
        draft.next(api) {}; draft.bureau = true; draft.next(api) {}; draft.next(api) {}
        assertEquals(1, draft.step); assertNull(draft.consentId); assertNotNull(draft.error)
    }
    @Test fun retryPreservesIdempotencyAndNeverDropsDraft() = runTest {
        val draft = draft(); val api = RecordingApi().apply { assessmentFailure = java.net.SocketTimeoutException() }
        draft.next(api) {}; draft.bureau = true; draft.next(api) {}; draft.next(api) {}; assertTrue(draft.inProgress)
        api.assessmentFailure = null; draft.next(api) {}; assertEquals(2, api.keys.size); assertEquals(api.keys[0], api.keys[1])
    }
    @Test fun existingApplicantIsReused() = runTest { val draft = draft(); val api = RecordingApi(); draft.next(api) {}; assertTrue(draft.existingApplicant); assertEquals(0, api.createdApplicants) }
    @Test fun backDoesNotEraseUserInput() = runTest { val draft = draft(); draft.next(RecordingApi()) {}; draft.back(); assertEquals(0, draft.step); assertEquals("CLIENT-001", draft.document); assertEquals("5000", draft.income) }
    @Test fun negativeOrMalformedAmountsDoNotEnableContinue() { val draft = draft(); draft.income = "-1"; assertFalse(draft.validApplicant); draft.income = "5000"; draft.amount = "1.2.3"; assertFalse(draft.validApplicant) }
    @Test fun invalidTermIsIdentifiedAndCannotReachApiUntilCorrected() = runTest {
        val draft = draft(); val api = RecordingApi()
        draft.term = "361"
        assertFalse(draft.validateApplicant())
        assertEquals(setOf(ApplicantField.TERM), draft.applicantErrors.keys)
        draft.next(api) {}
        assertEquals(0, api.calls)
        draft.term = "360"
        assertTrue(draft.validateApplicant())
        draft.next(api) {}
        assertEquals(1, draft.step)
    }
    @Test fun decimalCommaAndBoundaryValuesRemainValid() {
        val draft = draft().apply { income = "5000,50"; amount = ""; seniority = "1200"; term = "1" }
        assertTrue(draft.validApplicant)
        draft.seniority = "1201"
        assertEquals(setOf(ApplicantField.SENIORITY), draft.applicantErrors.keys)
        draft.seniority = "0"; draft.income = "1,,2"
        assertEquals(setOf(ApplicantField.INCOME), draft.applicantErrors.keys)
    }
}
internal open class RecordingApi : DoggyCreditApi {
    var calls = 0; var createdApplicants = 0; var authorized: List<String> = emptyList(); val keys = mutableListOf<String>(); var assessmentFailure: Exception? = null
    private val applicant = ApiJson.decodeFromString<Applicant>(APPLICANT)
    override suspend fun applicants(page: Int, size: Int, segment: String?, document: String?) = Page(listOf(applicant), 1).also { calls++ }
    override suspend fun createApplicant(body: NewApplicant) = applicant.also { createdApplicants++; calls++ }
    override suspend fun applicant(id: String) = applicant.also { calls++ }
    override suspend fun createConsent(body: NewConsent) = ApiJson.decodeFromString<Consent>(CONSENT).also { authorized = body.sources; calls++ }
    override suspend fun integrations() = listOf(Integration("BURO", "CONECTADO")).also { calls++ }
    override suspend fun createAssessment(body: NewAssessment, key: String): Assessment { calls++; keys += key; assessmentFailure?.let { throw it }; return Assessment(score = 710, band = "B", simulated = false, reasons = listOf("SIN_MORA_12M")) }
    override suspend fun tenant(): Tenant = error("No invocado")
    override suspend fun usage(): TenantUsage = error("No invocado")
    override suspend fun preferences(): Preferences = error("No invocado")
    override suspend fun consents(applicantId: String): List<Consent> = error("No invocado")
    override suspend fun revokeConsent(id: String): Consent = error("No invocado")
    override suspend fun assessments(applicantId: String?, page: Int, size: Int, since: String?): Page<AssessmentSummary> = error("No invocado")
    override suspend fun assessment(id: String): Assessment = error("No invocado")
    override suspend fun reviewCases(page: Int, size: Int): ReviewPage = error("No invocado")
    override suspend fun resolve(id: String, body: NewResolution): Resolution = error("No invocado")
    override suspend fun analytics(from: String, to: String): AnalyticsSummary = error("No invocado")
}
