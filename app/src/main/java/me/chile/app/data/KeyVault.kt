package me.chile.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeyVault(context: Context) {
    private val prefs=context.getSharedPreferences("model_secret",Context.MODE_PRIVATE)
    private val alias="chile.model.key"
    private fun key(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias,null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun hasKey()=prefs.contains("cipher")
    fun rollbackOnFailure(block: ()->Unit) {
        val previousCipher=prefs.getString("cipher",null)
        val previousIv=prefs.getString("iv",null)
        try {block()} catch(e: Exception) {
            check(prefs.edit().putString("cipher",previousCipher).putString("iv",previousIv).commit()) {"配置保存失败，密钥恢复失败，请重新填写 API Key"}
            throw e
        }
    }
    fun save(value: String) {
        require(value.length in 1..4096 && value.all { it.code in 33..126 }) { "API Key 格式无效，请检查是否包含空白或特殊字符" }
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.ENCRYPT_MODE,key())}
        val encoded=Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)),Base64.NO_WRAP)
        check(prefs.edit().putString("cipher",encoded).putString("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP)).commit())
    }
    fun read(): String? {
        val value=prefs.getString("cipher",null)?:return null
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(prefs.getString("iv",null),Base64.NO_WRAP))) }
        return String(cipher.doFinal(Base64.decode(value,Base64.NO_WRAP)),Charsets.UTF_8)
    }
    fun clear() { check(prefs.edit().clear().commit()) }
}
