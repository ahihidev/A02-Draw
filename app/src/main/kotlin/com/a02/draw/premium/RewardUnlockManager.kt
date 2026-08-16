package com.a02.draw.premium

import com.a02.draw.core.common.result.AppResult
import com.a02.draw.core.ui.ads.RewardAccessState
import com.a02.draw.core.ui.ads.RewardContentKey
import com.a02.draw.core.ui.ads.RewardUnlockStore
import com.a02.draw.domain.repository.AppPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Singleton
class RewardUnlockManager @Inject constructor(
    private val preferencesRepository: AppPreferencesRepository,
    private val timeSource: RewardTimeSource,
) : RewardUnlockStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _accessState = MutableStateFlow(RewardAccessState())
    private val pendingPermanentUnlocks = mutableSetOf<String>()
    private val pendingPassExpiries = mutableMapOf<String, Long>()
    private var persistedPermanentUnlocks = emptySet<String>()
    private var persistedPassExpiries = emptyMap<String, Long>()
    private var expiryJob: Job? = null

    override val accessState: StateFlow<RewardAccessState> = _accessState.asStateFlow()

    init {
        scope.launch {
            preferencesRepository.observePreferences().collect { preferences ->
                persistedPermanentUnlocks = preferences.rewardUnlockedItemIds
                persistedPassExpiries = preferences.rewardPassExpiries
                pendingPermanentUnlocks.removeAll(persistedPermanentUnlocks)
                pendingPassExpiries.entries.removeAll { (key, expiry) ->
                    (persistedPassExpiries[key] ?: Long.MIN_VALUE) >= expiry
                }
                publishAccessState()
            }
        }
    }

    override fun grant(content: RewardContentKey) {
        when (content) {
            is RewardContentKey.Artwork -> grantPermanent(content.legacyItemKey)
            is RewardContentKey.Lesson -> grantPass(
                key = content.passKey,
                durationMillis = LESSON_PASS_DURATION_MILLIS,
            )

            is RewardContentKey.Emoji -> grantPass(
                key = RewardAccessState.EMOJI_MIX_PASS_KEY,
                durationMillis = EMOJI_PASS_DURATION_MILLIS,
            )
        }
    }

    private fun grantPermanent(key: String) {
        if (key in persistedPermanentUnlocks || key in pendingPermanentUnlocks) return
        pendingPermanentUnlocks += key
        publishAccessState()
        val next = persistedPermanentUnlocks + pendingPermanentUnlocks
        scope.launch {
            if (preferencesRepository.setRewardUnlockedItemIds(next) is AppResult.Failure) {
                pendingPermanentUnlocks -= key
                publishAccessState()
            }
        }
    }

    private fun grantPass(key: String, durationMillis: Long) {
        val now = timeSource.currentTimeMillis()
        val currentExpiry = maxOf(
            persistedPassExpiries[key] ?: Long.MIN_VALUE,
            pendingPassExpiries[key] ?: Long.MIN_VALUE,
        )
        if (currentExpiry > now) return
        val expiry = now + durationMillis
        pendingPassExpiries[key] = expiry
        publishAccessState()
        val next = persistedPassExpiries + pendingPassExpiries
        scope.launch {
            if (preferencesRepository.setRewardPassExpiries(next) is AppResult.Failure &&
                pendingPassExpiries[key] == expiry
            ) {
                pendingPassExpiries -= key
                publishAccessState()
            }
        }
    }

    private fun publishAccessState() {
        val now = timeSource.currentTimeMillis()
        val activePasses = (persistedPassExpiries + pendingPassExpiries)
            .filterValues { it > now }
        _accessState.value = RewardAccessState(
            permanentItemKeys = persistedPermanentUnlocks + pendingPermanentUnlocks,
            activePassExpiries = activePasses,
        )
        scheduleNextExpiry(activePasses.values.minOrNull(), now)
    }

    private fun scheduleNextExpiry(nextExpiry: Long?, now: Long) {
        expiryJob?.cancel()
        expiryJob = nextExpiry?.let { expiry ->
            scope.launch {
                delay((expiry - now).coerceAtLeast(1L))
                expiryJob = null
                publishAccessState()
                removeExpiredPersistedPasses()
            }
        }
    }

    private fun removeExpiredPersistedPasses() {
        val now = timeSource.currentTimeMillis()
        pendingPassExpiries.entries.removeAll { it.value <= now }
        val activePersisted = persistedPassExpiries.filterValues { it > now }
        if (activePersisted.size == persistedPassExpiries.size) return
        persistedPassExpiries = activePersisted
        scope.launch {
            preferencesRepository.setRewardPassExpiries(activePersisted + pendingPassExpiries)
        }
    }

    internal fun close() {
        expiryJob?.cancel()
        scope.cancel()
    }

    companion object {
        const val LESSON_PASS_DURATION_MILLIS = 30L * 60L * 1_000L
        const val EMOJI_PASS_DURATION_MILLIS = 15L * 60L * 1_000L
    }
}
