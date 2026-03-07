package ru.merrcurys.seacard.features.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.core.content.edit
import ru.merrcurys.seacard.core.db.DatabaseProvider
import ru.merrcurys.seacard.domain.entity.Card as CardModel
import java.text.Collator
import java.util.Locale

enum class SortType(val displayName: String) {
    ADD_TIME("По времени добавления"),
    NAME_ASC("По названию (А-Я)"),
    NAME_DESC("По названию (Я-А)"),
    NAME_ASC_LATIN("По названию (A-Z)"),
    NAME_DESC_LATIN("По названию (Z-A)"),
    USAGE_FREQ("По частоте использования")
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = DatabaseProvider.get(application).cardDao()
    private val prefs = application.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)

    private val cardsFromDb = dao.getAllFlow().map { list -> list.map { it.toCard() } }

    val sortType = MutableStateFlow(loadSortTypePref())
    val showCoverPicker = MutableStateFlow(false)

    val cards: StateFlow<List<CardModel>> = combine(cardsFromDb, sortType) { list, sort ->
        list.sortedWith(getSortComparator(sort))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private fun loadSortTypePref(): SortType {
        val sortTypeName = prefs.getString("sort_type", SortType.ADD_TIME.name)
        return try {
            SortType.valueOf(sortTypeName ?: SortType.ADD_TIME.name)
        } catch (e: IllegalArgumentException) {
            SortType.ADD_TIME
        }
    }

    fun setSortType(type: SortType) {
        prefs.edit { putString("sort_type", type.name) }
        sortType.value = type
    }

    fun setShowCoverPicker(show: Boolean) {
        showCoverPicker.value = show
    }

    fun updateCardUsage(cardId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.incrementUsage(cardId)
        }
    }

    fun deleteCards(cardsToDelete: List<CardModel>) {
        viewModelScope.launch(Dispatchers.IO) {
            cardsToDelete.forEach { dao.deleteById(it.id) }
            ru.merrcurys.seacard.widget.SeaCardAppWidgetProvider.notifyDataChanged(getApplication())
        }
    }

    private fun getSortComparator(sortType: SortType): Comparator<CardModel> = when (sortType) {
        SortType.ADD_TIME -> compareByDescending { it.addTime }
        SortType.NAME_ASC -> compareBy(Collator.getInstance(Locale("ru"))) { it.name }
        SortType.NAME_DESC -> compareByDescending(Collator.getInstance(Locale("ru"))) { it.name }
        SortType.NAME_ASC_LATIN -> compareBy(Collator.getInstance(Locale.ENGLISH)) { it.name }
        SortType.NAME_DESC_LATIN -> compareByDescending(Collator.getInstance(Locale.ENGLISH)) { it.name }
        SortType.USAGE_FREQ -> compareByDescending { it.usageCount }
    }
}
