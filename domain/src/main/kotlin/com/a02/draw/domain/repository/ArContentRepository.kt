package com.a02.draw.domain.repository

import com.a02.draw.core.common.result.AppResult
import com.a02.draw.domain.model.ArCatalog

interface ArContentRepository {
    /** Loads remote content when configured and falls back to the bundled fixture catalog. */
    suspend fun getCatalog(forceRefresh: Boolean = false): AppResult<ArCatalog>
}
