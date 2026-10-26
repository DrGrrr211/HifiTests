package de.drgrrr.hifitests

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID

// ================================================================ Hilfen

fun fmtDate(iso: String): String =
    if (iso.length >= 10) "${iso.substring(8, 10)}.${iso.substring(5, 7)}.${iso.substring(0, 4)}" else iso

fun fmtPrice(p: Int?): String? = p?.let { "%,d €".format(it).replace(',', '.') }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Bar(title: String, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

@Composable
fun ScoreBadge(score: Int?, big: Boolean = false) {
    val c = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(if (big) 52.dp else 42.dp)
            .background(if (score != null) c.primary else c.surfaceVariant, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            score?.toString() ?: "–",
            color = if (score != null) c.onPrimary else c.secondary,
            fontWeight = FontWeight.Bold,
            fontSize = if (big) 20.sp else 16.sp,
        )
    }
}

@Composable
fun Meta(text: String, modifier: Modifier = Modifier) = Text(text, modifier = modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)

/** Einfache Auswahlliste ohne experimentelle APIs. */
@Composable
fun <T> Picker(label: String, current: String, options: List<T>, text: (T) -> String, onPick: (T) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
            Column(Modifier.fillMaxWidth()) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                Text(current, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(text(o)) }, onClick = { open = false; onPick(o) })
            }
        }
    }
}

@Composable
fun NumField(label: String, value: Int?, onChange: (Int?) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value?.toString() ?: "",
        onValueChange = { s -> onChange(s.filter { it.isDigit() }.take(7).toIntOrNull()) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
fun ReviewCard(r: Review, onClick: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().clickable {
            if (onClick != null) onClick() else if (r.readly) openReadly(ctx, r) else openUrl(ctx, r.url)
        },
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            ScoreBadge(r.rating)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                val bits = buildList {
                    add(r.source)
                    if (r.readly) {
                        r.issue?.let { add("Ausgabe $it") }
                        r.page?.let { add("S. $it") }
                    } else add(fmtDate(r.date))
                    add(Catalog.label(r.category))
                }
                Meta(bits.joinToString(" · "))
                val facts = listOfNotNull(
                    r.brand, fmtPrice(r.priceEur)?.let { if (r.priceCurrency != null && r.priceCurrency != "EUR") "ca. $it" else it },
                    r.watt?.let { "$it W" },
                ) + r.technology
                if (facts.isNotEmpty()) Meta(facts.joinToString(" · "))
                val txt = if (r.readly) r.note.orEmpty() else r.teaser
                if (txt.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(txt, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
                if (r.readly) {
                    TextButton(onClick = { openReadly(ctx, r) }, contentPadding = PaddingValues(0.dp)) { Text("In Readly öffnen") }
                }
            }
        }
    }
}

@Composable
fun Empty(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium)
    }
}

// ================================================================ Neu

@Composable
fun FeedScreen(state: UiState, vm: MainViewModel) {
    var showSources by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Bar("Neue Tests") {
            IconButton(onClick = { showSources = true }) { Icon(Icons.Filled.Info, "Quellen") }
            if (state.loading) CircularProgressIndicator(Modifier.size(22.dp).padding(end = 4.dp), strokeWidth = 2.dp)
            else IconButton(onClick = { vm.refresh() }) { Icon(Icons.Filled.Refresh, "Aktualisieren") }
        }
        val list = remember(state.remote, state.manual) { state.all.sortedByDescending { it.date } }
        Text(
            buildString {
                append("${state.remote.size} Tests")
                state.updated?.let { append(" · Stand ${fmtDate(it)} ${it.take(16).drop(11)} UTC") }
            },
            Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary,
        )
        state.error?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (list.isEmpty()) Empty(if (state.loading) "Lade Tests …" else "Noch keine Tests. Der Sammler läuft täglich – oder oben aktualisieren.")
        else LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { ReviewCard(it) }
        }
    }
    if (showSources) SourcesDialog(state.status) { showSources = false }
}

@Composable
fun SourcesDialog(status: StatusFile, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Schließen") } },
        title = { Text("Quellen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (status.sources.isEmpty()) Text("Noch kein Sammel-Lauf.")
                status.sources.forEach { s ->
                    Text((if (s.ok) "✓ " else "✗ ") + s.name, fontWeight = FontWeight.SemiBold)
                    Meta(if (s.ok) "${s.reviews} Tests im Feed, ${s.new} neu" else "nicht erreichbar")
                    Spacer(Modifier.height(6.dp))
                }
                Meta("Readly-Tests trägst du im Reiter „Readly“ selbst ein.")
            }
        },
    )
}

// ================================================================ Suche

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(state: UiState, vm: MainViewModel) {
    val f = state.filter
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val results = remember(state.remote, state.manual, f) { MainViewModel.apply(state.all, f) }
    Column(Modifier.fillMaxSize()) {
        Bar("Suche") {
            if (f.active > 0) TextButton(onClick = { vm.setFilter(Filter(query = f.query, sort = f.sort)) }) { Text("Filter löschen") }
        }
        OutlinedTextField(
            value = f.query,
            onValueChange = { vm.setFilter(f.copy(query = it)) },
            placeholder = { Text("Modell, Marke, Stichwort …") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (f.query.isNotEmpty()) IconButton(onClick = { vm.setFilter(f.copy(query = "")) }) { Icon(Icons.Filled.Clear, "Leeren") } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showFilters = !showFilters }) {
                Text(if (showFilters) "Filter ausblenden" else "Filter" + if (f.active > 0) " (${f.active})" else "")
            }
            Spacer(Modifier.weight(1f))
            Meta("${results.size} Treffer")
        }
        if (showFilters) {
            Column(
                Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Picker("Kategorie", f.category?.let { Catalog.label(it) } ?: "Alle", listOf<Category?>(null) + Catalog.categories,
                    { it?.label ?: "Alle" }, { vm.setFilter(f.copy(category = it?.id)) })
                OutlinedTextField(f.brand, { vm.setFilter(f.copy(brand = it)) }, label = { Text("Marke") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("Preis ab €", f.priceMin, { vm.setFilter(f.copy(priceMin = it)) }, Modifier.weight(1f))
                    NumField("Preis bis €", f.priceMax, { vm.setFilter(f.copy(priceMax = it)) }, Modifier.weight(1f))
                }
                NumField("Leistung ab Watt", f.wattMin, { vm.setFilter(f.copy(wattMin = it)) }, Modifier.fillMaxWidth())
                Text("Technologie", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Catalog.technologies.forEach { t ->
                        FilterChip(
                            selected = t in f.techs,
                            onClick = { vm.setFilter(f.copy(techs = if (t in f.techs) f.techs - t else f.techs + t)) },
                            label = { Text(t) },
                        )
                    }
                }
                Text("Mindestwertung: " + if (f.ratingMin == 0) "egal" else "${f.ratingMin} / 100", style = MaterialTheme.typography.labelLarge)
                Slider(value = f.ratingMin.toFloat(), onValueChange = { vm.setFilter(f.copy(ratingMin = (it / 5).toInt() * 5)) }, valueRange = 0f..100f)
                Picker("Quelle / Magazin", f.source ?: "Alle", listOf<String?>(null) + state.sources, { it ?: "Alle" }, { vm.setFilter(f.copy(source = it)) })
                val periods = listOf(0 to "Gesamter Zeitraum", 30 to "Letzte 30 Tage", 90 to "Letzte 3 Monate", 365 to "Letztes Jahr")
                Picker("Zeitraum", periods.first { it.first == f.days }.second, periods, { it.second }, { vm.setFilter(f.copy(days = it.first)) })
                Picker("Sortierung", f.sort.label, Sort.entries.toList(), { it.label }, { vm.setFilter(f.copy(sort = it)) })
                Spacer(Modifier.height(8.dp))
            }
            HorizontalDivider()
        }
        if (results.isEmpty()) Empty("Keine Treffer.")
        else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results, key = { it.id }) { ReviewCard(it) }
        }
    }
}

// ================================================================ Bestenlisten

@Composable
fun RankingScreen(state: UiState) {
    var cat by rememberSaveable { mutableStateOf(Catalog.categories.first().id) }
    var minTests by rememberSaveable { mutableIntStateOf(1) }
    var open by remember { mutableStateOf<String?>(null) }
    val rows = remember(state.remote, state.manual, cat, minTests) { Repo.rank(state.all, cat, minTests) }
    Column(Modifier.fillMaxSize()) {
        Bar("Bestenlisten")
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(Catalog.categories.filter { it.id != "sonstiges" }, key = { it.id }) { c ->
                FilterChip(selected = c.id == cat, onClick = { cat = c.id; open = null }, label = { Text(c.label) })
            }
        }
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Meta("Mindestens")
            listOf(1, 2, 3).forEach { n ->
                TextButton(onClick = { minTests = n }) {
                    Text("$n Test" + if (n > 1) "s" else "", fontWeight = if (n == minTests) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        Meta("Ø aller Wertungen, umgerechnet auf 0–100", Modifier.padding(horizontal = 16.dp))
        if (rows.isEmpty()) Empty("Noch keine bewerteten Tests in dieser Kategorie.")
        else LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(rows, key = { _, r -> r.key }) { i, r ->
                RankCard(i + 1, r, expanded = open == r.key) { open = if (open == r.key) null else r.key }
            }
        }
    }
}

@Composable
fun RankCard(pos: Int, r: RankRow, expanded: Boolean, onToggle: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().clickable { onToggle() }, shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$pos", Modifier.width(28.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                Column(Modifier.weight(1f)) {
                    Text(r.product, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Meta(listOfNotNull(r.brand, fmtPrice(r.price)).joinToString(" · ").ifEmpty { " " })
                    Meta("${r.count} Test${if (r.count > 1) "s" else ""}: ${r.sources.joinToString(", ")}")
                }
                ScoreBadge(r.avg, big = true)
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { r.reviews.forEach { ReviewCard(it) } }
            }
        }
    }
}

// ================================================================ Readly

@Composable
fun ReadlyScreen(state: UiState, vm: MainViewModel) {
    val ctx = LocalContext.current
    var editing by remember { mutableStateOf<Review?>(null) }
    var menu by remember { mutableStateOf(false) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            Toast.makeText(ctx, if (text != null) vm.importBackup(text) else "Datei nicht lesbar", Toast.LENGTH_LONG).show()
        }
    }
    Scaffold(
        topBar = {
            Bar("Meine Readly-Tests") {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menü") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Sicherung teilen") }, onClick = {
                            menu = false
                            val send = Intent(Intent.ACTION_SEND).setType("application/json")
                                .putExtra(Intent.EXTRA_SUBJECT, "HiFi Tests – Readly-Sicherung")
                                .putExtra(Intent.EXTRA_TEXT, vm.exportBackup())
                            ctx.startActivity(Intent.createChooser(send, "Sicherung speichern"))
                        })
                        DropdownMenuItem(text = { Text("Sicherung importieren") }, onClick = {
                            menu = false
                            importer.launch(arrayOf("application/json", "text/plain", "*/*"))
                        })
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = Review(id = "", title = "", readly = true) }, containerColor = Navy) {
                Icon(Icons.Filled.Add, "Neuer Eintrag", tint = androidx.compose.ui.graphics.Color.White)
            }
        },
    ) { pad ->
        val list = state.manual.sortedByDescending { it.date }
        if (list.isEmpty()) Box(Modifier.padding(pad)) {
            Empty("Hier trägst du Tests aus STEREO, AUDIO, stereoplay & Co. ein. Sie fließen in Suche und Bestenlisten ein.")
        }
        else LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { r -> ReviewCard(r) { editing = r } }
        }
    }
    editing?.let { r ->
        EntryDialog(r, onDismiss = { editing = null }, onSave = { vm.saveManual(it); editing = null },
            onDelete = if (r.id.isNotEmpty()) ({ vm.deleteManual(r.id); editing = null }) else null)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryDialog(start: Review, onDismiss: () -> Unit, onSave: (Review) -> Unit, onDelete: (() -> Unit)?) {
    var magazine by remember { mutableStateOf(start.source.removePrefix("Readly · ").ifBlank { Catalog.readlyMagazines.first() }) }
    var issue by remember { mutableStateOf(start.issue.orEmpty()) }
    var page by remember { mutableStateOf(start.page.orEmpty()) }
    var product by remember { mutableStateOf(start.product) }
    var brand by remember { mutableStateOf(start.brand.orEmpty()) }
    var category by remember { mutableStateOf(start.category.takeIf { it != "sonstiges" } ?: "standlautsprecher") }
    var price by remember { mutableStateOf(start.priceEur) }
    var watt by remember { mutableStateOf(start.watt) }
    var techs by remember { mutableStateOf(start.technology.toSet()) }
    var scale by remember { mutableStateOf(Scale.PERCENT) }
    var value by remember { mutableStateOf(start.rating?.toString().orEmpty()) }
    var max by remember { mutableStateOf("") }
    var verdict by remember { mutableStateOf("Sehr gut") }
    var note by remember { mutableStateOf(start.note.orEmpty()) }

    val num = { s: String -> s.replace(',', '.').toDoubleOrNull() }
    val score = normalize(scale, num(value), num(max), verdict)
    val valid = product.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (start.id.isEmpty()) "Readly-Test eintragen" else "Eintrag bearbeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker("Magazin", magazine, Catalog.readlyMagazines, { it }, { magazine = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(issue, { issue = it }, label = { Text("Ausgabe") }, placeholder = { Text("11/2026") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(page, { page = it }, label = { Text("Seite") }, singleLine = true, modifier = Modifier.weight(0.6f))
                }
                OutlinedTextField(product, { product = it }, label = { Text("Produkt *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(brand, { brand = it }, label = { Text("Marke") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Picker("Kategorie", Catalog.label(category), Catalog.assignable, { it.label }, { category = it.id })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("Preis €", price, { price = it }, Modifier.weight(1f))
                    NumField("Watt", watt, { watt = it }, Modifier.weight(1f))
                }
                Text("Technologie", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Catalog.technologies.forEach { t ->
                        FilterChip(selected = t in techs, onClick = { techs = if (t in techs) techs - t else techs + t }, label = { Text(t) })
                    }
                }
                Picker("Wertung im Heft", scale.label, Scale.entries.toList(), { it.label }, { scale = it })
                when (scale) {
                    Scale.VERDICT -> Picker("Urteil", verdict, verdicts.map { it.first }, { it }, { verdict = it })
                    Scale.POINTS -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value, { value = it }, label = { Text("Punkte") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        OutlinedTextField(max, { max = it }, label = { Text("von max.") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    }
                    else -> OutlinedTextField(value, { value = it }, label = { Text("Wert") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                }
                Meta("Umgerechnet: " + (score?.let { "$it / 100" } ?: "–"))
                OutlinedTextField(note, { note = it }, label = { Text("Kurzfazit / Notiz") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                if (onDelete != null) TextButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(4.dp))
                    Text("Eintrag löschen", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val b = brand.trim().ifBlank { null }
                val p = product.trim()
                val full = if (b != null && !p.startsWith(b, ignoreCase = true)) "$b $p" else p
                onSave(
                    start.copy(
                        id = start.id.ifEmpty { "readly-" + UUID.randomUUID() },
                        title = "$full – $magazine" + (if (issue.isNotBlank()) " ${issue.trim()}" else ""),
                        product = full,
                        productKey = Catalog.normKey(full),
                        source = "Readly · $magazine",
                        sourceId = "readly",
                        date = start.date.ifEmpty { java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString() },
                        category = category,
                        brand = b,
                        priceEur = price,
                        priceCurrency = "EUR",
                        watt = watt,
                        technology = techs.toList().sorted(),
                        rating = score,
                        ratingRaw = when (scale) {
                            Scale.VERDICT -> verdict
                            Scale.POINTS -> "$value/$max"
                            else -> value.ifBlank { null }
                        },
                        readly = true,
                        issue = issue.trim().ifBlank { null },
                        page = page.trim().ifBlank { null },
                        note = note.trim().ifBlank { null },
                    ),
                )
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
