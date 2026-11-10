package com.mybalance.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun AuthScaffold(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    val p = LocalPalette.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(Ic.GAUGE, p.accent, iconSize = 30.dp)
            Spacer(Modifier.width(10.dp))
            Text("MyBalance", color = p.accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, color = p.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = p.muted, fontSize = 15.sp, lineHeight = 21.sp)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

// ---------------------------------------------------------------------------
// Primo avvio: benvenuto e creazione account
// ---------------------------------------------------------------------------

@Composable
fun RegisterScreen(vm: MainViewModel) {
    val l = LocalL.current
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var tried by rememberSaveable { mutableStateOf(false) }
    var confirmNew by remember { mutableStateOf(false) }

    val nameErr = if (tried) validateName(name, l) else null
    val emailErr = if (tried) validateEmail(email, l) else null
    val passErr = if (tried) validatePassword(pass, l) else null

    AuthScaffold(
        title = l("Welcome", "Benvenuto"),
        subtitle = l(
            "Create your personal wallet. You will always need your username, e-mail and password to sign in.",
            "Crea il tuo portafoglio personale. Per accedere ti serviranno sempre nome utente, e-mail e password."
        )
    ) {
        AppField(name, { name = it }, l("Username", "Nome utente"), error = nameErr)
        AppField(
            email, { email = it }, l("E-mail", "E-mail"),
            keyboardType = KeyboardType.Email, error = emailErr
        )
        AppField(
            pass, { pass = it }, l("Password", "Password"),
            password = true, error = passErr, imeAction = ImeAction.Done
        )
        Spacer(Modifier.height(8.dp))
        if (confirmNew) {
            ConfirmDialog(
                title = l("Replace existing account?", "Sostituire l'account esistente?"),
                message = l(
                    "An account already exists on this phone. Creating a new one deletes it and all its data.",
                    "Esiste già un account su questo telefono. Crearne uno nuovo cancella quello attuale e tutti i suoi dati."
                ),
                confirm = l("Create new", "Crea nuovo"),
                danger = true,
                onConfirm = {
                    confirmNew = false
                    vm.register(name, email, pass)
                },
                onDismiss = { confirmNew = false }
            )
        }
        PrimaryButton(l("Create account", "Crea account")) {
            tried = true
            if (validateName(name, l) == null && validateEmail(email, l) == null &&
                validatePassword(pass, l) == null
            ) {
                if (vm.account != null) confirmNew = true else vm.register(name, email, pass)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Accesso (sempre con nome utente + e-mail + password)
// ---------------------------------------------------------------------------

@Composable
fun LoginScreen(vm: MainViewModel, onForgot: () -> Unit) {
    val l = LocalL.current
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AuthScaffold(
        title = l("Log in", "Accedi"),
        subtitle = l(
            "Enter your username, e-mail and password to open your wallet.",
            "Inserisci nome utente, e-mail e password per aprire il tuo portafoglio."
        )
    ) {
        AppField(name, { name = it }, l("Username", "Nome utente"))
        AppField(email, { email = it }, l("E-mail", "E-mail"), keyboardType = KeyboardType.Email)
        AppField(
            pass, { pass = it }, l("Password", "Password"),
            password = true, imeAction = ImeAction.Done,
            error = error
        )
        Spacer(Modifier.height(4.dp))
        PrimaryButton(l("Log in", "Accedi"), enabled = name.isNotBlank() && email.isNotBlank() && pass.isNotEmpty()) {
            when (vm.login(name, email, pass)) {
                LoginResult.OK -> error = null
                LoginResult.WRONG -> error = l(
                    "Incorrect credentials. Check username, e-mail and password.",
                    "Credenziali non corrette. Controlla nome utente, e-mail e password."
                )
                LoginResult.LOCKED -> error = l(
                    "Too many attempts. Try again in 30 seconds.",
                    "Troppi tentativi. Riprova tra 30 secondi."
                )
            }
        }
        LinkButton(l("Forgot password?", "Password dimenticata?"), onForgot)
    }
}

// ---------------------------------------------------------------------------
// Recupero password con codice OTP via e-mail
// ---------------------------------------------------------------------------

@Composable
fun ForgotScreen(vm: MainViewModel, onDone: () -> Unit) {
    val l = LocalL.current
    val ctx = LocalContext.current
    var step by rememberSaveable { mutableStateOf(0) }
    var email by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var info by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var tried by rememberSaveable { mutableStateOf(false) }

    BackHandler { if (step > 0) step = 0 else onDone() }

    fun sendCode(): Boolean {
        val generated = vm.startRecovery(email)
        if (generated == null) {
            error = l("No account found with this e-mail.", "Nessun account con questa e-mail.")
            return false
        }
        val sent = MailAppOtpSender.send(ctx, email.trim(), generated, l.italian)
        if (!sent) {
            error = l("No e-mail app found on this phone.", "Nessuna app di posta trovata su questo telefono.")
            return false
        }
        error = null
        info = l(
            "We prepared an e-mail with your 6-digit code. Send it, open it in your inbox and enter the code below. It is valid for 10 minutes.",
            "Abbiamo preparato un'e-mail con il tuo codice di 6 cifre. Inviala, aprila nella tua casella e inserisci qui il codice. Vale 10 minuti."
        )
        return true
    }

    AuthScaffold(
        title = l("Reset password", "Recupera password"),
        subtitle = if (step == 0) {
            l(
                "Enter the e-mail of your account. We will send you a one-time code.",
                "Inserisci l'e-mail del tuo account. Ti invieremo un codice monouso."
            )
        } else {
            l("Enter the code and choose a new password.", "Inserisci il codice e scegli una nuova password.")
        }
    ) {
        if (step == 0) {
            AppField(
                email, { email = it }, l("E-mail", "E-mail"),
                keyboardType = KeyboardType.Email, imeAction = ImeAction.Done, error = error
            )
            Spacer(Modifier.height(4.dp))
            PrimaryButton(l("Send code", "Invia codice"), enabled = email.isNotBlank()) {
                if (sendCode()) {
                    step = 1
                    code = ""
                    pass = ""
                    tried = false
                }
            }
        } else {
            val p = LocalPalette.current
            val shownInfo = info
            if (shownInfo != null) {
                Text(shownInfo, color = p.muted, fontSize = 14.sp, lineHeight = 20.sp)
            }
            AppField(
                code, { code = it.filter { ch -> ch in '0'..'9' }.take(6) },
                l("6-digit code", "Codice a 6 cifre"),
                keyboardType = KeyboardType.Number
            )
            AppField(
                pass, { pass = it }, l("New password", "Nuova password"),
                password = true, imeAction = ImeAction.Done,
                error = if (tried) validatePassword(pass, l) else error
            )
            Spacer(Modifier.height(4.dp))
            PrimaryButton(l("Reset password", "Reimposta password"), enabled = code.length == 6) {
                tried = true
                if (validatePassword(pass, l) == null) {
                    when (vm.resetPassword(code, pass)) {
                        ResetResult.OK -> onDone()
                        ResetResult.WRONG -> error = l("Wrong code.", "Codice errato.")
                        ResetResult.EXPIRED -> error = l(
                            "The code has expired. Request a new one.",
                            "Il codice è scaduto. Richiedine uno nuovo."
                        )
                        ResetResult.TOO_MANY -> error = l(
                            "Too many wrong attempts. Request a new code.",
                            "Troppi tentativi errati. Richiedi un nuovo codice."
                        )
                    }
                }
            }
            LinkButton(l("Send a new code", "Invia un nuovo codice")) { sendCode() }
        }
        Spacer(Modifier.height(4.dp))
        LinkButton(l("Back to sign in", "Torna all'accesso"), onDone)
    }
}

// ---------------------------------------------------------------------------
// Accesso: "Accedi" (account esistente) e "Registrati" (nuovo account) separati
// ---------------------------------------------------------------------------

@Composable
fun AuthHost(vm: MainViewModel) {
    val l = LocalL.current
    val p = LocalPalette.current
    var tab by rememberSaveable { mutableStateOf(if (vm.account == null) 1 else 0) }
    var forgot by rememberSaveable { mutableStateOf(false) }
    if (forgot && vm.account != null) {
        ForgotScreen(vm, onDone = { forgot = false })
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Chip(l("Log in", "Accedi"), tab == 0, { tab = 0 })
            Chip(l("Sign up", "Registrati"), tab == 1, { tab = 1 })
        }
        Column(modifier = Modifier.weight(1f)) {
            if (tab == 0 && vm.account == null) {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                    Text(
                        l("There is no account on this phone yet.", "Non c'è ancora nessun account su questo telefono."),
                        color = p.muted, fontSize = 15.sp
                    )
                    LinkButton(l("Sign up", "Registrati")) { tab = 1 }
                }
            } else if (tab == 0) {
                LoginScreen(vm, onForgot = { forgot = true })
            } else {
                RegisterScreen(vm)
            }
        }
    }
}
