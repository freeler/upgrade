package com.freeler.upgrade.utils

import android.content.Context

/**
 * Author: xuzeyang
 * Date: 2020-04-07
 */
object DownloadCache {

    private const val FILE_NAME = "download_cache"

    fun saveProgress(context: Context, url: String, size: Long) {
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).edit().putLong(url, size).apply()
    }

    fun getProgress(context: Context, url: String): Long {
        return context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).getLong(url, 0)
    }

}