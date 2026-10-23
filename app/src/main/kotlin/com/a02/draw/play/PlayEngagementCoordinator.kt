package com.a02.draw.play

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.core.content.edit
import com.a02.draw.BuildConfig
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayEngagementCoordinator @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(context)
    private val reviewManager: ReviewManager = ReviewManagerFactory.create(context)
    private val _isFlexibleUpdateDownloaded = MutableStateFlow(false)

    val isFlexibleUpdateDownloaded: StateFlow<Boolean> =
        _isFlexibleUpdateDownloaded.asStateFlow()

    private var didRecordSession = false
    private var didCheckUpdate = false
    private var didShowUpdatePrompt = false
    private var didAttemptReview = false
    private var isPromptInFlight = false
    private var isInstallListenerRegistered = false

    private val installStateListener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADED -> {
                isPromptInFlight = false
                _isFlexibleUpdateDownloaded.value = true
            }

            InstallStatus.CANCELED,
            InstallStatus.FAILED,
                -> {
                isPromptInFlight = false
                rememberUpdateDismissed()
                unregisterInstallListener()
            }

            InstallStatus.INSTALLED -> {
                isPromptInFlight = false
                _isFlexibleUpdateDownloaded.value = false
                unregisterInstallListener()
            }

            else -> Unit
        }
    }

    fun recordMainSession() {
        if (didRecordSession) return
        didRecordSession = true
        val now = System.currentTimeMillis()
        preferences.edit {
            if (preferences.getLong(KEY_FIRST_MAIN_SESSION_AT, 0L) <= 0L) {
                putLong(KEY_FIRST_MAIN_SESSION_AT, now)
            }
            putInt(KEY_MAIN_SESSION_COUNT, preferences.getInt(KEY_MAIN_SESSION_COUNT, 0) + 1)
        }
    }

    fun tryShowAutomaticPrompt(
        activity: Activity,
        updateLauncher: ActivityResultLauncher<IntentSenderRequest>,
        canLaunch: () -> Boolean,
        beforeLaunch: () -> Unit,
    ) {
        if (
            activity.isFinishing ||
            activity.isDestroyed ||
            !canLaunch() ||
            isPromptInFlight ||
            _isFlexibleUpdateDownloaded.value
        ) {
            return
        }
        if (!didCheckUpdate) {
            didCheckUpdate = true
            checkForFlexibleUpdate(activity, updateLauncher, canLaunch, beforeLaunch)
        } else if (!didShowUpdatePrompt) {
            requestReviewIfEligible(activity, canLaunch, beforeLaunch)
        }
    }

    fun onUpdateFlowResult(resultCode: Int) {
        isPromptInFlight = false
        if (resultCode != Activity.RESULT_OK) {
            rememberUpdateDismissed()
            unregisterInstallListener()
        }
    }

    fun completeFlexibleUpdate() {
        if (!_isFlexibleUpdateDownloaded.value) return
        appUpdateManager.completeUpdate()
    }

    private fun checkForFlexibleUpdate(
        activity: Activity,
        updateLauncher: ActivityResultLauncher<IntentSenderRequest>,
        canLaunch: () -> Boolean,
        beforeLaunch: () -> Unit,
    ) {
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                if (info.installStatus() == InstallStatus.DOWNLOADED) {
                    _isFlexibleUpdateDownloaded.value = true
                    return@addOnSuccessListener
                }
                val canOffer = PlayPromptPolicy.canOfferUpdate(
                    nowMillis = System.currentTimeMillis(),
                    lastDismissedAtMillis = preferences.getLong(KEY_UPDATE_DISMISSED_AT, 0L),
                )
                val isAvailable =
                    info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                            info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                if (!isAvailable || !canOffer) {
                    requestReviewIfEligible(activity, canLaunch, beforeLaunch)
                    return@addOnSuccessListener
                }
                if (activity.isFinishing || activity.isDestroyed || !canLaunch()) {
                    didCheckUpdate = false
                    return@addOnSuccessListener
                }

                beforeLaunch()
                registerInstallListener()
                isPromptInFlight = true
                didShowUpdatePrompt = true
                val didStart = runCatching {
                    appUpdateManager.startUpdateFlowForResult(
                        info,
                        updateLauncher,
                        AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                    )
                }.onFailure { error ->
                    Log.w(TAG, "Could not start flexible update flow.", error)
                }.getOrDefault(false)
                if (!didStart) {
                    isPromptInFlight = false
                    rememberUpdateDismissed()
                    unregisterInstallListener()
                }
            }
            .addOnFailureListener { error ->
                Log.d(TAG, "Google Play update check unavailable.", error)
                requestReviewIfEligible(activity, canLaunch, beforeLaunch)
            }
    }

    private fun requestReviewIfEligible(
        activity: Activity,
        canLaunch: () -> Boolean,
        beforeLaunch: () -> Unit,
    ) {
        if (didAttemptReview || didShowUpdatePrompt || isPromptInFlight) return
        val now = System.currentTimeMillis()
        val isEligible = PlayPromptPolicy.canRequestReview(
            nowMillis = now,
            sessionCount = preferences.getInt(KEY_MAIN_SESSION_COUNT, 0),
            firstMainSessionAtMillis = preferences.getLong(KEY_FIRST_MAIN_SESSION_AT, 0L),
            lastReviewRequestAtMillis = preferences.getLong(KEY_REVIEW_REQUESTED_AT, 0L),
            lastReviewVersionCode = preferences.getInt(KEY_REVIEW_VERSION_CODE, 0),
            currentVersionCode = BuildConfig.VERSION_CODE,
        )
        if (!isEligible) return

        didAttemptReview = true
        isPromptInFlight = true
        reviewManager.requestReviewFlow().addOnCompleteListener { request ->
            if (!request.isSuccessful || activity.isFinishing || activity.isDestroyed || !canLaunch()) {
                isPromptInFlight = false
                return@addOnCompleteListener
            }
            beforeLaunch()
            reviewManager.launchReviewFlow(activity, request.result).addOnCompleteListener {
                isPromptInFlight = false
                preferences.edit {
                    putLong(KEY_REVIEW_REQUESTED_AT, System.currentTimeMillis())
                    putInt(KEY_REVIEW_VERSION_CODE, BuildConfig.VERSION_CODE)
                }
            }
        }
    }

    private fun registerInstallListener() {
        if (isInstallListenerRegistered) return
        appUpdateManager.registerListener(installStateListener)
        isInstallListenerRegistered = true
    }

    private fun unregisterInstallListener() {
        if (!isInstallListenerRegistered) return
        appUpdateManager.unregisterListener(installStateListener)
        isInstallListenerRegistered = false
    }

    private fun rememberUpdateDismissed() {
        preferences.edit { putLong(KEY_UPDATE_DISMISSED_AT, System.currentTimeMillis()) }
    }

    private companion object {
        const val TAG = "PlayEngagement"
        const val PREFERENCES_NAME = "play_engagement_prompts"
        const val KEY_FIRST_MAIN_SESSION_AT = "first_main_session_at"
        const val KEY_MAIN_SESSION_COUNT = "main_session_count"
        const val KEY_UPDATE_DISMISSED_AT = "update_dismissed_at"
        const val KEY_REVIEW_REQUESTED_AT = "review_requested_at"
        const val KEY_REVIEW_VERSION_CODE = "review_version_code"
    }
}
