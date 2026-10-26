package de.drgrrr.hifitests

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class Repo(private val ctx: Context) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
    private val cacheFile = File(ctx.filesDir, "reviews_cache.json")
    private val statusFile = File(ctx.filesDir, "status_cache.json")
    private val manualFile = File(ctx.filesDir, "readly_entries.json")

    private fun download(name: String): String {
        val conn = URL(BuildConfig.DATA_URL + name + "?t=" + System.currentTimeMillis() / 60000)
            .openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.setRequestProperty("Cache-Control", "no-cache")
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** Lädt die Testliste vom Server; bei Fehler bleibt der Zwischenspeicher. */
    suspend fun refresh(): ReviewFile = withContext(Dispatchers.IO) {
        val text = download("reviews.json")
        val parsed = json.decodeFromString(ReviewFile.serializer(), text)
        cacheFile.writeText(text)
        runCatching { statusFile.writeText(download("sources_status.json")) }
        parsed
    }

    fun cached(): ReviewFile = runCatching {
        json.decodeFromString(ReviewFile.serializer(), cacheFile.readText())
    }.getOrDefault(ReviewFile())

    fun status(): StatusFile = runCatching {
        json.decodeFromString(StatusFile.serializer(), statusFile.readText())
    }.getOrDefault(StatusFile())

    // ---------------- eigene Readly-Einträge

    private val listSer = ListSerializer(Review.serializer())

    fun manual(): List<Review> = runCatching {
        json.decodeFromString(listSer, manualFile.readText())
    }.getOrDefault(emptyList())

    fun saveManual(list: List<Review>) {
        manualFile.writeText(json.encodeToString(listSer, list))
    }

    fun exportManual(): String = json.encodeToString(listSer, manual())

    /** Fügt importierte Einträge hinzu (gleiche ID wird ersetzt). Rückgabe: Anzahl. */
    fun importManual(text: String): Int {
        val incoming = json.decodeFromString(listSer, text).map { it.copy(readly = true) }
        val merged = (manual().associateBy { it.id } + incoming.associateBy { it.id }).values.toList()
        saveManual(merged)
        return incoming.size
    }

    companion object {
        /** Bestenliste: Mittelwert aller Wertungen je Produkt. */
        fun rank(all: List<Review>, cat: String, minTests: Int): List<RankRow> =
            all.asSequence()
                .filter { it.rating != null && Catalog.matches(it, cat) }
                .groupBy { it.productKey.ifBlank { Catalog.normKey(it.product.ifBlank { it.title }) } }
                .filter { it.key.isNotBlank() && it.value.size >= minTests }
                .map { (key, list) ->
                    val best = list.maxByOrNull { it.date }!!
                    RankRow(
                        key = key,
                        product = best.product.ifBlank { best.title },
                        brand = list.firstNotNullOfOrNull { it.brand },
                        avg = Math.round(list.mapNotNull { it.rating }.average()).toInt(),
                        count = list.size,
                        sources = list.map { it.source }.distinct(),
                        price = list.firstNotNullOfOrNull { it.priceEur },
                        reviews = list.sortedByDescending { it.date },
                    )
                }
                .sortedWith(compareByDescending<RankRow> { it.avg }.thenByDescending { it.count })
                .toList()
    }
}
