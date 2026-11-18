package tv.music.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import tv.music.player.ui.TvMusicApp

class MainActivity : ComponentActivity() {

    var onBackLongPress: (() -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private var backKeyHeld = false
    private var backLongFired = false

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val backLongPressRunnable = Runnable {
        if (backKeyHeld) {
            backLongFired = true
            onBackLongPress?.invoke()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        setContent { TvMusicApp() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStart() {
        super.onStart()
        (application as TvMusicApplication).container.playback.setAppForeground(true)
    }

    override fun onStop() {
        (application as TvMusicApplication).container.playback.setAppForeground(false)
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    when {
                        event.isLongPress || event.repeatCount > 0 -> {
                            handler.removeCallbacks(backLongPressRunnable)
                            if (!backLongFired) {
                                backLongFired = true
                                onBackLongPress?.invoke()
                            }
                        }
                        else -> {
                            backKeyHeld = true
                            backLongFired = false
                            handler.postDelayed(backLongPressRunnable, LONG_PRESS_MS)
                        }
                    }
                }
                KeyEvent.ACTION_UP -> {
                    backKeyHeld = false
                    handler.removeCallbacks(backLongPressRunnable)
                    if (backLongFired) {
                        backLongFired = false
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        handler.removeCallbacks(backLongPressRunnable)
        super.onDestroy()
    }

    private companion object {
        const val LONG_PRESS_MS = 700L
    }
}
