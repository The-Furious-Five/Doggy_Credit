package com.doggycredit.app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

val ApiJson = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true; encodeDefaults = true }

@Serializable data class Page<T>(val items: List<T>, val total: Int)
@Serializable data class Tenant(
    val id: String, @SerialName("nombre") val name: String, @SerialName("nit") val taxId: String,
    @SerialName("tipo") val kind: String, val plan: String, @SerialName("pais") val country: String,
    @SerialName("moneda") val currency: String, @SerialName("activo") val active: Boolean
)
@Serializable data class TenantUsage(@SerialName("evaluacionesMes") val used: Int, @SerialName("limiteMensual") val limit: Int) {
    val unlimited get() = limit == 0
    val fraction get() = if (unlimited) null else (used.toFloat() / limit).coerceIn(0f, 1f)
    val warning get() = !unlimited && used.toLong() * 100 >= limit.toLong() * 80
    val exhausted get() = !unlimited && used >= limit
}
@Serializable data class Preferences(@SerialName("enmascararDatos") val maskSensitiveData: Boolean)
@Serializable data class Applicant(
    val id: String, @SerialName("documento") val document: String, @SerialName("actividad") val activity: String,
    @SerialName("antiguedadActividadMeses") val seniorityMonths: Int,
    @SerialName("ingresosMensuales") val monthlyIncome: String, @SerialName("moneda") val currency: String,
    @SerialName("destinoCredito") val purpose: String, @SerialName("segmento") val segment: String? = null,
    @SerialName("creadoEn") val createdAt: String, @SerialName("perfil") val profile: FinancialProfile? = null
)
@Serializable data class NewApplicant(
    @SerialName("documento") val document: String, @SerialName("actividad") val activity: String,
    @SerialName("antiguedadActividadMeses") val seniorityMonths: Int,
    @SerialName("ingresosMensuales") val monthlyIncome: String, @SerialName("moneda") val currency: String = "BOB",
    @SerialName("destinoCredito") val purpose: String, @SerialName("segmento") val segment: String
)
@Serializable data class Money(val amount: String, val currency: String)
@Serializable data class FinancialProfile(
    val id: String, val hash: String, @SerialName("creadoEn") val createdAt: String,
    @SerialName("ingresoMensual") val monthlyIncome: Money, @SerialName("origenIngreso") val incomeOrigin: String,
    @SerialName("deudaVigente") val debt: Money? = null, @SerialName("obligaciones") val obligations: Int? = null,
    @SerialName("cuotaMensual") val installment: Money? = null,
    @SerialName("endeudamientoPorcentaje") val debtPercent: String? = null,
    @SerialName("capacidadPago") val paymentCapacity: Money? = null,
    @SerialName("antiguedadSistemaFinancieroMeses") val financialSeniority: Int? = null,
    @SerialName("fuentes") val sources: List<ProfileSource>
)
@Serializable data class ProfileSource(
    @SerialName("fuente") val source: String, @SerialName("estado") val state: String,
    @SerialName("consultadaEn") val queriedAt: String? = null, val sha256: String? = null
)
@Serializable data class Consent(
    val id: String, @SerialName("titularDocumento") val document: String,
    @SerialName("fuentes") val sources: List<String>, @SerialName("finalidad") val purpose: String,
    @SerialName("vigenciaDias") val validityDays: Int? = null, @SerialName("estado") val status: String,
    @SerialName("creadoEn") val createdAt: String? = null, @SerialName("expiraEn") val expiresAt: String? = null,
    @SerialName("revocadoEn") val revokedAt: String? = null
)
@Serializable data class NewConsent(
    @SerialName("titularDocumento") val document: String, @SerialName("fuentes") val sources: List<String>,
    @SerialName("finalidad") val purpose: String = "Evaluación de crédito", @SerialName("vigenciaDias") val validityDays: Int = 90
)
@Serializable data class Integration(@SerialName("fuente") val source: String, @SerialName("estado") val status: String)
@Serializable data class Assessment(
    val id: String? = null, @SerialName("estado") val status: String? = null, val decision: String? = null,
    val score: Int? = null, @SerialName("banda") val band: String? = null,
    @SerialName("razones") val reasons: List<String> = emptyList(), val modelVersion: String? = null,
    @SerialName("simulado") val simulated: Boolean = true, val correlationId: String? = null
)
@Serializable data class NewAssessment(
    @SerialName("titularDocumento") val document: String, val consentId: String,
    @SerialName("proposito") val purpose: String? = null, @SerialName("montoSolicitado") val requestedAmount: String? = null,
    @SerialName("plazoMeses") val termMonths: Int? = null
)
@Serializable data class AssessmentSummary(
    val id: String, @SerialName("solicitanteId") val applicantId: String, @SerialName("estado") val status: String,
    val decision: String? = null, val score: Int, @SerialName("banda") val band: String,
    @SerialName("montoSolicitado") val requestedAmount: String? = null, @SerialName("plazoMeses") val termMonths: Int? = null,
    @SerialName("creadoEn") val createdAt: String
)
@Serializable data class ReviewCase(
    @SerialName("evaluacionId") val assessmentId: String, @SerialName("solicitanteId") val applicantId: String,
    @SerialName("documento") val document: String, @SerialName("creadoEn") val createdAt: String,
    val score: Int, @SerialName("banda") val band: String, @SerialName("motivo") val motive: String,
    @SerialName("resultadoParcial") val partial: Boolean, @SerialName("razones") val reasons: List<String>,
    @SerialName("montoSolicitado") val requestedAmount: String? = null, @SerialName("plazoMeses") val termMonths: Int? = null,
    @SerialName("proposito") val purpose: String? = null
)
@Serializable data class ReviewPage(val items: List<ReviewCase>, val total: Int, @SerialName("productos") val products: List<Product>)
@Serializable data class Product(
    val id: String, @SerialName("tipo") val kind: String, @SerialName("moneda") val currency: String,
    @SerialName("montoMinimo") val minimumAmount: String, @SerialName("montoMaximo") val maximumAmount: String,
    @SerialName("plazoMinimoMeses") val minimumTerm: Int, @SerialName("plazoMaximoMeses") val maximumTerm: Int,
    @SerialName("tasaAnual") val annualRate: String, @SerialName("bandaMinima") val minimumBand: String,
    @SerialName("activo") val active: Boolean
)
@Serializable data class NewResolution(val decision: String, @SerialName("productoId") val productId: String? = null, @SerialName("justificacion") val justification: String)
@Serializable data class Resolution(
    @SerialName("evaluacionId") val assessmentId: String, val decision: String,
    @SerialName("productoId") val productId: String? = null, @SerialName("justificacion") val justification: String,
    @SerialName("resueltoPor") val resolvedBy: String, @SerialName("resueltoEn") val resolvedAt: String
)
@Serializable data class AnalyticsSummary(
    @SerialName("desde") val from: String, @SerialName("hasta") val to: String, val plan: String,
    @SerialName("moneda") val currency: String, @SerialName("analiticaAvanzada") val advanced: Boolean,
    @SerialName("evaluaciones") val assessments: Int, @SerialName("scorePromedio") val averageScore: Double? = null,
    @SerialName("integracionesActivas") val activeIntegrations: Int,
    @SerialName("porcentajeConRecomendacion") val recommendationPercent: Double,
    @SerialName("necesidades") val needs: List<Need>, val pro: AdvancedAnalytics? = null
)
@Serializable data class Need(@SerialName("categoria") val category: String, @SerialName("cantidad") val count: Int, @SerialName("porcentaje") val percent: Double)
@Serializable data class AdvancedAnalytics(
    @SerialName("conversionPorcentaje") val conversion: Double, @SerialName("tiempoDecisionMedianaMs") val decisionTime: Double? = null,
    @SerialName("montoRecomendado") val recommendedAmount: String, @SerialName("recomendacionesPorProducto") val products: List<ProductBreakdown>,
    @SerialName("modelos") val models: List<ModelMonitor>
)
@Serializable data class ProductBreakdown(
    @SerialName("tipo") val kind: String, @SerialName("cantidad") val count: Int, @SerialName("porcentaje") val percent: Double,
    @SerialName("montoRecomendado") val recommendedAmount: String
)
@Serializable data class ModelMonitor(
    val version: String, val psi: Double? = null, @SerialName("estado") val state: String,
    @SerialName("desde") val from: String, @SerialName("hasta") val to: String,
    @SerialName("muestrasActuales") val samples: Int, @SerialName("muestrasBase") val baselineSamples: Int
)

data class UserSession(val name: String, val username: String, val tenantId: String, val roles: List<String>) {
    val administrator get() = roles.any { it == "TENANT_ADMIN" || it == "PLATFORM_ADMIN" }
    val canResolve get() = roles.any { it == "ANALYST" || it == "TENANT_ADMIN" }
    val initials get() = name.split(" ").filter(String::isNotBlank).take(2).mapNotNull { it.firstOrNull() }.joinToString("").uppercase(Locale.ROOT)
    fun shouldMask(preferences: Preferences?) = preferences == null || (preferences.maskSensitiveData && !administrator)
}

object Display {
    val locale = Locale.forLanguageTag("es-BO")
    val zone: ZoneId = ZoneId.of("America/La_Paz")
    fun number(value: Number?, decimals: Int = 0): String = value?.let {
        NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = decimals }.format(it)
    } ?: "—"
    fun money(value: String?, currency: String = "BOB"): String = value?.toBigDecimalOrNull()?.let {
        (if (currency == "BOB") "Bs " else "$currency ") + number(it, 2)
    } ?: "—"
    fun money(value: Money?): String = value?.let { money(it.amount, it.currency) } ?: "—"
    fun percent(value: Number?): String = value?.let { "${number(it, 1)} %" } ?: "—"
    fun date(value: String?): String = value?.let {
        runCatching { Instant.parse(it).atZone(zone).format(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", locale)) }
            .getOrElse { runCatching { LocalDate.parse(value).format(DateTimeFormatter.ofPattern("d MMM yyyy", locale)) }.getOrDefault(value) }
    } ?: "—"
    fun document(value: String, mask: Boolean): String {
        if (!mask) return value
        val prefix = Regex("^([A-Za-z]{2,4})[- ]").find(value)?.groupValues?.get(1)?.uppercase(Locale.ROOT)
        return (prefix?.let { "$it " } ?: "") + "•••• ${value.takeLast(4)}"
    }
    fun amount(value: String, mask: Boolean) = if (mask) "••••••" else value
    fun decimal(value: String): BigDecimal? = value.trim().replace(',', '.').toBigDecimalOrNull()
    fun apiAmount(value: String): String? = decimal(value)?.setScale(2, RoundingMode.HALF_UP)?.toPlainString()
    fun label(raw: String?): String = when (raw) {
        null -> "No disponible"
        "PERSONA" -> "Persona"; "EMPRENDEDOR" -> "Emprendedor"; "PYME" -> "PYME"
        "BURO" -> "Buró de crédito"; "BANCO" -> "Banco"; "WALLET" -> "Billetera móvil"
        "OPEN_BANKING" -> "Open banking"; "FACTURACION" -> "Facturación electrónica"; "GARANTIAS" -> "Registro de garantías"
        "PREAPROBADO" -> "Preaprobado"; "REVISION_MANUAL" -> "Revisión manual"; "SOLICITAR_DATOS" -> "Solicitar datos"
        "PARCIAL" -> "Resultado parcial"; "PENDIENTE" -> "Pendiente"; "FALLIDA" -> "Fallida"
        "APROBADO" -> "Aprobado"; "RECHAZADO" -> "Rechazado"; "ACTIVO" -> "Vigente"; "REVOCADO" -> "Revocado"; "EXPIRADO" -> "Expirado"
        "CONECTADO" -> "Conectado"; "DISPONIBLE" -> "Disponible"; "REQUIERE_PRO" -> "Plan Pro"; "PROXIMAMENTE" -> "Próximamente"
        "COMPLETA" -> "Completa"; "SIN_REGISTRO" -> "Sin registro"; "NO_DISPONIBLE" -> "No disponible"; "NO_CONSULTADA" -> "No consultada"
        "CAPITAL_TRABAJO" -> "Capital de trabajo"; "INVERSION_PRODUCTIVA" -> "Inversión productiva"
        "EFICIENCIA_ENERGETICA" -> "Eficiencia energética"; "CONSUMO_CONSOLIDACION" -> "Consumo o consolidación"
        "MICROCREDITO" -> "Microcrédito"; "VERDE" -> "Crédito verde"
        "TENANT_ADMIN" -> "Administrador de institución"; "ANALYST" -> "Analista"; "PLATFORM_ADMIN" -> "Administrador de plataforma"
        "ESTABLE" -> "Estable"; "VIGILAR" -> "Vigilar"; "ALERTA" -> "Alerta"; "INSUFICIENTE" -> "Muestras insuficientes"
        "FREE" -> "Gratis"; "PRO" -> "Pro"; "ENTERPRISE" -> "Enterprise"
        else -> raw.lowercase(locale).replace('_', ' ').replaceFirstChar { it.titlecase(locale) }
    }
    fun reason(raw: String): String {
        val code = raw.substringBefore('(').substringBefore(':').trim()
        val label = when (code) {
            "SIN_MORA_12M" -> "Sin mora en los últimos 12 meses"
            "CON_REGISTRO_BURO" -> "Con historial en el buró"
            "SIN_REGISTRO_BURO" -> "Sin historial en el buró"
            "MORA_1_30_DIAS" -> "Mora de 1 a 30 días"
            "MORA_31_90_DIAS" -> "Mora de 31 a 90 días"
            "MORA_MAYOR_90_DIAS" -> "Mora mayor a 90 días"
            else -> label(code)
        }
        return label + raw.removePrefix(code)
    }
}
