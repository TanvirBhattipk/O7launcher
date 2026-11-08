package com.oneuiclone.home.model

import android.content.Context

/** Names the user gave apps (long-press › Rename), kept by app key; the app's own label is used for the rest. */
class AppNames(context: Context) {
    private val sp = context.getSharedPreferences("app_names", Context.MODE_PRIVATE)

    fun get(key: AppKey): String? = sp.getString(key.flatten(), null)

    /** [name] null or blank puts the app's own label back. */
    fun set(key: AppKey, name: String?) {
        sp.edit().apply {
            if (name.isNullOrBlank()) remove(key.flatten()) else putString(key.flatten(), name.trim())
        }.apply()
    }
}
