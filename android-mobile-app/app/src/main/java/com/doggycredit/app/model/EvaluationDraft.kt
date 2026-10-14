package com.doggycredit.app.model

import androidx.compose.runtime.*
import com.doggycredit.app.data.*
import kotlinx.coroutines.CancellationException
import java.util.UUID

val creditPurposes = listOf("Capital de trabajo", "Inversión productiva", "Eficiencia energética", "Consumo o consolidación")

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
    private var idempotencyKey = UUID.randomUUID().toString()
    private var submittedRequest: NewAssessment? = null
    val inProgress get() = result == null && (document.isNotBlank() || activity.isNotBlank() || income.isNotBlank() || step > 0)
    val validApplicant get() = document.trim().length in 3..64 && activity.trim().length in 2..120 &&
        (Display.decimal(income)?.signum() ?: 0) > 0 && purpose != null &&
        seniority.toIntOrNull()?.let { it in 0..1200 } == true && (amount.isBlank() || (Display.decimal(amount)?.signum() ?: 0) > 0) &&
        term.toIntOrNull()?.let { it in 1..360 } == true
    val requirement get() = when (step) {
        0 -> if (validApplicant) null else "Completa documento, actividad, ingreso, antigüedad, plazo y destino. Los montos deben ser positivos."
        1 -> if (bureau) null else "El cliente debe autorizar la consulta al buró para continuar."
        else -> null
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
