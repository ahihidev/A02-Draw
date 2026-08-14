package com.a02.draw.domain.usecase

import com.a02.draw.core.common.result.AppResult
import com.a02.draw.domain.model.ArCatalog
import com.a02.draw.domain.repository.ArContentRepository
import javax.inject.Inject

class GetArCatalogUseCase @Inject constructor(
    private val repository: ArContentRepository,
) {
    suspend operator fun invoke(forceRefresh: Boolean = false): AppResult<ArCatalog> =
        repository.getCatalog(forceRefresh)
}
