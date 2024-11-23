package com.bulifier.core.git

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.bulifier.core.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.nio.charset.Charset
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.random.Random

class SecureCredentialManager(context: Context) {

    private val logger = Logger("creds")

    private val sharedPreferences =
        context.getSharedPreferences("secure_credential_prefs", Context.MODE_PRIVATE)

    // computing the kye in run time for security reason
    private val keyAlias by lazy {
        logger.d("Generating keyAlias")
        "${Random(Math.PI.hashCode()).nextLong()}_alias"
    }
    private val charset: Charset = Charsets.UTF_8

    // Lazily initialized KeyStore instance
    private val keyStore by lazy {
        logger.d("Initializing KeyStore")
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }
    }

    // Lazily initialized key generation (only generates key if it's not already present)
    private val secretKey: SecretKey
        get() {
            logger.d("Retrieving secret key")
            if (!keyStore.containsAlias(keyAlias)) {
                logger.i("Key not found, generating new key")
                generateKey()
            } else {
                logger.d("Key found")
            }
            return keyStore.getKey(keyAlias, null) as SecretKey
        }

    // Generates a key for encryption, called only when necessary
    private fun generateKey() {
        try {
            logger.d("Generating a new key")
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
            logger.i("Key generation successful")
        } catch (e: Exception) {
            logger.e("Key generation failed: ${e.message}")
        }
    }

    // Encrypt data using the keystore key
    private fun encryptData(data: String): Pair<String, String> {
        return try {
            logger.d("Encrypting data")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryption = cipher.doFinal(data.toByteArray(charset))
            logger.i("Data encryption successful")
            Pair(
                Base64.encodeToString(iv, Base64.DEFAULT),
                Base64.encodeToString(encryption, Base64.DEFAULT)
            )
        } catch (e: Exception) {
            logger.e("Data encryption failed: ${e.message}")
            throw e
        }
    }

    // Decrypt data using the keystore key
    private fun decryptData(iv: String, encryptedData: String): String {
        return try {
            logger.d("Decrypting data")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, Base64.decode(iv, Base64.DEFAULT))
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val decodedData = cipher.doFinal(Base64.decode(encryptedData, Base64.DEFAULT))
            logger.i("Data decryption successful")
            String(decodedData, charset)
        } catch (e: Exception) {
            logger.e("Data decryption failed: ${e.message}")
            throw e
        }
    }

    // Save credentials by projectId using coroutines
    suspend fun saveCredentials(projectId: Long, username: String, passwordOrToken: String) {
        withContext(Dispatchers.IO) {
            logger.d("Saving credentials for projectId: $projectId")
            try {
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
                logger.i("Credentials saved successfully for projectId: $projectId")
            } catch (e: Exception) {
                logger.e("Failed to save credentials for projectId: $projectId, Error: ${e.message}")
            }
        }
    }

    suspend fun retrieveCredentials(projectId: Long): CredentialsProvider? =
        withContext(Dispatchers.IO) {
            logger.d("Retrieving credentials for projectId: $projectId")
            try {
                val keyUsername = "username_$projectId"
                val keyPassword = "password_$projectId"

                val keyUsernameIv = "${keyUsername}_iv"
                val keyPasswordIv = "${keyPassword}_iv"

                val usernameIv = sharedPreferences.getString(keyUsernameIv, null)
                val encryptedUsername = sharedPreferences.getString(keyUsername, null)

                val passwordIv = sharedPreferences.getString(keyPasswordIv, null)
                val encryptedPassword = sharedPreferences.getString(keyPassword, null)

                return@withContext if (usernameIv != null && encryptedUsername != null && passwordIv != null && encryptedPassword != null) {
                    logger.i("Credentials found for projectId: $projectId")
                    val username = decryptData(usernameIv, encryptedUsername)
                    val passwordOrToken = decryptData(passwordIv, encryptedPassword)
                    UsernamePasswordCredentialsProvider(username, passwordOrToken)
                } else {
                    logger.d("No credentials found for projectId: $projectId")
                    null
                }
            } catch (e: Exception) {
                logger.e("Failed to retrieve credentials for projectId: $projectId, Error: ${e.message}")
                null
            }
        }


    // Clear credentials by projectId using coroutines
    suspend fun clearCredentials(projectId: Long) {
        withContext(Dispatchers.IO) {
            logger.d("Clearing credentials for projectId: $projectId")
            try {
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
                logger.i("Credentials cleared for projectId: $projectId")
            } catch (e: Exception) {
                logger.e("Failed to clear credentials for projectId: $projectId, Error: ${e.message}")
            }
        }
    }

    // Method to check if credentials are provided for a given projectId
    suspend fun isCredentialsProvided(projectId: Long): Boolean = withContext(Dispatchers.IO) {
        logger.d("Checking if credentials are provided for projectId: $projectId")
        try {
            val keyUsername = "username_$projectId"
            val keyPassword = "password_$projectId"

            val keyUsernameIv = "${keyUsername}_iv"
            val keyPasswordIv = "${keyPassword}_iv"

            val result = sharedPreferences.contains(keyUsername) &&
                    sharedPreferences.contains(keyPassword) &&
                    sharedPreferences.contains(keyUsernameIv) &&
                    sharedPreferences.contains(keyPasswordIv)

            logger.i("Credentials presence check for projectId: $projectId - Result: $result")
            return@withContext result
        } catch (e: Exception) {
            logger.e("Error checking credentials presence for projectId: $projectId, Error: ${e.message}")
            false
        }
    }
}
