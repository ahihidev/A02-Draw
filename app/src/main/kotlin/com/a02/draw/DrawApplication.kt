package com.a02.draw

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.a02.draw.ads.KiroSdkInitializer
import com.a02.draw.ads.app.AppBackgroundTracker
import com.a02.draw.connectivity.NoInternetGatekeeper
import com.a02.draw.core.ui.billing.PremiumBillingController
import com.a02.draw.domain.repository.AppPreferencesRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltAndroidApp
class DrawApplication : Application() {
    @Inject lateinit var preferencesRepository: AppPreferencesRepository
    @Inject
    lateinit var appBackgroundTracker: AppBackgroundTracker
    @Inject
    lateinit var premiumBillingController: PremiumBillingController
    @Inject
    internal lateinit var noInternetGatekeeper: NoInternetGatekeeper

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        // Force registration before SplashActivity starts so in-app Activity transitions are
        // never misclassified as a real process background/foreground cycle.
        appBackgroundTracker.ensureRegistered()
        noInternetGatekeeper.ensureRegistered()
        KiroSdkInitializer.initialize(this)
        // This is the single process-start entitlement check. Splash waits for the result with
        // a bounded, fail-closed timeout so Billing can never strand the user on startup.
        premiumBillingController.synchronizeEntitlement()
        applicationScope.launch {
            preferencesRepository.observePreferences()
                .map { it.languageTag.orEmpty() }
                .distinctUntilChanged()
                .collect(::applyLanguage)
        }
    }

    private suspend fun applyLanguage(languageTag: String) =
        withContext(Dispatchers.Main.immediate) {
            val locales = LocaleListCompat.forLanguageTags(languageTag)
            if (AppCompatDelegate.getApplicationLocales() != locales) {
                AppCompatDelegate.setApplicationLocales(locales)
            }
        }
}
