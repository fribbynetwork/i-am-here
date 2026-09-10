package net.fribbynetwork.iamhere.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.content.res.Configuration
import android.provider.Settings
import net.fribbynetwork.iamhere.data.LangMode
import java.util.Locale

data class BatteryInfo(val percent: Int?, val charging: Boolean?)

/** Lettura sincrona dello stato batteria tramite l'intent "appiccicoso". */
fun Context.batteryInfo(): BatteryInfo {
    val i: Intent? = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    if (i == null) return BatteryInfo(null, null)
    val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    val pct = if (level >= 0 && scale > 0) (level * 100) / scale else null
    val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL
    return BatteryInfo(pct, charging)
}

fun Context.networkType(): String {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return "unknown"
    val n = cm.activeNetwork ?: return "none"
    val c = cm.getNetworkCapabilities(n) ?: return "none"
    if (!c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return "none"
    return when {
        c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
        c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
        c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
        else -> "other"
    }
}

fun Context.hasNetwork(): Boolean {
    val t = networkType()
    return t != "none" && t != "unknown"
}

fun Context.isIgnoringBatteryOptimizations(): Boolean {
    val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return pm.isIgnoringBatteryOptimizations(packageName)
}

/**
 * Chiede l'esenzione con una finestra secca "consenti a questa app di
 * funzionare sempre in background": un tocco invece di far cercare l'app
 * dentro un elenco. Alcune ROM non espongono questa activity, per questo
 * chi la usa deve prevedere il ripiego qui sotto.
 */
@SuppressLint("BatteryLife")
fun Context.batteryOptimizationRequestIntent(): Intent =
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        .setData(android.net.Uri.fromParts("package", packageName, null))

/** Ripiego: l'elenco di sistema di tutte le app. */
fun Context.batteryOptimizationIntent(): Intent =
    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

fun Context.appSettingsIntent(): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(
        android.net.Uri.fromParts("package", packageName, null)
    )

fun sdkAtLeast(v: Int) = Build.VERSION.SDK_INT >= v

/**
 * Contesto con la lingua scelta dall'utente. SYSTEM lascia decidere al
 * telefono; le altre forzano la risorsa, indipendentemente dalle
 * impostazioni di sistema.
 */
fun Context.withLocale(mode: LangMode): Context {
    val locale = when (mode) {
        LangMode.SYSTEM -> return this
        LangMode.EN -> Locale("en")
        LangMode.IT -> Locale("it")
    }
    val conf = Configuration(resources.configuration)
    conf.setLocale(locale)
    return createConfigurationContext(conf)
}
