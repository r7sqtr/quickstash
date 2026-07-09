package com.example.quickstash

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.quickstash.ui.navigation.AppNavHost
import com.example.quickstash.ui.theme.QuickStashTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val lockState = mutableStateOf<Boolean?>(null)
    private var lockEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )

        lifecycleScope.launch {
            val container = (application as QuickStashApp).container
            lockEnabled = container.settingsStore.appLockEnabled()
            lockState.value = lockEnabled
            if (lockEnabled) {
                showUnlockPrompt()
            }
        }

        setContent {
            QuickStashTheme {
                val locked by lockState
                when (locked) {
                    null -> Surface(modifier = Modifier.fillMaxSize()) {}
                    true -> LockScreen(
                        canAuthenticate = canAuthenticate(),
                        onUnlock = ::showUnlockPrompt,
                        onOpenSecuritySettings = ::openSecuritySettings,
                    )
                    false -> AppNavHost()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            val container = (application as QuickStashApp).container
            lockEnabled = container.settingsStore.appLockEnabled()
        }
    }

    override fun onStop() {
        super.onStop()
        if (lockEnabled && !isChangingConfigurations) {
            lockState.value = true
        }
    }

    private fun openSecuritySettings() {
        runCatching {
            startActivity(android.content.Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS))
        }
    }

    private fun canAuthenticate(): Boolean {
        val result = BiometricManager.from(this).canAuthenticate(AUTHENTICATORS)
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun showUnlockPrompt() {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult,
                ) {
                    lockState.value = false
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("QuickStash のロック解除")
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build(),
        )
    }

    companion object {
        private const val AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}

@androidx.compose.runtime.Composable
private fun LockScreen(
    canAuthenticate: Boolean,
    onUnlock: () -> Unit,
    onOpenSecuritySettings: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF141F35), Color(0xFF090F1E)),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(160.dp),
            )
            Text(
                text = "QuickStash",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.padding(top = 20.dp),
            )
            Spacer(modifier = Modifier.height(56.dp))
            val pulseTransition = rememberInfiniteTransition(label = "lockPulse")
            val pulse by pulseTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2400, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "pulseProgress",
            )
            Box(contentAlignment = Alignment.Center) {
                PulseRing(progress = pulse)
                PulseRing(progress = (pulse + 0.5f) % 1f)
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(
                            width = 1.dp,
                            color = Color(0x33FFCA4A),
                            shape = CircleShape,
                        )
                        .clickable(enabled = canAuthenticate, onClick = onUnlock),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lock),
                        contentDescription = "ロック解除",
                        tint = Color(0xFFFFCA4A),
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            if (canAuthenticate) {
                Text(
                    text = "ロックされています",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 20.dp),
                )
                Text(
                    text = "タップして解除",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 6.dp),
                )
            } else {
                Text(
                    text = "この端末では解除できません",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 20.dp),
                )
                Text(
                    text = "端末に画面ロック（PIN・パターン・生体）を設定してください",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = onOpenSecuritySettings) {
                    Text("端末のセキュリティ設定を開く")
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun PulseRing(progress: Float) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .graphicsLayer {
                val scale = 1f + progress * 0.9f
                scaleX = scale
                scaleY = scale
                alpha = (1f - progress) * 0.35f
            }
            .border(
                width = 1.5.dp,
                color = Color(0xFFFFCA4A),
                shape = CircleShape,
            ),
    )
}
