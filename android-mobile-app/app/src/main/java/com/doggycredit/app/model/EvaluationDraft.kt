package com.doggycredit.app.model

import androidx.compose.runtime.*
import com.doggycredit.app.data.*
import kotlinx.coroutines.CancellationException
import java.util.UUID

val creditPurposes = listOf("Capital de trabajo", "Inversión productiva", "Eficiencia energética", "Consumo o consolidación")

enum class ApplicantField { DOCUMENT, ACTIVITY, SENIORITY, INCOME, AMOUNT, TERM, PURPOSE }

/** Borrador en memoria de sesión. No se guarda PII en SavedState ni en preferencias. */
class EvaluationDraft {
    var step by mutableIntStateOf(0)
    var segment by mutableStateOf("PERSONA")
    var document by mutableStateOf("")
    var activity by mutableStateOf("")
    var seniority by mutableStateOf("0")
    var income by mutableStateOf("")
    var amount by mutableStateOf("")
    var term by mutableStateOf("12")
    var purpose by mutableStateOf<String?>(null)
    var bureau by mutableStateOf(false)
    var bank by mutableStateOf(false)
    var wallet by mutableStateOf(false)
    var applicantId by mutableStateOf<String?>(null)
    var consentId by mutableStateOf<String?>(null)
    var result by mutableStateOf<Assessment?>(null)
    var capacity by mutableStateOf<Money?>(null)
    var sources by mutableStateOf<Remote<List<Integration>>>(Remote())
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var existingApplicant by mutableStateOf(false)
    var validationAttempted by mutableStateOf(false); private set
    private var idempotencyKey = UUID.randomUUID().toString()
    private var submittedRequest: NewAssessment? = null
    val inProgress get() = result == null && (document.isNotBlank() || activity.isNotBlank() || income.isNotBlank() ||
        amount.isNotBlank() || purpose != null || seniority != "0" || term != "12" || segment != "PERSONA" || step > 0)
    val applicantErrors: Map<ApplicantField, String> get() = buildMap {
        if (document.trim().length !in 3..64) put(ApplicantField.DOCUMENT, "Escribe un CI o NIT de 3 a 64 caracteres.")
        if (activity.trim().length !in 2..120) put(ApplicantField.ACTIVITY, "Describe la actividad con 2 a 120 caracteres.")
        if (seniority.toIntOrNull()?.let { it in 0..1200 } != true) put(ApplicantField.SENIORITY, "La antigüedad debe estar entre 0 y 1200 meses.")
        if ((Display.decimal(income)?.signum() ?: 0) <= 0) put(ApplicantField.INCOME, "Escribe un ingreso mensual mayor que 0.")
        if (amount.isNotBlank() && (Display.decimal(amount)?.signum() ?: 0) <= 0) put(ApplicantField.AMOUNT, "Escribe un monto mayor que 0 o deja el campo vacío.")
        if (term.toIntOrNull()?.let { it in 1..360 } != true) put(ApplicantField.TERM, "El plazo debe estar entre 1 y 360 meses.")
        if (purpose !in creditPurposes) put(ApplicantField.PURPOSE, "Selecciona el destino del crédito.")
    }
    val validApplicant get() = applicantErrors.isEmpty()
    val requirement get() = when (step) {
        0 -> applicantErrors.values.firstOrNull()
        1 -> if (bureau) null else "El cliente debe autorizar la consulta al buró para continuar."
        else -> null
    }
    fun validateApplicant(): Boolean {
        validationAttempted = true
        return validApplicant
    }
    suspend fun next(api: DoggyCreditApi, onCreated: suspend () -> Unit) {
        if (busy || requirement != null) return
        busy = true; error = null
        try {
            when (step) {
                0 -> {
                    val normalized = document.trim().uppercase(java.util.Locale.ROOT)
                    val existing = api.applicants(size = 1, document = normalized).items.firstOrNull()
                    applicantId = existing?.id ?: try {
                        api.createApplicant(NewApplicant(normalized, activity.trim(), seniority.toInt(), Display.apiAmount(income)!!, purpose = purpose!!, segment = segment)).id
                    } catch (error: ApiException) {
                        if (error.code != "APPLICANT_ALREADY_EXISTS") throw error
                        api.applicants(size = 1, document = normalized).items.firstOrNull()?.id ?: throw error
                    }
                    existingApplicant = existing != null
                    document = normalized
                    step = 1
                }
                1 -> {
                    val authorized = buildList { add("BURO"); if (bank) add("BANCO"); if (wallet) add("WALLET") }
                    consentId = api.createConsent(NewConsent(document.trim(), authorized)).id
                    step = 2
                    loadSources(api)
                }
                2 -> {
                    val consent = consentId ?: run { step = 1; error = "Registra un consentimiento vigente."; return }
                    val request = NewAssessment(document.trim(), consent, purpose, Display.apiAmount(amount), term.toIntOrNull())
                    if (submittedRequest != null && submittedRequest != request) idempotencyKey = UUID.randomUUID().toString()
                    submittedRequest = request
                    result = api.createAssessment(request, idempotencyKey)
                    step = 3
                    // El resultado ya está registrado aunque el Perfil 360 tarde en actualizarse.
                    try { capacity = applicantId?.let { api.applicant(it).profile?.paymentCapacity } }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { capacity = null }
                    onCreated()
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            when ((failure as? ApiException)?.code) {
                "CONSENT_REQUIRED" -> { consentId = null; step = 1; error = "El consentimiento venció o fue revocado. Registra uno nuevo." }
                "QUOTA_EXCEEDED" -> { error = "Tu institución alcanzó la cuota mensual. El borrador se conserva."; onCreated() }
                else -> error = failure.userMessage()
            }
        } finally { busy = false }
    }
    suspend fun loadSources(api: DoggyCreditApi) {
        sources = Remote(loading = true)
        try { sources = Remote(value = api.integrations()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { sources = Remote(error = failure.userMessage()) }
    }
    fun back() { if (!busy && step in 1..2) { step--; error = null } }
}

data class Remote<T>(val value: T? = null, val loading: Boolean = false, val error: String? = null, val status: Int? = null)
