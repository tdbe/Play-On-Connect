package com.tdbe.playonconnect.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri

object WebApps {

    data class AppOption(val packageName: String, val label: String)

    fun ensureScheme(url: String): Uri? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        return runCatching { Uri.parse(withScheme) }.getOrNull()
    }

    private fun viewIntent(uri: Uri) =
        Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)

    fun browserApps(context: Context): List<AppOption> =
        queryApps(context, Uri.parse("https://www.duckduckgo.com"))

    fun appsForUrl(context: Context, url: String): List<AppOption> {
        val uri = ensureScheme(url) ?: return emptyList()
        return queryApps(context, uri)
    }

    private fun queryApps(context: Context, uri: Uri): List<AppOption> = runCatching {
        context.packageManager.queryIntentActivities(viewIntent(uri), 0)
            .asSequence()
            .mapNotNull { ri ->
                val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                AppOption(pkg, appLabel(context, pkg))
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }.getOrDefault(emptyList())

    fun appLabel(context: Context, packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    /** True when the OS has a usable default for web links (explicitly preferred or only one browser). */
    fun hasDefaultBrowser(context: Context): Boolean {
        val candidates = browserApps(context)
        if (candidates.isEmpty()) return false
        if (candidates.size == 1) return true
        val filters = mutableListOf<IntentFilter>()
        val activities = mutableListOf<ComponentName>()
        runCatching { context.packageManager.getPreferredActivities(filters, activities, null) }
        val preferredPkgs = activities.map { it.packageName }.toSet()
        return candidates.any { it.packageName in preferredPkgs }
    }

    /** Best-effort: package of a dedicated (non-generic-browser) default handler for this URL. */
    fun dedicatedAppPackage(context: Context, url: String): String? {
        val uri = ensureScheme(url) ?: return null
        val resolved = runCatching {
            context.packageManager.resolveActivity(viewIntent(uri), PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrNull()
        val pkg = resolved?.activityInfo?.packageName ?: return null
        if (pkg == context.packageName) return null
        val browserDefault = runCatching {
            context.packageManager
                .resolveActivity(viewIntent(Uri.parse("https://www.duckduckgo.com")), PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
        }.getOrNull()
        return if (browserDefault != null && pkg == browserDefault) null else pkg
    }

    fun open(context: Context, url: String, packageName: String?): Boolean = try {
        val uri = ensureScheme(url) ?: return false
        val intent = viewIntent(uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (packageName != null) setPackage(packageName)
        }
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }
}
