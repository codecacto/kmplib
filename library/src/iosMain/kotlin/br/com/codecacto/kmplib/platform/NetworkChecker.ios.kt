package br.com.codecacto.kmplib.platform

import platform.SystemConfiguration.SCNetworkReachabilityCreateWithName
import platform.SystemConfiguration.SCNetworkReachabilityGetFlags
import platform.SystemConfiguration.SCNetworkReachabilityFlags
import platform.SystemConfiguration.SCNetworkReachabilityFlagsVar
import platform.SystemConfiguration.kSCNetworkReachabilityFlagsReachable
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value

class IosNetworkChecker : NetworkChecker {
    @OptIn(ExperimentalForeignApi::class)
    override fun isAvailable(): Boolean {
        return memScoped {
            val reachability = SCNetworkReachabilityCreateWithName(null, "www.google.com")
                ?: return@memScoped false
            val flags = alloc<SCNetworkReachabilityFlagsVar>()
            if (!SCNetworkReachabilityGetFlags(reachability, flags.ptr)) {
                return@memScoped false
            }
            val flagsValue: SCNetworkReachabilityFlags = flags.value
            (flagsValue and kSCNetworkReachabilityFlagsReachable) != 0u
        }
    }
}
