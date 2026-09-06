package com.shohan.bokeya.ui.screens.lock

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.shohan.bokeya.R
import com.shohan.bokeya.core.money.MoneyFormatter
import com.shohan.bokeya.security.PinManager
import kotlinx.coroutines.launch

/**
 * PIN entry with an optional biometric shortcut.
 *
 * A custom keypad rather than a text field: it keeps the digits large enough to
 * hit reliably, never opens the system keyboard over the dots, and lets a wrong
 * PIN shake the row instead of showing a form error.
 */
@Composable
fun LockScreen(
    biometricEnabled: Boolean,
    onPinEntered: (String) -> Boolean,
    onUnlocked: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val scope = rememberCoroutineScope()

    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }

    val canUseBiometric = remember(biometricEnabled, activity) {
        biometricEnabled && activity != null &&
            BiometricManager.from(context).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK,
            ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun promptBiometric() {
        val host = activity ?: return
        val prompt = BiometricPrompt(
            host,
            androidx.core.content.ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onUnlocked()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.lock_biometric_title))
            .setSubtitle(context.getString(R.string.lock_biometric_subtitle))
            .setNegativeButtonText(context.getString(R.string.lock_biometric_cancel))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
        runCatching { prompt.authenticate(info) }
    }

    // Offer the fingerprint immediately — that is why the user turned it on.
    LaunchedEffect(canUseBiometric) {
        if (canUseBiometric) promptBiometric()
    }

    fun submit(candidate: String) {
        if (onPinEntered(candidate)) {
            onUnlocked()
        } else {
            error = true
            pin = ""
            scope.launch {
                shake.snapTo(0f)
                repeat(3) {
                    shake.animateTo(14f, androidx.compose.animation.core.tween(50))
                    shake.animateTo(-14f, androidx.compose.animation.core.tween(50))
                }
                shake.animateTo(0f, androidx.compose.animation.core.tween(50))
            }
        }
    }

    fun append(digit: String) {
        if (pin.length >= PinManager.MAX_PIN_LENGTH) return
        error = false
        val next = pin + digit
        pin = next
        if (next.length >= PinManager.MIN_PIN_LENGTH) {
            // Try as soon as it *could* be right; the user never taps "OK".
            if (onPinEntered(next)) {
                onUnlocked()
            } else if (next.length == PinManager.MAX_PIN_LENGTH) {
                submit(next)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
        }

        Spacer(Modifier.height(18.dp))

        Text(
            text = stringResource(R.string.lock_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(if (error) R.string.lock_wrong else R.string.lock_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = if (error) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        Spacer(Modifier.height(26.dp))

        Row(
            modifier = Modifier.graphicsLayer { translationX = shake.value },
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            repeat(PinManager.MAX_PIN_LENGTH) { index ->
                val filled = index < pin.length
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                error -> MaterialTheme.colorScheme.error
                                filled -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.outlineVariant
                            },
                        ),
                )
            }
        }

        Spacer(Modifier.height(34.dp))

        Keypad(
            onDigit = ::append,
            onBackspace = {
                error = false
                pin = pin.dropLast(1)
            },
        )

        Spacer(Modifier.height(10.dp))

        if (canUseBiometric) {
            TextButton(onClick = { promptBiometric() }) {
                Icon(Icons.Outlined.Fingerprint, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.lock_use_biometric))
            }
        }

        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun Keypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "<"),
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { key ->
                    when (key) {
                        "" -> Spacer(Modifier.size(70.dp))
                        "<" -> KeypadKey(
                            contentDescription = stringResource(R.string.lock_delete_digit),
                            onClick = onBackspace,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Backspace,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        else -> KeypadKey(
                            contentDescription = key,
                            onClick = { onDigit(key) },
                        ) {
                            Text(
                                text = MoneyFormatter.formatNumber(key.toInt(), true),
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadKey(
    contentDescription: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(70.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .clearAndSetSemantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * Walks the context chain to the hosting activity.
 *
 * `BiometricPrompt` needs a `FragmentActivity`; the composition only hands us a
 * `Context`, which under a dialog or view host may be a wrapper several levels
 * deep.
 */
private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
