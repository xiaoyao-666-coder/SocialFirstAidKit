package com.socialfirstaidkit.core.data.personalexpression

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PersonalExpressionUsagePreferences(
    val messageAssistEnabled: Boolean = true,
    val socialCopyEnabled: Boolean = true
)

@Singleton
class PersonalExpressionUsageRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _preferences = MutableStateFlow(readPreferences())
    val preferences: StateFlow<PersonalExpressionUsagePreferences> = _preferences.asStateFlow()

    fun isMessageAssistEnabled(): Boolean = readPreferences().messageAssistEnabled

    fun isSocialCopyEnabled(): Boolean = readPreferences().socialCopyEnabled

    fun setMessageAssistEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MESSAGE_ASSIST, enabled).apply()
        refresh()
    }

    fun setSocialCopyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOCIAL_COPY, enabled).apply()
        refresh()
    }

    private fun refresh() {
        _preferences.value = readPreferences()
    }

    private fun readPreferences(): PersonalExpressionUsagePreferences {
        return PersonalExpressionUsagePreferences(
            messageAssistEnabled = prefs.getBoolean(KEY_MESSAGE_ASSIST, true),
            socialCopyEnabled = prefs.getBoolean(KEY_SOCIAL_COPY, true)
        )
    }

    private companion object {
        const val PREFS_NAME = "personal_expression_usage_preferences"
        const val KEY_MESSAGE_ASSIST = "message_assist_enabled"
        const val KEY_SOCIAL_COPY = "social_copy_enabled"
    }
}
