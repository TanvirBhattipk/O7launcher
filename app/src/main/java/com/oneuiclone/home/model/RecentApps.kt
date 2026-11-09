package com.oneuiclone.home.model

import android.content.Context

/** The apps opened from search, latest first: what search shows before anything is typed. */
class RecentApps(context: Context) {
    private val sp = context.getSharedPreferences("recent_apps", Context.MODE_PRIVATE)

    private fun stored(): List<String> = (sp.getString("keys", "") ?: "").split('\n').filter { it.isNotEmpty() }

    fun add(key: AppKey) {
        val k = key.flatten()
        val list = listOf(k) + stored().filter { it != k }
        sp.edit().putString("keys", list.take(MAX).joinToString("\n")).apply()
    }

    /** The recent apps that are still installed, latest first. */
    fun resolve(apps: List<AppInfo>): List<AppInfo> =
        stored().mapNotNull { s -> apps.firstOrNull { it.key.flatten() == s } }

    private companion object { const val MAX = 12 }
}
