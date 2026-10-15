package com.safekey.authenticator.network

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.safekey.authenticator.R

class LocalNetworkPermissionException : Exception()

object LocalNetworkAccess {
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    fun isGranted(context: Context): Boolean =
        !LocalNetworkPolicy.permissionRequired(Build.VERSION.SDK_INT, context.applicationInfo.targetSdkVersion) ||
            ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Call off the main thread: ordinary hostnames can use split-horizon DNS. */
    fun checkWebDav(context: Context, baseUrl: String) {
        if (!isGranted(context) && LocalNetworkPolicy.webDavUsesLocalNetwork(baseUrl)) {
            throw LocalNetworkPermissionException()
        }
    }

    fun deniedMessage(context: Context): String = context.getString(R.string.local_network_permission_desc)
}