package com.shohan.bokeya.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shohan.bokeya.data.prefs.UserSettings
import com.shohan.bokeya.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * App-wide state: theme, first-run routing and the app-lock gate.
 *
 * Owned by the activity so a configuration change (or navigating between tabs)
 * never re-locks the app or re-runs onboarding.
 */
class AppViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<UserSettings> = container.preferences.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _startDestinationReady = MutableStateFlow(false)
    val startDestinationReady: StateFlow<Boolean> = _startDestinationReady.asStateFlow()

    /** True once the very first settings read completes, so Splash can hand off. */
    init {
        viewModelScope.launch {
            val current = container.preferences.current()
            // Nothing to unlock when the user never enabled a PIN.
            _isUnlocked.value = !(current.appLockEnabled && container.pinManager.hasPin)
            _startDestinationReady.value = true
        }
    }

    fun unlock() {
        _isUnlocked.value = true
    }

    /** Re-arms the lock when the app goes to the background. */
    fun lock() {
        val current = settings.value
        if (current.appLockEnabled && container.pinManager.hasPin) {
            _isUnlocked.value = false
        }
    }

    fun verifyPin(pin: String): Boolean {
        val ok = container.pinManager.verify(pin)
        if (ok) _isUnlocked.value = true
        return ok
    }

    val hasBiometric: Boolean
        get() = settings.value.biometricEnabled

    fun onOnboardingComplete() {
        viewModelScope.launch { container.preferences.setOnboardingComplete(true) }
    }
}
