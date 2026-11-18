package com.yann4wrld.y4battery

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

private val Green = Color(0xFF00E38C)

class MainActivity : ComponentActivity() {

    private val tick = mutableIntStateOf(0)
    private val permListener = Shizuku.OnRequestPermissionResultListener { _, _ -> tick.intValue++ }
    private val binderListener = Shizuku.OnBinderReceivedListener { tick.intValue++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(permListener)
        Shizuku.addBinderReceivedListenerSticky(binderListener)
        setContent {
            val scheme = if (isSystemInDarkTheme()) {
                darkColorScheme(primary = Green, onPrimary = Color(0xFF101820))
            } else {
                lightColorScheme(primary = Color(0xFF00A86B))
            }
            MaterialTheme(colorScheme = scheme) {
                Surface(Modifier.fillMaxSize()) { MainScreen(tick.intValue) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        tick.intValue++ // re-vérifie les permissions au retour des réglages
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permListener)
        Shizuku.removeBinderReceivedListener(binderListener)
        super.onDestroy()
    }
}

@Composable
fun MainScreen(tick: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { Prefs(ctx) }
    val snackbar = remember { SnackbarHostState() }

    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var cleaning by remember { mutableStateOf(false) }
    var blocked by remember { mutableStateOf(prefs.blocked) }
    var auto by remember { mutableStateOf(prefs.auto) }
    var showSystem by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }

    val usageOk = remember(tick) { AppRepo.hasUsageAccess(ctx) }
    val shizuku = remember(tick) { ShizukuShell.state() }

    LaunchedEffect(tick, showSystem, reload) {
        loading = apps.isEmpty()
        apps = withContext(Dispatchers.IO) { AppRepo.load(ctx, showSystem) }
        loading = false
    }

    val shown = apps.filter {
        query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true)
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("Y4 Battery", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text(
                    "Coupe les applis qui vident ta batterie en arrière-plan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatusRow(
                            title = "Accès aux statistiques d'utilisation",
                            ok = usageOk,
                            detail = if (usageOk) "Actif" else "Pour voir quand chaque appli a servi",
                            action = "Autoriser"
                        ) { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }

                        when (shizuku) {
                            ShizukuState.READY -> StatusRow(
                                "Mode avancé (Shizuku)", true, "Actif : gel réel des applis", null
                            ) {}
                            ShizukuState.NO_PERMISSION -> StatusRow(
                                "Mode avancé (Shizuku)", false, "Shizuku tourne, autorise Y4 Battery", "Autoriser"
                            ) { Shizuku.requestPermission(1) }
                            ShizukuState.NOT_RUNNING -> StatusRow(
                                "Mode avancé (Shizuku)", false,
                                "Optionnel : installe et démarre Shizuku pour geler vraiment les applis",
                                "Ouvrir"
                            ) { openShizuku(ctx) }
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        cleaning = true
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { Cleaner.clean(ctx) }
                            cleaning = false
                            reload++
                            val frozenTxt = if (r.frozen > 0) " · ${r.frozen} gelée(s)" else ""
                            snackbar.showSnackbar("${r.cleaned} applis nettoyées$frozenTxt · ${r.freedMb} Mo libérés")
                        }
                    },
                    enabled = !cleaning,
                    modifier = Modifier.fillMaxWidth().height(58.dp)
                ) {
                    if (cleaning) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else Text("⚡ Nettoyer maintenant", fontWeight = FontWeight.Bold)
                }
            }

            item {
                SwitchRow("Nettoyage auto (toutes les 15 min)", auto) {
                    auto = it
                    prefs.auto = it
                    AutoClean.set(ctx, it)
                }
                SwitchRow("Afficher les applis système", showSystem) { showSystem = it }
            }

            item {
                Text(
                    "Active l'interrupteur des applis à geler. " +
                        if (shizuku == ShizukuState.READY) "Elles seront interdites d'arrière-plan et arrêtées à chaque nettoyage."
                        else "Sans Shizuku, elles sont seulement nettoyées comme les autres.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Rechercher une appli") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            items(shown, key = { it.pkg }) { app ->
                AppRow(
                    app = app,
                    checked = app.pkg in blocked,
                    usageOk = usageOk,
                    onToggle = { on ->
                        val nb = if (on) blocked + app.pkg else blocked - app.pkg
                        blocked = nb
                        prefs.blocked = nb
                        if (shizuku == ShizukuState.READY) {
                            scope.launch(Dispatchers.IO) {
                                ShizukuShell.restrictBackground(app.pkg, on)
                                if (on) ShizukuShell.forceStop(app.pkg)
                                withContext(Dispatchers.Main) { reload++ }
                            }
                        }
                    },
                    onOpen = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))
                        )
                    }
                )
            }
        }
    }
}

private fun openShizuku(ctx: Context) {
    val launch = ctx.packageManager.getLaunchIntentForPackage(ShizukuShell.PKG)
    ctx.startActivity(
        launch ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
    )
}

@Composable
private fun StatusRow(title: String, ok: Boolean, detail: String, action: String?, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (ok) "✅" else "⚠️")
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!ok && action != null) {
            FilledTonalButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun AppRow(
    app: AppItem,
    checked: Boolean,
    usageOk: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
) {
    val sub = buildList {
        if (app.stopped) add("❄️ Gelée")
        if (app.system) add("Système")
        if (usageOk) {
            add(
                if (app.lastUsed > 0) "Utilisée " + DateUtils.getRelativeTimeSpanString(app.lastUsed)
                else "Pas utilisée depuis 7 j"
            )
        }
    }.joinToString(" · ")

    Card(Modifier.fillMaxWidth().clickable { onOpen() }) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(42.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sub.isNotEmpty()) {
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(checked = checked, onCheckedChange = onToggle)
        }
    }
}
