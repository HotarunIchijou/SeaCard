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
import ru.merrcurys.seacard.core.utils.CardSortUtil
import ru.merrcurys.seacard.core.utils.SortType
import ru.merrcurys.seacard.domain.entity.Card as CardModel

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = DatabaseProvider.get(application).cardDao()
    private val prefs = application.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)

    private val cardsFromDb = dao.getAllFlow().map { list -> list.map { it.toCard() } }

    val sortType = MutableStateFlow(loadSortTypePref())
    val showCoverPicker = MutableStateFlow(false)

    val cards: StateFlow<List<CardModel>> = combine(cardsFromDb, sortType) { list, sort ->
        CardSortUtil.sorted(list, sort.name)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private fun loadSortTypePref(): SortType =
        CardSortUtil.parseSortType(prefs.getString("sort_type", null))

    fun setSortType(type: SortType) {
        prefs.edit { putString("sort_type", type.name) }
        sortType.value = type
        ru.merrcurys.seacard.widget.SeaCardAppWidgetProvider.notifyDataChanged(getApplication())
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

    }
