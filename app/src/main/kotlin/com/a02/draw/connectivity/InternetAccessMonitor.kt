package com.a02.draw.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.a02.draw.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection

internal enum class InternetAccessState {
    CHECKING,
    ONLINE,
    OFFLINE,
}

@Singleton
internal class InternetAccessMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val probeMutex = Mutex()
    private val _state = MutableStateFlow(InternetAccessState.CHECKING)
    private val _hasInternetAccess = MutableStateFlow(false)
    private var periodicCheckJob: Job? = null

    val state: StateFlow<InternetAccessState> = _state.asStateFlow()
    val hasInternetAccess: StateFlow<Boolean> = _hasInternetAccess.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = retryNow()
        override fun onLost(network: Network) = retryNow()
        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) = retryNow()
    }

    init {
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        retryNow()
    }

    fun setAppForeground(isForeground: Boolean) {
        if (!isForeground) {
            periodicCheckJob?.cancel()
            periodicCheckJob = null
            return
        }
        if (periodicCheckJob?.isActive == true) return
        periodicCheckJob = scope.launch {
            while (isActive) {
                checkInternetAccess()
                delay(
                    if (_hasInternetAccess.value) {
                        ONLINE_RECHECK_MILLIS
                    } else {
                        OFFLINE_RETRY_MILLIS
                    },
                )
            }
        }
    }

    fun retryNow() {
        scope.launch { checkInternetAccess() }
    }

    private suspend fun checkInternetAccess() = probeMutex.withLock {
        if (!_hasInternetAccess.value) updateState(InternetAccessState.CHECKING)
        val network = connectivityManager.activeNetwork
        val capabilities = network?.let(connectivityManager::getNetworkCapabilities)
        val isSystemValidated = capabilities
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        if (network == null || !isSystemValidated) {
            updateState(InternetAccessState.OFFLINE)
            return@withLock
        }

        val probeSucceeded = runCatching { probeGoogle(network) }.getOrDefault(false)
        updateState(
            if (probeSucceeded) InternetAccessState.ONLINE else InternetAccessState.OFFLINE,
        )
    }

    private fun probeGoogle(network: Network): Boolean {
        val connection = network.openConnection(
            URL(context.getString(R.string.internet_probe_url)),
        ) as HttpsURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = PROBE_TIMEOUT_MILLIS
            connection.readTimeout = PROBE_TIMEOUT_MILLIS
            connection.setRequestProperty("Cache-Control", "no-cache")
            connection.responseCode == HttpsURLConnection.HTTP_NO_CONTENT
        } finally {
            connection.disconnect()
        }
    }

    private fun updateState(state: InternetAccessState) {
        _state.value = state
        _hasInternetAccess.value = state == InternetAccessState.ONLINE
    }

    private companion object {
        const val PROBE_TIMEOUT_MILLIS = 3_000
        const val OFFLINE_RETRY_MILLIS = 3_000L
        const val ONLINE_RECHECK_MILLIS = 15_000L
    }
}
