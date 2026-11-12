package com.mybalance.app

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/** Restituisce una funzione che apre il selettore di Android: scegli cartella e nome del file CSV. */
@Composable
fun rememberExporter(vm: MainViewModel): () -> Unit {
    val ctx = LocalContext.current
    val l = LocalL.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) {
            val profile = vm.profile
            var ok = false
            if (profile != null) {
                try {
                    val out = ctx.contentResolver.openOutputStream(uri, "wt")
                    if (out != null) {
                        out.use { it.write(Csv.export(profile, vm.txs, vm.funds).toByteArray(Charsets.UTF_8)) }
                        ok = true
                    }
                } catch (e: Exception) {
                    ok = false
                }
            }
            Toast.makeText(
                ctx,
                if (ok) l("Data exported", "Dati esportati") else l("Export failed", "Esportazione non riuscita"),
                Toast.LENGTH_LONG
            ).show()
        }
    }
    return { launcher.launch("MyBalance_" + LocalDate.now().toString() + ".csv") }
}

/** Restituisce una funzione che apre il selettore di file e importa un salvataggio CSV. */
@Composable
fun rememberImporter(vm: MainViewModel): () -> Unit {
    val ctx = LocalContext.current
    val l = LocalL.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            var text: String? = null
            try {
                val input = ctx.contentResolver.openInputStream(uri)
                if (input != null) text = input.use { String(it.readBytes(), Charsets.UTF_8) }
            } catch (e: Exception) {
                text = null
            }
            val parsed = if (text == null) null else Csv.parse(text)
            if (parsed == null) {
                Toast.makeText(
                    ctx,
                    l("Invalid file: not a MyBalance backup", "File non valido: non e' un salvataggio di MyBalance"),
                    Toast.LENGTH_LONG
                ).show()
            } else {
                vm.importData(parsed.first, parsed.second, parsed.third)
                Toast.makeText(ctx, l("Data imported", "Dati importati"), Toast.LENGTH_LONG).show()
            }
        }
    }
    return { launcher.launch(arrayOf("*/*")) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
    ) {
        Text(text, color = p.accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
