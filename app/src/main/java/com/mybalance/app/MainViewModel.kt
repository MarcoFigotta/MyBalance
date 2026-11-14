package com.mybalance.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.time.LocalDate

enum class LoginResult { OK, WRONG, LOCKED }
enum class ResetResult { OK, WRONG, EXPIRED, TOO_MANY }

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)

    var account by mutableStateOf<Account?>(store.loadAccount())
        private set
    var loggedIn by mutableStateOf(account != null && store.loggedIn)
        private set
    var profile by mutableStateOf<Profile?>(store.loadProfile())
        private set
    var txs by mutableStateOf<List<Tx>>(store.loadTxs())
        private set
    var funds by mutableStateOf<List<Fund>>(store.loadFunds())
        private set
    var themeMode by mutableStateOf(store.theme)
        private set
    var langMode by mutableStateOf(store.lang)
        private set

    private var failedLogins = 0
    private var lockedUntil = 0L

    private var otp: String? = null
    private var otpExpiry = 0L
    private var otpTries = 0

    // ----- Account -----

    private fun passwordMatches(acc: Account, password: String): Boolean {
        val salt = Security.fromB64(acc.salt)
        return Security.same(Security.hash(password, salt), Security.fromB64(acc.hash))
    }

    private fun withPassword(acc: Account, password: String): Account {
        val salt = Security.newSalt()
        return acc.copy(salt = Security.toB64(salt), hash = Security.toB64(Security.hash(password, salt)))
    }

    fun register(username: String, email: String, password: String) {
        store.clearUserData()
        val base = Account(username.trim(), email.trim(), "", "")
        val acc = withPassword(base, password)
        store.saveAccount(acc)
        store.loggedIn = true
        account = acc
        profile = null
        txs = emptyList()
        funds = emptyList()
        loggedIn = true
    }

    fun login(username: String, email: String, password: String): LoginResult {
        val acc = account ?: return LoginResult.WRONG
        val now = System.currentTimeMillis()
        if (now < lockedUntil) return LoginResult.LOCKED
        val passwordOk = passwordMatches(acc, password)
        val ok = passwordOk &&
            acc.username.equals(username.trim(), ignoreCase = true) &&
            acc.email.equals(email.trim(), ignoreCase = true)
        if (ok) {
            failedLogins = 0
            store.loggedIn = true
            loggedIn = true
            return LoginResult.OK
        }
        failedLogins++
        if (failedLogins >= 5) {
            failedLogins = 0
            lockedUntil = now + 30_000L
            return LoginResult.LOCKED
        }
        return LoginResult.WRONG
    }

    fun logout() {
        store.loggedIn = false
        loggedIn = false
    }

    fun changeName(newName: String) {
        val acc = account ?: return
        val updated = acc.copy(username = newName.trim())
        store.saveAccount(updated)
        account = updated
    }

    fun changePassword(oldPassword: String, newPassword: String): Boolean {
        val acc = account ?: return false
        if (!passwordMatches(acc, oldPassword)) return false
        val updated = withPassword(acc, newPassword)
        store.saveAccount(updated)
        account = updated
        return true
    }

    fun deleteAccount() {
        store.clearUserData()
        account = null
        profile = null
        txs = emptyList()
        funds = emptyList()
        loggedIn = false
    }

    // ----- Recupero password con OTP -----

    /** Se l'e-mail corrisponde all'account genera un codice a 6 cifre e lo restituisce. */
    fun startRecovery(email: String): String? {
        val acc = account ?: return null
        if (!acc.email.equals(email.trim(), ignoreCase = true)) return null
        val code = Security.newOtp()
        otp = code
        otpExpiry = System.currentTimeMillis() + 10 * 60_000L
        otpTries = 0
        return code
    }

    fun resetPassword(code: String, newPassword: String): ResetResult {
        val acc = account ?: return ResetResult.WRONG
        val saved = otp ?: return ResetResult.EXPIRED
        if (System.currentTimeMillis() > otpExpiry) {
            otp = null
            return ResetResult.EXPIRED
        }
        if (!Security.same(code.trim().toByteArray(), saved.toByteArray())) {
            otpTries++
            if (otpTries >= 5) {
                otp = null
                return ResetResult.TOO_MANY
            }
            return ResetResult.WRONG
        }
        otp = null
        val updated = withPassword(acc, newPassword)
        store.saveAccount(updated)
        account = updated
        return ResetResult.OK
    }

    // ----- Obiettivo e movimenti -----

    fun completeOnboarding(p: Profile) {
        store.saveProfile(p)
        profile = p
    }

    fun addTx(
        type: TxType,
        name: String,
        cents: Long,
        period: Period,
        tag: Tag,
        rule: Recurrence? = null,
        fundId: Long? = null,
        splits: List<FundShare> = emptyList()
    ) {
        val lastId = txs.maxOfOrNull { it.id } ?: 0L
        val id = maxOf(System.currentTimeMillis(), lastId + 1)
        val tx = Tx(id, type, name, cents, period, tag, LocalDate.now().toEpochDay(), rule, emptyList(), fundId, splits)
        val updated = txs + tx
        store.saveTxs(updated)
        txs = updated
    }

    /** Modifica un movimento esistente (mantiene id e giorno di inizio). */
    fun updateTx(
        id: Long,
        type: TxType,
        name: String,
        cents: Long,
        period: Period,
        tag: Tag,
        rule: Recurrence?,
        fundId: Long? = null,
        splits: List<FundShare> = emptyList()
    ) {
        val updated = txs.map {
            if (it.id == id) {
                it.copy(type = type, name = name, cents = cents, period = period, tag = tag, rule = rule, fundId = fundId, splits = splits)
            } else {
                it
            }
        }
        store.saveTxs(updated)
        txs = updated
    }

    /** Sostituisce obiettivo, soldi iniziali e movimenti con quelli di un salvataggio. */
    fun importData(p: Profile, list: List<Tx>, newFunds: List<Fund> = emptyList()) {
        val seen = HashSet<Long>()
        var next = System.currentTimeMillis()
        val fixed = list.map {
            if (seen.add(it.id)) {
                it
            } else {
                next++
                seen.add(next)
                it.copy(id = next)
            }
        }
        store.saveProfile(p)
        store.saveTxs(fixed)
        store.saveFunds(newFunds)
        profile = p
        txs = fixed
        funds = newFunds
    }

    fun deleteTx(id: Long) {
        val updated = txs.filter { it.id != id }
        store.saveTxs(updated)
        txs = updated
    }

    // ----- Fondi -----

    fun addFund(name: String, icon: Int, limited: Boolean, cents: Long) {
        val lastId = funds.maxOfOrNull { it.id } ?: 0L
        val id = maxOf(System.currentTimeMillis(), lastId + 1)
        val updated = funds + Fund(id, name, icon, limited, cents)
        store.saveFunds(updated)
        funds = updated
    }

    fun updateFund(f: Fund) {
        val updated = funds.map { if (it.id == f.id) f else it }
        store.saveFunds(updated)
        funds = updated
    }

    fun deleteFund(id: Long) {
        val newFunds = funds.filter { it.id != id }
        val newTxs = txs.map { it.copy(fundId = if (it.fundId == id) null else it.fundId, splits = it.splits.filter { s -> s.fundId != id }) }
        store.saveFunds(newFunds)
        store.saveTxs(newTxs)
        funds = newFunds
        txs = newTxs
    }

    // ----- Ripetizioni annullate (dalla cronologia) -----

    fun skipOccurrence(id: Long, day: Long) {
        val updated = txs.map { if (it.id == id) it.copy(skipped = (it.skipped + day).distinct()) else it }
        store.saveTxs(updated)
        txs = updated
    }

    fun restoreOccurrence(id: Long, day: Long) {
        val updated = txs.map { if (it.id == id) it.copy(skipped = it.skipped.filter { d -> d != day }) else it }
        store.saveTxs(updated)
        txs = updated
    }

    // ----- Preferenze -----

    fun setTheme(mode: ThemeMode) {
        store.theme = mode
        themeMode = mode
    }

    fun setLang(mode: LangMode) {
        store.lang = mode
        langMode = mode
    }
}
