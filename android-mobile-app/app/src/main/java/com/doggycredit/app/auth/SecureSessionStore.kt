package com.doggycredit.app.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.doggycredit.app.model.ApiJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable data class SessionTokens(val accessToken: String, val refreshToken: String?, val idToken: String?, val expiresAt: Long)
interface SessionStorage {
    fun read(): SessionTokens?
    fun write(tokens: SessionTokens)
    fun clear()
}

/** AES-GCM con clave no exportable del Android Keystore y archivo excluido de backups. */
class SecureSessionStore(context: Context) : SessionStorage {
    private val file = AtomicFile(File(context.noBackupFilesDir, "session.enc"))
    private val alias = "doggycredit.session.v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized override fun read(): SessionTokens? = runCatching {
        val bytes = file.readFully()
        require(bytes.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        ApiJson.decodeFromString<SessionTokens>(cipher.doFinal(bytes.copyOfRange(12, bytes.size)).decodeToString())
    }.getOrElse { clear(); null }
    @Synchronized override fun write(tokens: SessionTokens) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(ApiJson.encodeToString(tokens).encodeToByteArray())
        val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output) }
        catch (error: Exception) { file.failWrite(output); throw error }
    }
    @Synchronized override fun clear() { file.delete() }
}
