package com.mybalance.app

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { AppRoot(vm) }
    }
}

@Composable
fun AppRoot(vm: MainViewModel) {
    val systemLang = LocalConfiguration.current.locales[0].language
    val italian = when (vm.langMode) {
        LangMode.IT -> true
        LangMode.EN -> false
        LangMode.SYSTEM -> systemLang == "it"
    }
    val dark = vm.themeMode == ThemeMode.DARK
    val l = remember(italian) { L(italian) }
    val locale = if (italian) Locale.ITALY else Locale.US

    // colore delle icone nelle barre di sistema in base al tema scelto
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(LocalL provides l, LocalLocale provides locale) {
        MyBalanceTheme(dark) {
            val p = LocalPalette.current
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(p.bg)
                    .safeDrawingPadding()
            ) {
                Router(vm)
            }
        }
    }
}

@Composable
private fun Router(vm: MainViewModel) {
    when {
        !vm.loggedIn -> AuthHost(vm)
        vm.profile == null -> OnboardingFlow(vm)
        else -> MainShell(vm)
    }
}

/** Schede: 0 Entrata/Uscita, 1 Cronologia, 2 Bilancio (centro), 3 Fondo cassa, 4 Account. */
@Composable
private fun MainShell(vm: MainViewModel) {
    var tab by rememberSaveable { mutableStateOf(2) }
    var pendingEdit by remember { mutableStateOf<Tx?>(null) }
    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier
            .weight(1f)
            .fillMaxWidth()) {
            when (tab) {
                0 -> TransactionsScreen(vm, pendingEdit, onEditHandled = { pendingEdit = null })
                1 -> HistoryScreen(vm, onEdit = {
                    pendingEdit = it
                    tab = 0
                })
                2 -> HomeScreen(vm)
                3 -> FundsScreen(vm)
                else -> SettingsScreen(vm)
            }
        }
        if (!keyboardOpen) {
            NavBar(selected = tab, onSelect = { tab = it })
        }
    }
}

@Composable
private fun NavBar(selected: Int, onSelect: (Int) -> Unit) {
    val l = LocalL.current
    val p = LocalPalette.current
    Box(modifier = Modifier
        .fillMaxWidth()
        .padding(start = 12.dp, end = 12.dp, bottom = 8.dp, top = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(p.bar)
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val m = { sel: Boolean -> Modifier.weight(if (sel) 2.2f else 1f) }
            NavItem(Ic.SWAP, l("Income/Expense", "Entrata/Uscita"), selected == 0, false, m(selected == 0)) { onSelect(0) }
            NavItem(Ic.HISTORY, l("History", "Cronologia"), selected == 1, false, m(selected == 1)) { onSelect(1) }
            NavItem(Ic.GAUGE, l("Balance", "Bilancio"), selected == 2, true, m(selected == 2)) { onSelect(2) }
            NavItem(Ic.WALLET, l("Cash funds", "Fondo cassa"), selected == 3, false, m(selected == 3)) { onSelect(3) }
            NavItem(Ic.USER, l("Account", "Account"), selected == 4, false, m(selected == 4)) { onSelect(4) }
        }
    }
}

@Composable
private fun NavItem(
    icon: Ic,
    label: String,
    selected: Boolean,
    big: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val p = LocalPalette.current
    val tint = if (selected) BarAccent else p.onBar.copy(alpha = 0.65f)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) Color.White.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIcon(icon, tint, iconSize = if (big) 28.dp else 24.dp)
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = if (selected) tint else Color.Transparent,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
