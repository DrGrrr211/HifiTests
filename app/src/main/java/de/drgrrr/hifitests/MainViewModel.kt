package de.drgrrr.hifitests

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

enum class Sort(val label: String) { NEWEST("Neueste"), RATING("Beste Wertung"), PRICE_ASC("Preis aufsteigend"), PRICE_DESC("Preis absteigend") }

data class Filter(
    val query: String = "",
    val category: String? = null,
    val brand: String = "",
    val priceMin: Int? = null,
    val priceMax: Int? = null,
    val wattMin: Int? = null,
    val techs: Set<String> = emptySet(),
    val ratingMin: Int = 0,
    val source: String? = null,
    val days: Int = 0,
    val sort: Sort = Sort.NEWEST,
) {
    val active: Int
        get() = listOf(
            category != null, brand.isNotBlank(), priceMin != null, priceMax != null, wattMin != null,
            techs.isNotEmpty(), ratingMin > 0, source != null, days > 0,
        ).count { it }
}

data class UiState(
    val remote: List<Review> = emptyList(),
    val manual: List<Review> = emptyList(),
    val updated: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val status: StatusFile = StatusFile(),
    val filter: Filter = Filter(),
) {
    val all: List<Review> get() = manual + remote
    val sources: List<String> get() = all.map { it.source }.distinct().sorted()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repo(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        val c = repo.cached()
        _state.value = UiState(remote = c.reviews, updated = c.updated, manual = repo.manual(), status = repo.status())
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val f = repo.refresh()
                _state.update { it.copy(remote = f.reviews, updated = f.updated, loading = false, status = repo.status()) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "Aktualisierung fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}") }
            }
        }
    }

    fun setFilter(f: Filter) = _state.update { it.copy(filter = f) }

    fun saveManual(r: Review) {
        val list = repo.manual().filter { it.id != r.id } + r
        repo.saveManual(list)
        _state.update { it.copy(manual = list) }
    }

    fun deleteManual(id: String) {
        val list = repo.manual().filter { it.id != id }
        repo.saveManual(list)
        _state.update { it.copy(manual = list) }
    }

    fun importBackup(text: String): String = try {
        val n = repo.importManual(text)
        _state.update { it.copy(manual = repo.manual()) }
        "$n Einträge importiert"
    } catch (e: Exception) {
        "Import fehlgeschlagen: Datei ist keine gültige Sicherung"
    }

    fun exportBackup(): String = repo.exportManual()

    companion object {
        fun apply(all: List<Review>, f: Filter): List<Review> {
            val q = f.query.trim().lowercase()
            val cutoff = if (f.days > 0) Instant.now().minus(f.days.toLong(), ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString() else null
            val res = all.filter { r ->
                (q.isEmpty() || q.split(" ").filter { it.isNotBlank() }.all { w ->
                    r.title.lowercase().contains(w) || r.product.lowercase().contains(w) ||
                        (r.brand?.lowercase()?.contains(w) == true) || r.teaser.lowercase().contains(w) ||
                        (r.note?.lowercase()?.contains(w) == true)
                }) &&
                    (f.category == null || Catalog.matches(r, f.category)) &&
                    (f.brand.isBlank() || (r.brand ?: r.title).contains(f.brand.trim(), ignoreCase = true)) &&
                    (f.priceMin == null || (r.priceEur != null && r.priceEur >= f.priceMin)) &&
                    (f.priceMax == null || (r.priceEur != null && r.priceEur <= f.priceMax)) &&
                    (f.wattMin == null || (r.watt != null && r.watt >= f.wattMin)) &&
                    (f.techs.isEmpty() || r.technology.containsAll(f.techs)) &&
                    (f.ratingMin == 0 || (r.rating != null && r.rating >= f.ratingMin)) &&
                    (f.source == null || r.source == f.source) &&
                    (cutoff == null || r.date >= cutoff)
            }
            return when (f.sort) {
                Sort.NEWEST -> res.sortedByDescending { it.date }
                Sort.RATING -> res.sortedWith(compareByDescending<Review> { it.rating ?: -1 }.thenByDescending { it.date })
                Sort.PRICE_ASC -> res.sortedWith(compareBy<Review> { it.priceEur ?: Int.MAX_VALUE })
                Sort.PRICE_DESC -> res.sortedByDescending { it.priceEur ?: -1 }
            }
        }
    }
}
