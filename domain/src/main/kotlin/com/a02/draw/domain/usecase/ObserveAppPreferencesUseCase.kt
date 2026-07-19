package com.a02.draw.domain.usecase

import com.a02.draw.domain.model.AppPreferences
import com.a02.draw.domain.repository.AppPreferencesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveAppPreferencesUseCase @Inject constructor(
    private val repository: AppPreferencesRepository,
) {
    operator fun invoke(): Flow<AppPreferences> = repository.observePreferences()
}
