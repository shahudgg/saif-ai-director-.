package com.jarvis.assistant.ui.settings

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.SaifAutomation
import com.jarvis.assistant.data.model.GeminiConstants
import com.jarvis.assistant.databinding.ActivityApiConnectionBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Status screen only. The API key / provider / model are managed in the SAIF AI Settings panel;
 * this screen shows what SAIF AI AUTOMATION is using and lets the user pick the Gemini Live model.
 */
class ApiConnectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityApiConnectionBinding
    private val preferences by lazy { JarvisApp.instance.preferences }
    private val testHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        com.jarvis.assistant.util.ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityApiConnectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        com.jarvis.assistant.util.ThemeManager.applyDynamicBackground(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun maskKey(key: String): String {
        if (key.isBlank()) return "not set"
        if (key.length <= 8) return "••••"
        return key.take(4) + "••••••••" + key.takeLast(4)
    }

    private fun refreshStatus() {
        val cfg = SaifAutomation.currentApi()
        binding.tvLinkedProvider.text = "Provider: ${cfg.provider.ifBlank { "not set" }}"
        binding.tvLinkedModel.text = "Live model: ${preferences.aiModel.removePrefix("models/")}"
        binding.tvLinkedKey.text = "API key: ${maskKey(cfg.apiKey)}"

        when (preferences.aiModel) {
            GeminiConstants.MODEL_3_8_LIVE -> binding.rbModelFlash20.isChecked = true
            GeminiConstants.MODEL_3_1_FLASH_AGENT -> binding.rbModelFlash15.isChecked = true
            else -> binding.rbModelFlashThinking.isChecked = true
        }

        when {
            !cfg.hasKey -> setStatus("NO API KEY • ADD IT IN SAIF AI SETTINGS", "#EAB308")
            !cfg.isGemini -> setStatus("GEMINI KEY NEEDED FOR LIVE VOICE", "#EF4444")
            else -> setStatus("KEY LINKED • READY", "#22C55E")
        }
    }

    private fun setStatus(text: String, colorHex: String) {
        binding.tvLiveStatus.text = text
        binding.viewStatusDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor(colorHex))
    }

    private fun setupListeners() {
        binding.btnOpenSaifSettings.setOnClickListener {
            SaifAutomation.openHostSettings()
        }

        binding.btnTestConnection.setOnClickListener {
            val cfg = SaifAutomation.currentApi()
            if (!cfg.hasKey) {
                Snackbar.make(binding.root, "SAIF AI Settings me pehle API key daalo", Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (!cfg.isGemini) {
                Snackbar.make(binding.root, "Live voice ke liye SAIF AI Settings me provider Gemini chuno", Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }
            testConnection(cfg.apiKey)
        }

        binding.btnSaveConnection.setOnClickListener { saveModel() }
    }

    private fun testConnection(apiKey: String) {
        binding.btnTestConnection.isEnabled = false
        binding.tvPingResult.text = "Testing ping..."
        binding.viewStatusDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#3B82F6"))

        lifecycleScope.launch {
            val startTime = System.currentTimeMillis()
            val result = withContext(Dispatchers.IO) {
                try {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
                    val request = Request.Builder().url(url).build()
                    testHttpClient.newCall(request).execute().use { response ->
                        val duration = System.currentTimeMillis() - startTime
                        if (response.isSuccessful) Pair(true, duration) else Pair(false, response.code.toLong())
                    }
                } catch (e: Exception) {
                    Pair(false, -1L)
                }
            }

            binding.btnTestConnection.isEnabled = true
            if (result.first) {
                binding.tvPingResult.text = "Ping: ${result.second}ms • Valid"
                setStatus("CONNECTED • HANDSHAKE OK", "#22C55E")
                Snackbar.make(binding.root, "Connection verified! Ping: ${result.second}ms", Snackbar.LENGTH_LONG).show()
            } else {
                val code = result.second
                binding.tvPingResult.text = if (code == -1L) "Network Timeout" else "HTTP $code Error"
                setStatus("FAILED • INVALID KEY OR NETWORK", "#EF4444")
                Snackbar.make(binding.root, "Connection failed. SAIF AI Settings me API key check karo.", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun saveModel() {
        val selectedModel = when (binding.rgModelGroup.checkedRadioButtonId) {
            binding.rbModelFlash20.id -> GeminiConstants.MODEL_3_8_LIVE
            binding.rbModelFlash15.id -> GeminiConstants.MODEL_3_1_FLASH_AGENT
            else -> GeminiConstants.MODEL_3_1_FLASH
        }
        preferences.aiModel = selectedModel
        SaifAutomation.onHostApiChanged()
        Toast.makeText(this, "Live model saved!", Toast.LENGTH_SHORT).show()
        finish()
    }
}
