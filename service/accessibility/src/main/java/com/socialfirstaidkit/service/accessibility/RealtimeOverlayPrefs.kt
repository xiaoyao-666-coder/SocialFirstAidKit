package com.socialfirstaidkit.service.accessibility

import android.content.Context

object RealtimeOverlayPrefs {
    private const val PREFS_NAME = "realtime_overlay_settings"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_MANUAL_START_REQUESTED = "manual_start_requested"
    private const val KEY_BOUND_CONTACT_ID = "bound_contact_id"
    private const val KEY_BOUND_SESSION_ID = "bound_session_id"

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putBoolean(KEY_MANUAL_START_REQUESTED, enabled)
            .apply {
                if (!enabled) {
                    remove(KEY_BOUND_CONTACT_ID)
                    remove(KEY_BOUND_SESSION_ID)
                }
            }
            .apply()
    }

    fun isManualStartRequested(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_MANUAL_START_REQUESTED, false)
    }

    fun setBoundContext(context: Context, contactId: String?, sessionId: String?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .apply {
                if (contactId.isNullOrBlank()) {
                    remove(KEY_BOUND_CONTACT_ID)
                } else {
                    putString(KEY_BOUND_CONTACT_ID, contactId)
                }
                if (sessionId.isNullOrBlank()) {
                    remove(KEY_BOUND_SESSION_ID)
                } else {
                    putString(KEY_BOUND_SESSION_ID, sessionId)
                }
            }
            .apply()
    }

    fun clearIfBoundTo(
        context: Context,
        contactId: String? = null,
        sessionIds: Collection<String> = emptyList()
    ): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val boundContactId = prefs.getString(KEY_BOUND_CONTACT_ID, null)
        val boundSessionId = prefs.getString(KEY_BOUND_SESSION_ID, null)
        val shouldClear = (!contactId.isNullOrBlank() && boundContactId == contactId) ||
            (!boundSessionId.isNullOrBlank() && boundSessionId in sessionIds)

        if (shouldClear) {
            prefs.edit()
                .putBoolean(KEY_ENABLED, false)
                .putBoolean(KEY_MANUAL_START_REQUESTED, false)
                .remove(KEY_BOUND_CONTACT_ID)
                .remove(KEY_BOUND_SESSION_ID)
                .apply()
        }
        return shouldClear
    }

    fun getBoundContactId(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BOUND_CONTACT_ID, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun getBoundSessionId(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BOUND_SESSION_ID, null)
            ?.takeIf { it.isNotBlank() }
    }
}
