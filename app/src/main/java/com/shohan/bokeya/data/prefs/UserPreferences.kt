package com.shohan.bokeya.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shohan.bokeya.domain.model.ThemeMode
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "bokeya_settings")

/** Everything the user can configure, plus first-run state. */
data class UserSettings(
    val userName: String = "",
    val currencySymbol: String = "৳",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = false,
    val useBengaliDigits: Boolean = true,
    val hideAmounts: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val reminderDaysBefore: Int = 1,
    val reminderSameDay: Boolean = true,
    val reminderOverdue: Boolean = true,
    /** Minutes from midnight; 540 = 9:00 AM. */
    val reminderTimeMinutes: Int = 540,
    val appLockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val onboardingComplete: Boolean = false,
    val setupComplete: Boolean = false,
    val lastBackupAt: Long = 0L,
) {
    val reminderHour: Int get() = reminderTimeMinutes / 60
    val reminderMinute: Int get() = reminderTimeMinutes % 60
}

/**
 * DataStore-backed settings.
 *
 * The PIN is intentionally **not** stored here in plain text — see
 * [com.shohan.bokeya.security.PinManager], which keeps only a salted hash.
 */
class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val USER_NAME = stringPreferencesKey("user_name")
        val CURRENCY = stringPreferencesKey("currency_symbol")
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val BENGALI_DIGITS = booleanPreferencesKey("bengali_digits")
        val HIDE_AMOUNTS = booleanPreferencesKey("hide_amounts")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val REMINDER_DAYS = intPreferencesKey("reminder_days_before")
        val REMINDER_SAME_DAY = booleanPreferencesKey("reminder_same_day")
        val REMINDER_OVERDUE = booleanPreferencesKey("reminder_overdue")
        val REMINDER_TIME = intPreferencesKey("reminder_time_minutes")
        val APP_LOCK = booleanPreferencesKey("app_lock_enabled")
        val BIOMETRIC = booleanPreferencesKey("biometric_enabled")
        val ONBOARDING = booleanPreferencesKey("onboarding_complete")
        val SETUP = booleanPreferencesKey("setup_complete")
        val LAST_BACKUP = longPreferencesKey("last_backup_at")
    }

    val settings: Flow<UserSettings> = context.dataStore.data
        // A corrupted preferences file must not brick the app; fall back to defaults.
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { prefs -> prefs.toSettings() }

    suspend fun current(): UserSettings = settings.first()

    private fun Preferences.toSettings() = UserSettings(
        userName = this[Keys.USER_NAME].orEmpty(),
        currencySymbol = this[Keys.CURRENCY] ?: "৳",
        themeMode = this[Keys.THEME]?.let { name ->
            runCatching { ThemeMode.valueOf(name) }.getOrDefault(ThemeMode.SYSTEM)
        } ?: ThemeMode.SYSTEM,
        useDynamicColor = this[Keys.DYNAMIC_COLOR] ?: false,
        useBengaliDigits = this[Keys.BENGALI_DIGITS] ?: true,
        hideAmounts = this[Keys.HIDE_AMOUNTS] ?: false,
        notificationsEnabled = this[Keys.NOTIFICATIONS] ?: true,
        reminderDaysBefore = this[Keys.REMINDER_DAYS] ?: 1,
        reminderSameDay = this[Keys.REMINDER_SAME_DAY] ?: true,
        reminderOverdue = this[Keys.REMINDER_OVERDUE] ?: true,
        reminderTimeMinutes = this[Keys.REMINDER_TIME] ?: 540,
        appLockEnabled = this[Keys.APP_LOCK] ?: false,
        biometricEnabled = this[Keys.BIOMETRIC] ?: false,
        onboardingComplete = this[Keys.ONBOARDING] ?: false,
        setupComplete = this[Keys.SETUP] ?: false,
        lastBackupAt = this[Keys.LAST_BACKUP] ?: 0L,
    )

    suspend fun setUserName(value: String) = edit { it[Keys.USER_NAME] = value.trim() }

    suspend fun setCurrency(value: String) = edit { it[Keys.CURRENCY] = value }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) = edit { it[Keys.DYNAMIC_COLOR] = enabled }

    suspend fun setBengaliDigits(enabled: Boolean) = edit { it[Keys.BENGALI_DIGITS] = enabled }

    suspend fun setHideAmounts(enabled: Boolean) = edit { it[Keys.HIDE_AMOUNTS] = enabled }

    suspend fun setNotificationsEnabled(enabled: Boolean) = edit { it[Keys.NOTIFICATIONS] = enabled }

    suspend fun setReminderDaysBefore(days: Int) =
        edit { it[Keys.REMINDER_DAYS] = days.coerceIn(0, 30) }

    suspend fun setReminderSameDay(enabled: Boolean) = edit { it[Keys.REMINDER_SAME_DAY] = enabled }

    suspend fun setReminderOverdue(enabled: Boolean) = edit { it[Keys.REMINDER_OVERDUE] = enabled }

    suspend fun setReminderTime(minutes: Int) =
        edit { it[Keys.REMINDER_TIME] = minutes.coerceIn(0, 24 * 60 - 1) }

    suspend fun setAppLockEnabled(enabled: Boolean) = edit { it[Keys.APP_LOCK] = enabled }

    suspend fun setBiometricEnabled(enabled: Boolean) = edit { it[Keys.BIOMETRIC] = enabled }

    suspend fun setOnboardingComplete(complete: Boolean) = edit { it[Keys.ONBOARDING] = complete }

    suspend fun setSetupComplete(complete: Boolean) = edit { it[Keys.SETUP] = complete }

    suspend fun setLastBackupAt(timestamp: Long) = edit { it[Keys.LAST_BACKUP] = timestamp }

    /** Restores a settings snapshot from a backup file. */
    suspend fun applySettings(settings: UserSettings) = edit { prefs ->
        prefs[Keys.USER_NAME] = settings.userName
        prefs[Keys.CURRENCY] = settings.currencySymbol
        prefs[Keys.THEME] = settings.themeMode.name
        prefs[Keys.DYNAMIC_COLOR] = settings.useDynamicColor
        prefs[Keys.BENGALI_DIGITS] = settings.useBengaliDigits
        prefs[Keys.NOTIFICATIONS] = settings.notificationsEnabled
        prefs[Keys.REMINDER_DAYS] = settings.reminderDaysBefore
        prefs[Keys.REMINDER_SAME_DAY] = settings.reminderSameDay
        prefs[Keys.REMINDER_OVERDUE] = settings.reminderOverdue
        prefs[Keys.REMINDER_TIME] = settings.reminderTimeMinutes
        prefs[Keys.ONBOARDING] = true
        prefs[Keys.SETUP] = true
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
