package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.ui.components.FloatingVoiceOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

class LiveVoiceService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    
    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    
    private lateinit var engine: LiveVoiceSessionManager

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        engine = LiveVoiceSessionManager(this) { 
            stopSelf()
        }
        LiveVoiceManager.engine = engine
        
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "VOICE_CHANNEL")
            .setContentTitle("Live Voice Session")
            .setContentText("Tap to return to app")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now) // placeholder
            .build()
            
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, notification)
        }
        
        showOverlay()
        
        engine.start()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        
        serviceScope.launch {
            LiveVoiceManager.isAppInForeground.collect { inForeground ->
                if (inForeground) {
                    hideOverlay()
                } else {
                    showOverlay()
                }
            }
        }
    }
    
    private fun hideOverlay() {
        composeView?.let {
            try {
                if (it.isAttachedToWindow) {
                    windowManager.removeView(it)
                }
            } catch (e: Exception) {
                android.util.Log.e("LiveVoiceService", "Error removing overlay", e)
            }
        }
        composeView = null
    }
    
    @androidx.annotation.OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    private fun showOverlay() {
        if (composeView != null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
            android.util.Log.w("LiveVoiceService", "SYSTEM_ALERT_WINDOW not granted, skipping floating bubble")
            return
        }
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }
        
        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@LiveVoiceService)
            setViewTreeViewModelStoreOwner(this@LiveVoiceService)
            setViewTreeSavedStateRegistryOwner(this@LiveVoiceService)
            
            setContent {
                FloatingVoiceOverlay(
                    engine = engine,
                    onClose = { stopSelf() },
                    onOpenApp = {
                        val intent = Intent(this@LiveVoiceService, MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        startActivity(intent)
                    },
                    onDrag = { dx, dy ->
                        layoutParams.x += dx.toInt()
                        layoutParams.y += dy.toInt()
                        try {
                            windowManager.updateViewLayout(composeView, layoutParams)
                        } catch (e: Exception) {
                            android.util.Log.e("LiveVoiceService", "Error updating layout", e)
                        }
                    }
                )
            }
        }
        
        try {
            windowManager.addView(composeView, layoutParams)
        } catch (e: Exception) {
            android.util.Log.e("LiveVoiceService", "Failed to add overlay window", e)
            composeView = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "VOICE_CHANNEL",
                "Live Voice Session",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        engine.stop()
        LiveVoiceManager.engine = null
        LiveVoiceManager.isLiveModeActive.value = false
        serviceScope.cancel()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        
        composeView?.let {
            try {
                if (it.isAttachedToWindow) {
                    windowManager.removeView(it)
                }
            } catch (e: Exception) {
                android.util.Log.e("LiveVoiceService", "Error removing view in onDestroy", e)
            }
        }
        composeView = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
}
