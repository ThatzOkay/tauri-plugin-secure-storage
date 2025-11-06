package nl.thatzokay.secureStorage

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import android.webkit.WebView
import androidx.core.content.edit
import androidx.datastore.dataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.tauri.annotation.Command
import app.tauri.annotation.InvokeArg
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.CommandData
import app.tauri.plugin.Invoke
import app.tauri.plugin.JSObject
import app.tauri.plugin.Plugin
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec


@InvokeArg
class OptionArgs {
    var prefixedKey: String? = null
    var data: String? = null
}

fun interface StorageOp {
    suspend fun run()
}

@TauriPlugin
class SecureStoragePlugin(private val activity: Activity): Plugin(activity) {

    val ANDROID_KEY_STORE = "AndroidKeyStore"
    val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    val SHARED_PREFERENCES = "WSSecureStorageSharedPreferences"
    val DATA_IV_SEPARATOR = '\u0010'
    val BASE64_FLAGS = Base64.NO_PADDING + Base64.NO_WRAP

    private val keyStore: KeyStore by lazy {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        ks
    }

    private val Context.dataStore by preferencesDataStore(name = SHARED_PREFERENCES)

    @OptIn(DelicateCoroutinesApi::class)
    @Command
    fun setItem(invoke: Invoke) {
        val args = invoke.parseArgs(OptionArgs::class.java)

        if (args.prefixedKey == null) {
            KeyStoreException.reject(invoke, KeyStoreException.ErrorKind.invalidData)
            return
        }

        if (args.data == null) {
            KeyStoreException.reject(invoke, KeyStoreException.ErrorKind.invalidData)
            return
        }

        GlobalScope.launch {
            tryStorageOp(invoke) {
                storeDataInDataStore(args.prefixedKey!!, args.data!!)
                invoke.resolve()
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Command
    fun getItem(invoke: Invoke) {
        val args = invoke.parseArgs(OptionArgs::class.java)

        args.prefixedKey?.let { Log.i("prefixedKey", it) }
        args.data?.let { Log.i("data", it) }

        if (args.prefixedKey == null) {
            KeyStoreException.reject(invoke, KeyStoreException.ErrorKind.invalidData)
            return
        }

        GlobalScope.launch {
            tryStorageOp(invoke) {
                val data = getDataFromDataStore(args.prefixedKey!!)
                val result = JSObject()
                result.put("data", data)
                invoke.resolve(result)
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Command
    fun removeItem(invoke: Invoke) {
        val args = invoke.parseArgs(OptionArgs::class.java)

        args.prefixedKey?.let { Log.i("prefixedKey", it) }

        if (args.prefixedKey == null) {
            KeyStoreException.reject(invoke, KeyStoreException.ErrorKind.invalidData)
            return
        }


        GlobalScope.launch {
            tryStorageOp(invoke) {
                val deleted = deleteDataFromDataStore(args.prefixedKey!!)
                val result = JSObject()
                result.put("deleted", true)
                invoke.resolve(result)
            }
        }
    }

    private fun getPrefs() : SharedPreferences {
        return activity.getSharedPreferences(SHARED_PREFERENCES, Context.MODE_PRIVATE);
    }

    private fun storeDataInKeyStore(prefixedKey: String, data: String) {
        // When we get here, we know that the values are not null
        getPrefs()
            .edit {
                putString(prefixedKey, encryptString(data, prefixedKey))
            };
    }

    private suspend fun storeDataInDataStore(prefixedKey: String, data: String) {
        activity.dataStore.edit { prefs ->
            prefs[stringPreferencesKey(prefixedKey)] = encryptString(data, prefixedKey)
        }
    }

    private fun getDataFromKeyStore(prefixedKey: String): String? {
        val sharedPreferences = getPrefs();

        val data: String?;

        try {
            data = sharedPreferences.getString(prefixedKey, null)
        } catch (e: ClassCastException) {
            throw KeyStoreException(KeyStoreException.ErrorKind.invalidData);
        }

        return if (data != null) {
            decryptString(data, prefixedKey);
        } else {
            null;
        }
    }

    private suspend fun getDataFromDataStore(prefixedKey: String): String? {
        val prefs = activity.dataStore.data.first()
        val data = prefs[stringPreferencesKey(prefixedKey)]

        return data?.let { decryptString(it, prefixedKey) }
    }

    private suspend fun deleteDataFromDataStore(prefixedKey: String): Boolean {
        return try {
            val key = stringPreferencesKey(prefixedKey)
            activity.dataStore.edit { prefs ->
                prefs.remove(key)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun tryStorageOp(invoke: Invoke, op: StorageOp) {

        val exception: KeyStoreException = try {
            op.run()
            return
        } catch (e: KeyStoreException) {
            e
        } catch (e: GeneralSecurityException) {
            KeyStoreException(KeyStoreException.ErrorKind.osError, e)
        } catch (e: IOException) {
            KeyStoreException(KeyStoreException.ErrorKind.osError, e)
        } catch (e: Exception) {
            KeyStoreException(KeyStoreException.ErrorKind.unknownError, e)
        }

        exception.rejectCall(invoke)
    }

    private fun encryptString(str: String, prefixedKey: String): String {
        // Code taken from https://medium.com/@josiassena/using-the-android-keystore-system-to-store-sensitive-information-3a56175a454b
        val cipher: Cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(prefixedKey))

        val iv = cipher.iv;
        val plaintext = str.toByteArray(Charsets.UTF_8)

        val encryptedBytes = cipher.doFinal(plaintext)
        val encryptedStr = Base64.encodeToString(encryptedBytes, BASE64_FLAGS);

        // Append the IV
        val ivStr = Base64.encodeToString(iv, BASE64_FLAGS)
        return (encryptedStr + DATA_IV_SEPARATOR).toString() + ivStr
    }

    private fun decryptString(cipherText: String, prefixedKey: String): String? {
        val parts = cipherText.split(DATA_IV_SEPARATOR.toString())

        if (parts.size != 2) {
            throw KeyStoreException(KeyStoreException.ErrorKind.invalidData);
        }

        // The first part is the actual data, the second is the IV
        val encryptedData = Base64.decode(parts[0], BASE64_FLAGS);
        val iv = Base64.decode(parts[1], BASE64_FLAGS);

        val secretKeyEntry = keyStore.getEntry(prefixedKey, null) as KeyStore.SecretKeyEntry?
            ?: return null

        val secretKey = secretKeyEntry.secretKey;
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        val decryptedData = cipher.doFinal(encryptedData)
        return String(decryptedData, StandardCharsets.UTF_8)
    }

    private fun getSecretKey(prefixedKey: String): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            "AES",
            ANDROID_KEY_STORE
        )

        var entry: KeyStore.SecretKeyEntry? = null

        try {
            entry = keyStore.getEntry(prefixedKey, null) as KeyStore.SecretKeyEntry?
        } catch (e: UnrecoverableKeyException) {
            // We haven't yet generated a secret key for prefixedKey, generate one
        }

        val secretKey: SecretKey

        if(entry == null) {
            val builder = KeyGenParameterSpec.Builder(
                prefixedKey,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )

            val spec = builder.setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build();

            keyGenerator.init(spec);
            secretKey = keyGenerator.generateKey();
        } else {
            secretKey = entry.secretKey
        }

        return secretKey
    }
}
