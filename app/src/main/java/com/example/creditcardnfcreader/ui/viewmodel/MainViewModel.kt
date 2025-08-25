package com.example.creditcardnfcreader.ui.viewmodel


import android.nfc.Tag
import android.nfc.tech.IsoDep
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.creditcardnfcreader.data.model.EmvCard
import com.example.creditcardnfcreader.data.repository.NfcRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class Success(val card: EmvCard) : UiState()
    data class Error(val message: String) : UiState()
}

class MainViewModel : ViewModel() {
    private val nfcRepository = NfcRepository()

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun processNfcTag(tag: Tag?) {
        if (tag == null) {
            _uiState.value = UiState.Error("NFC Tag is not supported.")
            return
        }

        viewModelScope.launch {
            _uiState.value = UiState.Loading
            IsoDep.get(tag)?.let { isoDep ->
                nfcRepository.readNfcTag(isoDep)
                    .onSuccess { card -> _uiState.value = UiState.Success(card) }
                    .onFailure { error ->
                        _uiState.value = UiState.Error(error.localizedMessage ?: "An unknown error occurred.")
                    }
            } ?: run {
                _uiState.value = UiState.Error("Only ISO-DEP compatible tags are supported.")
            }
        }
    }

    fun resetState() {
        _uiState.value = UiState.Idle
    }
}