package com.bulifier.core.git

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.nio.charset.Charset
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.random.Random
import org.eclipse.jgit.transport.CredentialsProvider

class SecureCredentialManager(private val context: Context) {

    private val sharedPreferences =
        context.getSharedPreferences("secure_credential_prefs", Context.MODE_PRIVATE)

    // computing the kye in run time for security reason
    private val keyAlias by lazy {
        "${Random(Math.PI.hashCode()).nextLong()}_alias"
    }
    private val charset: Charset = Charsets.UTF_8

    // Lazily initialized KeyStore instance
    private val keyStore by lazy {
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }
    }

    // Lazily initialized key generation (only generates key if it's not already present)
    private val secretKey: SecretKey
        get() {
            if (!keyStore.containsAlias(keyAlias)) {
                generateKey()
            }
            return keyStore.getKey(keyAlias, null) as SecretKey
        }

    // Generates a key for encryption, called only when necessary
    private fun generateKey() {
        val keyGenerator =
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()

        keyGenerator.init(keyGenParameterSpec)
        keyGenerator.generateKey()
    }

    // Encrypt data using the keystore key
    private fun encryptData(data: String): Pair<String, String> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encryption = cipher.doFinal(data.toByteArray(charset))
        return Pair(
            Base64.encodeToString(iv, Base64.DEFAULT),
            Base64.encodeToString(encryption, Base64.DEFAULT)
        )
    }

    // Decrypt data using the keystore key
    private fun decryptData(iv: String, encryptedData: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, Base64.decode(iv, Base64.DEFAULT))
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        val decodedData = cipher.doFinal(Base64.decode(encryptedData, Base64.DEFAULT))
        return String(decodedData, charset)
    }

    // Save credentials by projectId using coroutines
    suspend fun saveCredentials(projectId: Long, username: String, passwordOrToken: String) {
        withContext(Dispatchers.IO) {
            val keyUsername = "username_$projectId"
            val keyPassword = "password_$projectId"

            val keyUsernameIv = "${keyUsername}_iv"
            val keyPasswordIv = "${keyPassword}_iv"

            // Encrypt username and password/token
            val (usernameIv, encryptedUsername) = encryptData(username)
            val (passwordIv, encryptedPassword) = encryptData(passwordOrToken)

            // Save encrypted data to SharedPreferences
            sharedPreferences.edit().apply {
                putString(keyUsernameIv, usernameIv)
                putString(keyUsername, encryptedUsername)
                putString(keyPasswordIv, passwordIv)
                putString(keyPassword, encryptedPassword)
                apply()
            }
        }
    }


    suspend fun retrieveCredentials(projectId: Long): CredentialsProvider? =
        withContext(Dispatchers.IO) {
            val keyUsername = "username_$projectId"
            val keyPassword = "password_$projectId"

            val keyUsernameIv = "${keyUsername}_iv"
            val keyPasswordIv = "${keyPassword}_iv"

            val usernameIv = sharedPreferences.getString(keyUsernameIv, null)
            val encryptedUsername = sharedPreferences.getString(keyUsername, null)

            val passwordIv = sharedPreferences.getString(keyPasswordIv, null)
            val encryptedPassword = sharedPreferences.getString(keyPassword, null)

            return@withContext if (usernameIv != null && encryptedUsername != null && passwordIv != null && encryptedPassword != null) {
                val username = decryptData(usernameIv, encryptedUsername)
                val passwordOrToken = decryptData(passwordIv, encryptedPassword)
                UsernamePasswordCredentialsProvider(username, passwordOrToken)
            } else {
                null
            }
        }


    // Clear credentials by projectId using coroutines
    suspend fun clearCredentials(projectId: Long) {
        withContext(Dispatchers.IO) {
            val keyUsername = "username_$projectId"
            val keyPassword = "password_$projectId"

            val keyUsernameIv = "${keyUsername}_iv"
            val keyPasswordIv = "${keyPassword}_iv"

            sharedPreferences.edit().apply {
                remove(keyUsernameIv)
                remove(keyUsername)
                remove(keyPasswordIv)
                remove(keyPassword)
                apply()
            }
        }
    }

    // Method to check if credentials are provided for a given projectId
    suspend fun isCredentialsProvided(projectId: Long): Boolean = withContext(Dispatchers.IO) {
        val keyUsername = "username_$projectId"
        val keyPassword = "password_$projectId"

        val keyUsernameIv = "${keyUsername}_iv"
        val keyPasswordIv = "${keyPassword}_iv"

        // Check if both username and password (and their IVs) exist in SharedPreferences
        return@withContext sharedPreferences.contains(keyUsername) &&
                sharedPreferences.contains(keyPassword) &&
                sharedPreferences.contains(keyUsernameIv) &&
                sharedPreferences.contains(keyPasswordIv)
    }

}
