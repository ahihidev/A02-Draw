package com.a02.draw.feature.home.screen.settings

import androidx.lifecycle.viewModelScope
import com.a02.draw.core.common.result.AppResult
import com.a02.draw.core.ui.base.BaseViewModel
import com.a02.draw.domain.model.AppSettingItem
import com.a02.draw.domain.model.SettingType
import com.a02.draw.domain.usecase.GetArCatalogUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getCatalog: GetArCatalogUseCase,
) : BaseViewModel<SettingsUiState, SettingsEffect>(SettingsUiState()) {
    private var loadJob: Job? = null

    init {
        load()
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            SettingsAction.Retry -> load(true)
            is SettingsAction.OpenItem -> openItem(action.settingId)
            is SettingsAction.OpenBottom -> send(SettingsEffect.NavigateBottom(action.destination))
        }
    }

    private fun load(forceRefresh: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            updateState { copy(isLoading = true, hasError = false) }
            when (val result = getCatalog(forceRefresh)) {
                is AppResult.Success -> updateState {
                    val visibleItems = result.data.settings.filterNot { it.id in HIDDEN_IDS }
                    copy(
                        items = visibleItems.ensurePremiumEntry(),
                        isLoading = false,
                        hasError = false,
                    )
                }

                is AppResult.Failure -> updateState { copy(isLoading = false, hasError = true) }
            }
        }
    }

    private fun openItem(id: String) {
        val item = state.value.items.firstOrNull { it.id == id } ?: return
        when {
            id == SUBSCRIPTION_ID -> send(SettingsEffect.NavigatePremium)
            id in setOf("help", "privacy", "terms") -> send(SettingsEffect.NavigateDetail(id))
            id == "update" || item.type == SettingType.RATE -> send(SettingsEffect.OpenStore)
            item.type == SettingType.SHARE -> send(SettingsEffect.ShareApp)
            id == "feedback" -> send(SettingsEffect.OpenExternal("mailto:?subject=AR%20Drawing%20feedback"))
            !item.target.isNullOrBlank() -> send(SettingsEffect.OpenExternal(requireNotNull(item.target)))
        }
    }

    private fun send(effect: SettingsEffect) {
        viewModelScope.launch { sendEffect(effect) }
    }

    private fun List<AppSettingItem>.ensurePremiumEntry(): List<AppSettingItem> =
        if (any { it.id == SUBSCRIPTION_ID }) {
            this
        } else {
            listOf(AppSettingItem(id = SUBSCRIPTION_ID, title = "")) + this
        }

    private companion object {
        const val SUBSCRIPTION_ID = "subscription"
        val HIDDEN_IDS = setOf("gift", "music", "help", "update", "feedback")
    }
}
