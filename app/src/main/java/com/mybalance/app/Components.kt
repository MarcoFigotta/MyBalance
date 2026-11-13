package com.mybalance.app

import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

// ---------------------------------------------------------------------------
// Lingua: l("English", "Italiano")
// ---------------------------------------------------------------------------

class L(val italian: Boolean) {
    operator fun invoke(en: String, itText: String): String = if (italian) itText else en
}

val LocalL = staticCompositionLocalOf { L(false) }
val LocalLocale = staticCompositionLocalOf { Locale.US }

@Composable
fun money(cents: Long, currency: String, alwaysDecimals: Boolean = true): String =
    formatMoney(cents, currency, LocalLocale.current, alwaysDecimals)

fun Period.label(l: L): String = when (this) {
    Period.DAILY -> l("Daily", "Giornaliera")
    Period.MONTHLY -> l("Monthly", "Mensile")
    Period.WEEKLY -> l("Weekly", "Settimanale")
    Period.ONCE -> l("One-time", "Unica")
    Period.CUSTOM -> l("Custom", "Personalizzata")
}

fun Tag.label(l: L): String = when (this) {
    Tag.SCHOOL -> l("School", "Scuola")
    Tag.WORK -> l("Work", "Lavoro")
    Tag.HEALTH -> l("Health", "Salute")
    Tag.FOOD -> l("Food", "Cibo")
    Tag.LEISURE -> l("Leisure", "Svago")
    Tag.OTHER -> l("Other", "Altro")
}

// ---------------------------------------------------------------------------
// Validazione
// ---------------------------------------------------------------------------

fun validateName(v: String, l: L): String? = when {
    v.trim().length < 2 -> l("At least 2 characters", "Almeno 2 caratteri")
    v.trim().length > 30 -> l("At most 30 characters", "Massimo 30 caratteri")
    else -> null
}

fun validateEmail(v: String, l: L): String? =
    if (android.util.Patterns.EMAIL_ADDRESS.matcher(v.trim()).matches()) null
    else l("Enter a valid e-mail", "Inserisci un'e-mail valida")

fun validatePassword(v: String, l: L): String? =
    if (v.length >= 8) null else l("At least 8 characters", "Almeno 8 caratteri")

// ---------------------------------------------------------------------------
// Componenti
// ---------------------------------------------------------------------------

@Composable
fun AppField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    error: String? = null,
    suffix: String? = null,
    imeAction: ImeAction = ImeAction.Next
) {
    val p = LocalPalette.current
    val l = LocalL.current
    var visible by remember { mutableStateOf(false) }
    val trailing: (@Composable () -> Unit)? = if (password) {
        {
            Text(
                text = if (visible) l("Hide", "Nascondi") else l("Show", "Mostra"),
                color = p.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { visible = !visible }
                    .padding(8.dp)
            )
        }
    } else null
    val suffixSlot: (@Composable () -> Unit)? = if (suffix != null) {
        { Text(suffix) }
    } else null
    val supporting: (@Composable () -> Unit)? = if (error != null) {
        { Text(error, color = p.negative) }
    } else null

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else keyboardType,
            imeAction = imeAction
        ),
        visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = trailing,
        suffix = suffixSlot,
        supportingText = supporting,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = p.accent,
            focusedLabelColor = p.accent,
            cursorColor = p.accent,
            unfocusedBorderColor = p.outline,
            errorBorderColor = p.negative
        )
    )
}

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val p = LocalPalette.current
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = p.accent,
            contentColor = p.onAccent,
            disabledContainerColor = p.track,
            disabledContentColor = p.muted
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun LinkButton(text: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    TextButton(onClick = onClick) {
        Text(text, color = p.accent, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) p.accent else p.surface)
            .border(1.dp, if (selected) p.accent else p.outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) p.onAccent else p.text,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        content()
    }
}

@Composable
fun SectionLabel(text: String) {
    val p = LocalPalette.current
    Text(
        text = text,
        color = p.muted,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
fun SurfaceCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(p.surface)
            .padding(16.dp),
        content = content
    )
}

@Composable
fun CurrencyPicker(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { open = true },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.height(56.dp)
        ) {
            Text(value, color = p.text, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(6.dp))
            AppIcon(Ic.CHEVRON_DOWN, p.muted, iconSize = 18.dp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (c in CURRENCIES) {
                DropdownMenuItem(
                    text = { Text("$c (${currencySymbol(c)})") },
                    onClick = {
                        onChange(c)
                        open = false
                    }
                )
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirm: String,
    danger: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val p = LocalPalette.current
    val l = LocalL.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirm,
                    color = if (danger) p.negative else p.accent,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(l("Cancel", "Annulla"), color = p.muted)
            }
        }
    )
}

@Composable
fun FormDialog(
    title: String,
    confirm: String,
    enabled: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val p = LocalPalette.current
    val l = LocalL.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = enabled) {
                Text(
                    confirm,
                    color = if (enabled) p.accent else p.muted,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(l("Cancel", "Annulla"), color = p.muted)
            }
        }
    )
}

/** Riga di una lista di impostazioni: icona, titolo, valore opzionale e freccia. */
@Composable
fun SettingRow(
    icon: Ic,
    title: String,
    value: String? = null,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val p = LocalPalette.current
    val color = if (danger) p.negative else p.text
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(icon, if (danger) p.negative else p.muted)
        Spacer(Modifier.width(14.dp))
        Text(title, color = color, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value, color = p.muted, fontSize = 14.sp)
            Spacer(Modifier.width(6.dp))
        }
        if (!danger) AppIcon(Ic.CHEVRON_RIGHT, p.muted, iconSize = 18.dp)
    }
}


/** Ingresso morbido: dissolvenza + piccola salita, con ritardo crescente per indice. */
@Composable
fun Modifier.appear(index: Int = 0): Modifier {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index.coerceIn(0, 8) * 50L)
        a.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
    }
    return this.graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * 24.dp.toPx()
    }
}
