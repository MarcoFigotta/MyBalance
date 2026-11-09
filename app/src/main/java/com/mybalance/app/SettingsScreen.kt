package com.mybalance.app

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class Dialog { NONE, NAME, PASSWORD, LOGOUT, DELETE1, DELETE2, DELETE3 }

/** Pagina di destra: impostazioni account. */
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val l = LocalL.current
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val acc = vm.account ?: return

    var dialog by remember { mutableStateOf(Dialog.NONE) }
    var nameInput by remember { mutableStateOf("") }
    var oldPass by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }

    fun close() {
        dialog = Dialog.NONE
        formError = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(l("Account", "Account"), color = p.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)

        // intestazione con iniziale, nome ed e-mail
        SurfaceCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(p.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        acc.username.take(1).uppercase(),
                        color = p.onAccent, fontSize = 22.sp, fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(acc.username, color = p.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(acc.email, color = p.muted, fontSize = 14.sp)
                }
            }
        }

        // aspetto
        SurfaceCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(Ic.THEME, p.muted)
                Spacer(Modifier.width(14.dp))
                Text(l("Theme", "Tema"), color = p.text, fontSize = 16.sp)
            }
            Spacer(Modifier.height(10.dp))
            ChipFlow {
                Chip(l("Light", "Chiaro"), vm.themeMode == ThemeMode.LIGHT, { vm.setTheme(ThemeMode.LIGHT) })
                Chip(l("Dark", "Scuro"), vm.themeMode == ThemeMode.DARK, { vm.setTheme(ThemeMode.DARK) })
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(Ic.GLOBE, p.muted)
                Spacer(Modifier.width(14.dp))
                Text(l("Language", "Lingua"), color = p.text, fontSize = 16.sp)
            }
            Spacer(Modifier.height(10.dp))
            ChipFlow {
                Chip(l("System", "Sistema"), vm.langMode == LangMode.SYSTEM, { vm.setLang(LangMode.SYSTEM) })
                Chip("Italiano", vm.langMode == LangMode.IT, { vm.setLang(LangMode.IT) })
                Chip("English", vm.langMode == LangMode.EN, { vm.setLang(LangMode.EN) })
            }
        }

        // credenziali
        SurfaceCard {
            SettingRow(Ic.PENCIL, l("Change name", "Cambia nome")) {
                nameInput = acc.username
                dialog = Dialog.NAME
            }
            SettingRow(Ic.LOCK, l("Change password", "Cambia password")) {
                oldPass = ""
                newPass = ""
                dialog = Dialog.PASSWORD
            }
        }

        // sessione
        SurfaceCard {
            SettingRow(Ic.LOGOUT, l("Log out", "Logout")) { dialog = Dialog.LOGOUT }
            SettingRow(Ic.TRASH, l("Delete account", "Cancella account"), danger = true) {
                dialog = Dialog.DELETE1
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    when (dialog) {
        Dialog.NONE -> {}

        Dialog.NAME -> FormDialog(
            title = l("Change name", "Cambia nome"),
            confirm = l("Save", "Salva"),
            enabled = validateName(nameInput, l) == null,
            onConfirm = {
                vm.changeName(nameInput)
                Toast.makeText(ctx, l("Name updated", "Nome aggiornato"), Toast.LENGTH_SHORT).show()
                close()
            },
            onDismiss = { close() }
        ) {
            AppField(
                nameInput, { nameInput = it }, l("Username", "Nome utente"),
                imeAction = ImeAction.Done, error = validateName(nameInput, l)
            )
        }

        Dialog.PASSWORD -> FormDialog(
            title = l("Change password", "Cambia password"),
            confirm = l("Save", "Salva"),
            enabled = oldPass.isNotEmpty() && validatePassword(newPass, l) == null,
            onConfirm = {
                if (vm.changePassword(oldPass, newPass)) {
                    Toast.makeText(ctx, l("Password updated", "Password aggiornata"), Toast.LENGTH_SHORT).show()
                    close()
                } else {
                    formError = l("Current password is incorrect", "La password attuale non è corretta")
                }
            },
            onDismiss = { close() }
        ) {
            AppField(
                oldPass, { oldPass = it; formError = null }, l("Current password", "Password attuale"),
                password = true, error = formError
            )
            AppField(
                newPass, { newPass = it }, l("New password", "Nuova password"),
                password = true, imeAction = ImeAction.Done,
                error = if (newPass.isNotEmpty()) validatePassword(newPass, l) else null
            )
        }

        Dialog.LOGOUT -> ConfirmDialog(
            title = l("Log out?", "Uscire dall'account?"),
            message = l(
                "You will need username, e-mail and password to sign in again.",
                "Per rientrare ti serviranno nome utente, e-mail e password."
            ),
            confirm = l("Log out", "Esci"),
            onConfirm = {
                close()
                vm.logout()
            },
            onDismiss = { close() }
        )

        Dialog.DELETE1 -> ConfirmDialog(
            title = l("Delete account? (1/3)", "Cancellare l'account? (1/3)"),
            message = l(
                "Your account and all your data on this phone will be deleted.",
                "Il tuo account e tutti i dati su questo telefono verranno cancellati."
            ),
            confirm = l("Continue", "Continua"),
            danger = true,
            onConfirm = { dialog = Dialog.DELETE2 },
            onDismiss = { close() }
        )

        Dialog.DELETE2 -> ConfirmDialog(
            title = l("Are you sure? (2/3)", "Sei sicuro? (2/3)"),
            message = l(
                "Balance, goal and every income and expense will be lost.",
                "Bilancio, obiettivo e tutte le entrate e uscite andranno persi."
            ),
            confirm = l("Yes, continue", "Sì, continua"),
            danger = true,
            onConfirm = { dialog = Dialog.DELETE3 },
            onDismiss = { close() }
        )

        Dialog.DELETE3 -> ConfirmDialog(
            title = l("Last confirmation (3/3)", "Ultima conferma (3/3)"),
            message = l(
                "This cannot be undone. Delete the account permanently?",
                "L'operazione non si può annullare. Cancellare definitivamente l'account?"
            ),
            confirm = l("Delete forever", "Cancella per sempre"),
            danger = true,
            onConfirm = {
                close()
                vm.deleteAccount()
            },
            onDismiss = { close() }
        )
    }
}
