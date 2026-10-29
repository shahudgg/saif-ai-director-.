package com.jarvis.assistant

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * Hands the library an application Context at process start, so the automation engine can
 * be used from services / the accessibility service without a custom Application subclass
 * (the host app SAIF AI keeps its own Application setup).
 */
class AutomationInitProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        AutomationContext.set(context?.applicationContext)
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}

object AutomationContext {
    @Volatile
    private var appContext: android.content.Context? = null

    fun set(context: android.content.Context?) {
        if (context != null) appContext = context.applicationContext
    }

    fun get(): android.content.Context =
        appContext ?: throw IllegalStateException("SAIF AI AUTOMATION context is not ready yet")
}
