package de.drgrrr.hifitests

import kotlinx.serialization.Serializable

@Serializable
data class Review(
    val id: String,
    val title: String,
    val product: String = "",
    val productKey: String = "",
    val url: String = "",
    val source: String = "",
    val sourceId: String = "",
    val lang: String = "de",
    val date: String = "",
    val category: String = "sonstiges",
    val brand: String? = null,
    val priceEur: Int? = null,
    val priceCurrency: String? = null,
    val watt: Int? = null,
    val technology: List<String> = emptyList(),
    val rating: Int? = null,
    val ratingRaw: String? = null,
    val teaser: String = "",
    // nur bei eigenen Readly-Einträgen
    val readly: Boolean = false,
    val issue: String? = null,
    val page: String? = null,
    val note: String? = null,
)

@Serializable
data class ReviewFile(val updated: String? = null, val count: Int = 0, val reviews: List<Review> = emptyList())

@Serializable
data class SourceStatus(
    val id: String, val name: String, val ok: Boolean = false, val feed: String? = null,
    val items: Int = 0, val reviews: Int = 0, val new: Int = 0, val error: String? = null,
)

@Serializable
data class StatusFile(val updated: String? = null, val sources: List<SourceStatus> = emptyList())

data class Category(val id: String, val label: String)

object Catalog {
    const val HIGHEND = "kopfhoerer_highend"
    const val ALL_SPEAKERS = "alle_lautsprecher"

    /** Kategorien für Filter und Bestenlisten. Reihenfolge = Anzeige. */
    val categories = listOf(
        Category("standlautsprecher", "Standlautsprecher"),
        Category("regallautsprecher", "Regallautsprecher"),
        Category("aktivlautsprecher", "Aktivlautsprecher"),
        Category("subwoofer", "Subwoofer"),
        Category("lautsprecher", "Lautsprecher (sonstige)"),
        Category("kopfhoerer_overear", "Over-Ear-Kopfhörer"),
        Category(HIGHEND, "Over-Ear High-End"),
        Category("inear", "In-Ear-Kopfhörer"),
        Category("kopfhoererverstaerker", "Kopfhörerverstärker"),
        Category("vollverstaerker", "Vollverstärker"),
        Category("streaming_verstaerker", "Streaming-Verstärker"),
        Category("vor_endstufe", "Vor- & Endstufen"),
        Category("dac", "D/A-Wandler"),
        Category("streamer", "Streamer"),
        Category("plattenspieler", "Plattenspieler"),
        Category("tonabnehmer", "Tonabnehmer & Phono"),
        Category("cd_player", "CD-Player"),
        Category("sonstiges", "Sonstiges"),
    )

    /** Kategorien, die man einem eigenen Eintrag geben kann (ohne die abgeleitete High-End-Liste). */
    val assignable = categories.filter { it.id != HIGHEND }

    fun label(id: String) = categories.firstOrNull { it.id == id }?.label ?: id

    val technologies = listOf(
        "Bändchen", "Planarmagnetisch", "Elektrostatisch", "Röhre", "Class D", "Class A",
        "Koaxial", "Breitbänder", "Horn", "Dipol", "Kabellos", "Offen", "Geschlossen",
    )

    val readlyMagazines = listOf(
        "STEREO", "AUDIO", "stereoplay", "HiFi Test", "image hifi", "HIFI & Musik Journal", "HiFi einsnull",
        "Klang+Ton", "FIDELITY", "What Hi-Fi?", "Hi-Fi News", "Hi-Fi Choice", "Sonstiges",
    )

    /** Over-Ear High-End: Over-Ear ab 1.000 € oder planar/elektrostatisch. */
    fun matches(r: Review, cat: String): Boolean = when (cat) {
        HIGHEND -> r.category == "kopfhoerer_overear" &&
            ((r.priceEur ?: 0) >= 1000 || "Planarmagnetisch" in r.technology || "Elektrostatisch" in r.technology)
        else -> r.category == cat
    }

    fun normKey(s: String): String = s.lowercase()
        .replace(Regex("[^a-z0-9äöüß+ ]"), " ")
        .replace(Regex("\\b(the|der|die|das|und|and|mit|with|new|neu|neue|neuer)\\b"), " ")
        .replace(Regex("\\s+"), " ").trim()
}

/** Umrechnung einer Magazin-Wertung auf 0–100. */
enum class Scale(val label: String) {
    PERCENT("Prozent (0–100)"),
    POINTS("Punkte von Maximum"),
    GRADE("Schulnote (1–6)"),
    STARS("Sterne (von 5)"),
    VERDICT("Urteil (Wort)");
}

val verdicts = listOf(
    "Referenz" to 98, "Überragend" to 97, "Highlight" to 95, "Exzellent" to 94, "Hervorragend" to 93,
    "Sehr gut" to 90, "Gut" to 78, "Befriedigend" to 64, "Ausreichend" to 50, "Mangelhaft" to 30,
)

fun normalize(scale: Scale, value: Double?, max: Double?, verdict: String?): Int? = when (scale) {
    Scale.PERCENT -> value?.takeIf { it in 0.0..100.0 }?.let { Math.round(it).toInt() }
    Scale.POINTS -> if (value != null && max != null && max > 0 && value in 0.0..max) Math.round(value / max * 100).toInt() else null
    Scale.GRADE -> value?.takeIf { it in 1.0..6.0 }?.let { Math.round((6 - it) / 5 * 100).toInt() }
    Scale.STARS -> value?.takeIf { it in 0.0..5.0 }?.let { Math.round(it * 20).toInt() }
    Scale.VERDICT -> verdicts.firstOrNull { it.first == verdict }?.second
}

data class RankRow(
    val key: String, val product: String, val brand: String?, val avg: Int, val count: Int,
    val sources: List<String>, val price: Int?, val reviews: List<Review>,
)
