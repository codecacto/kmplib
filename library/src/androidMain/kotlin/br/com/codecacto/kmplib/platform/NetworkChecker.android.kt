package br.com.codecacto.kmplib.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import br.com.codecacto.kmplib.core.context.AndroidAppContext

class AndroidNetworkChecker : NetworkChecker {
    override fun isAvailable(): Boolean {
        val context = AndroidAppContext.get()
            ?: return false
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
