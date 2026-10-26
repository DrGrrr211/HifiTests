package de.drgrrr.hifitests

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle

val Navy = Color(0xFF1F3A5F)

private val Colors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6F2),
    onPrimaryContainer = Color(0xFF0E2238),
    secondary = Color(0xFF4A5A6E),
    secondaryContainer = Color(0xFFE6EBF1),
    onSecondaryContainer = Color(0xFF1B2733),
    background = Color.White,
    surface = Color.White,
    surfaceVariant = Color(0xFFF3F5F8),
    surfaceContainer = Color(0xFFF6F7F9),
    surfaceContainerLow = Color(0xFFF8F9FB),
    surfaceContainerHigh = Color(0xFFF0F2F5),
    outline = Color(0xFFC5CCD6),
    outlineVariant = Color(0xFFE1E5EA),
)

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = Colors) { AppRoot(vm) } }
    }
}

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Neu", Icons.Filled.Home),
    Tab("Suche", Icons.Filled.Search),
    Tab("Bestenlisten", Icons.Filled.Star),
    Tab("Readly", Icons.Filled.Edit),
)

@Composable
fun AppRoot(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> FeedScreen(state, vm)
                1 -> SearchScreen(state, vm)
                2 -> RankingScreen(state)
                else -> ReadlyScreen(state, vm)
            }
        }
    }
}

fun openUrl(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        Toast.makeText(ctx, "Link lässt sich nicht öffnen", Toast.LENGTH_SHORT).show()
    }
}

/** Öffnet die Readly-App. Magazin und Ausgabe liegen danach in der Zwischenablage zum Suchen. */
fun openReadly(ctx: Context, r: Review) {
    val search = listOfNotNull(r.source.removePrefix("Readly · "), r.issue).joinToString(" ")
    val cb = ctx.getSystemService(ClipboardManager::class.java)
    cb.setPrimaryClip(ClipData.newPlainText("Readly", search))
    val launch = ctx.packageManager.getLaunchIntentForPackage("com.readly.client")
    if (launch != null) {
        Toast.makeText(ctx, "„$search“ kopiert – in Readly suchen, Seite ${r.page ?: "?"}", Toast.LENGTH_LONG).show()
        ctx.startActivity(launch)
    } else {
        openUrl(ctx, "https://de.readly.com/")
    }
}
