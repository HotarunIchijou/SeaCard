package ru.merrcurys.seacard.features.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import ru.merrcurys.seacard.core.db.DatabaseProvider
import ru.merrcurys.seacard.domain.entity.Card as CardModel

class CardDetailViewModel(application: Application, val cardId: Long) : AndroidViewModel(application) {

    private val dao = DatabaseProvider.get(application).cardDao()

    private val _card = MutableStateFlow<CardModel?>(null)
    val card: StateFlow<CardModel?> = _card.asStateFlow()

    init {
        loadCard()
    }

    fun loadCard() {
        viewModelScope.launch(Dispatchers.IO) {
            _card.value = dao.getById(cardId)?.toCard()
        }
    }

    fun updateCard(updated: CardModel?) {
        _card.value = updated
    }

    suspend fun getCard(): CardModel? = withContext(Dispatchers.IO) {
        dao.getById(cardId)?.toCard()
    }

    fun updateNote(note: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateNote(cardId, note)
            _card.value = dao.getById(cardId)?.toCard()
        }
    }

    fun updateFrontCover(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateFrontCover(cardId, path)
            _card.value = dao.getById(cardId)?.toCard()
        }
    }

    fun updateBackCover(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateBackCover(cardId, path)
            _card.value = dao.getById(cardId)?.toCard()
        }
    }

    fun updateCardFields(name: String, code: String, type: String, color: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = dao.getById(cardId) ?: return@launch
            dao.update(entity.copy(name = name, code = code, type = type, color = color))
            _card.value = dao.getById(cardId)?.toCard()
        }
    }

    fun deleteCard(onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteById(cardId)
            withContext(Dispatchers.Main) { onDone() }
        }
    }
}

class CardDetailViewModelFactory(private val application: Application, private val cardId: Long) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = CardDetailViewModel(application, cardId) as T
}
