package com.mybalance.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object Security {
    private val random = SecureRandom()

    fun newSalt(): ByteArray {
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return salt
    }

    /** La password non viene mai salvata: solo hash PBKDF2 con sale casuale. */
    fun hash(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 100_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    fun same(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    fun toB64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun fromB64(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)

    fun newOtp(): String = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000))
}

/**
 * Consegna del codice OTP all'utente.
 *
 * Questa versione dell'app non ha un server, quindi non puo' spedire e-mail da sola:
 * apre l'app di posta del telefono con il messaggio gia' compilato verso l'indirizzo
 * registrato. Per un vero invio automatico basta fornire un'altra implementazione
 * di [OtpSender] (ad esempio una chiamata a un tuo servizio e-mail) e usarla in
 * ForgotScreen al posto di [MailAppOtpSender].
 */
interface OtpSender {
    fun send(context: Context, email: String, code: String, italian: Boolean): Boolean
}

object MailAppOtpSender : OtpSender {
    override fun send(context: Context, email: String, code: String, italian: Boolean): Boolean {
        val subject = if (italian) "MyBalance - codice di verifica" else "MyBalance - verification code"
        val body = if (italian) {
            "Il tuo codice per reimpostare la password di MyBalance è: $code\n\nIl codice vale 10 minuti."
        } else {
            "Your code to reset your MyBalance password is: $code\n\nThe code is valid for 10 minutes."
        }
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
