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
    var forgot by rememberSaveable { mutableStateOf(false) }
    val account = vm.account
    val profile = vm.profile
    when {
        account == null -> RegisterScreen(vm)
        !vm.loggedIn -> {
            if (forgot) {
                ForgotScreen(vm, onDone = { forgot = false })
            } else {
                LoginScreen(vm, onForgot = { forgot = true })
            }
        }
        profile == null -> OnboardingFlow(vm)
        else -> MainShell(vm)
    }
}

/** Schede: 0 = Entrata/Uscita (sinistra), 1 = Bilancio (centro), 2 = Account (destra). */
@Composable
private fun MainShell(vm: MainViewModel) {
    var tab by rememberSaveable { mutableStateOf(1) }
    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier
            .weight(1f)
            .fillMaxWidth()) {
            when (tab) {
                0 -> TransactionsScreen(vm)
                1 -> HomeScreen(vm)
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
        .padding(start = 20.dp, end = 20.dp, bottom = 8.dp, top = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(p.bar)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem(Ic.SWAP, l("Income/Expense", "Entrata/Uscita"), selected == 0, big = false) { onSelect(0) }
            NavItem(Ic.GAUGE, l("Balance", "Bilancio"), selected == 1, big = true) { onSelect(1) }
            NavItem(Ic.USER, l("Account", "Account"), selected == 2, big = false) { onSelect(2) }
        }
    }
}

@Composable
private fun NavItem(icon: Ic, label: String, selected: Boolean, big: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val tint = if (selected) BarAccent else p.onBar.copy(alpha = 0.65f)
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) Color.White.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIcon(icon, tint, iconSize = if (big) 30.dp else 24.dp)
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = tint,
            fontSize = if (big) 12.sp else 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}
