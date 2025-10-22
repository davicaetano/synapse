package com.synapse.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors network connectivity using Android's ConnectivityManager.
 * 
 * Provides a real-time StateFlow<Boolean> that indicates whether the device
 * has an active network connection (WiFi, cellular, etc.).
 * 
 * Usage:
 * - Call startMonitoring() when app starts (e.g., in MainActivity.onStart())
 * - Call stopMonitoring() when app stops (e.g., in MainActivity.onStop())
 * - Observe isConnected flow to react to connection changes
 */
@Singleton
class NetworkConnectivityMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private val _isConnected = MutableStateFlow(checkInitialConnection())
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    // Track active networks to handle multiple connections (WiFi + cellular)
    // Using Collections.synchronizedSet for thread-safety since callbacks can come from different threads
    private val activeNetworks = java.util.Collections.synchronizedSet(mutableSetOf<Network>())
    
    /**
     * Check if there's an active network connection right now.
     */
    private fun checkInitialConnection(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    /**
     * Check if we have ANY valid network connection by checking all active networks.
     */
    private fun hasAnyValidNetwork(): Boolean {
        synchronized(activeNetworks) {
            if (activeNetworks.isEmpty()) {
                return false
            }
            return activeNetworks.any { network ->
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
        }
    }
    
    /**
     * Start monitoring network connectivity changes.
     * Call this in MainActivity.onStart() or when you need to start tracking.
     */
    fun startMonitoring() {
        if (networkCallback != null) {
            Log.d(TAG, "Already monitoring network connectivity")
            return
        }
        
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
        
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                activeNetworks.add(network)
                // Network is available, but we need to wait for capabilities to confirm it's usable
                updateConnectionState()
            }
            
            override fun onLost(network: Network) {
                activeNetworks.remove(network)
                // Check if we still have other valid networks
                updateConnectionState()
            }
            
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                
                // Add to active networks if it has internet and is validated
                if (hasInternet && isValidated) {
                    activeNetworks.add(network)
                } else {
                    activeNetworks.remove(network)
                }
                
                updateConnectionState()
            }
            
            private fun updateConnectionState() {
                _isConnected.value = hasAnyValidNetwork()
            }
        }
        
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
        
        // Also update initial state
        _isConnected.value = checkInitialConnection()
    }
    
    /**
     * Stop monitoring network connectivity changes.
     * Call this in MainActivity.onStop() or when you're done tracking.
     */
    fun stopMonitoring() {
        networkCallback?.let { callback ->
            connectivityManager.unregisterNetworkCallback(callback)
            networkCallback = null
            synchronized(activeNetworks) {
                activeNetworks.clear()
            }
        }
    }
    
    companion object {
        private const val TAG = "NetworkMonitor"
    }
}

