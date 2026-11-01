package com.example.llm

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Round 10 — image GENERATION and EDITING through the user's OWN provider key (BYOK). No developer keys.
 *
 * Supported providers (whatever is saved in SAIF AI Settings):
 *  • Gemini      — `generateContent` with `responseModalities:["TEXT","IMAGE"]` (Nano-Banana-class image
 *                  models; if the active model is not an image model, `gemini-2.5-flash-image` is used).
 *  • OpenAI      — `POST /v1/images/generations` (gpt-image-1) and `/v1/images/edits`.
 *  • OpenRouter  — `POST /api/v1/images/generations` / `images/edits` (e.g. google/gemini-2.5-flash-image).
 *  • Custom      — any OpenAI-compatible base URL (`<base>/images/generations`).
 *
 * Used by the AI Director (one AI key visual per scene, generated BEFORE the code/animations are drawn on
 * top) and by the asset tools (edit / background-remove / restyle a library or Flow image).
 */
object ImageGen {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    /** Providers that can generate images with the user's own key. */
    fun canGenerate(provider: String, apiKey: String): Boolean {
        if (apiKey.isBlank()) return false
        val p = provider.lowercase()
        return p.contains("gemini") || p.contains("openai") || p.contains("openrouter") || p.contains("custom")
    }

    /** Picks the image model: the user's active model when it is an image model, else the provider default. */
    fun imageModel(provider: String, activeModel: String): String {
        val m = activeModel.trim().removePrefix("models/")
        val lm = m.lowercase()
        if (lm.contains("image") || lm.contains("imagen") || lm.contains("dall") || lm.contains("flux") || lm.contains("banana")) return m
        val p = provider.lowercase()
        return when {
            p.contains("gemini") -> "gemini-2.5-flash-image"          // Nano Banana
            p.contains("openrouter") -> "google/gemini-2.5-flash-image"
            else -> "gpt-image-1"                                      // OpenAI / custom default
        }
    }

    private fun sizeFor(aspect: String?, provider: String): String {
        val a = aspect ?: "9:16"
        return if (provider.lowercase().contains("gemini")) {
            a // Gemini image models take the aspect ratio directly
        } else when (a) {
            "16:9", "16:10" -> "1792x1024"
            "1:1", "4:3" -> "1024x1024"
            "4:5" -> "1024x1280"
            else -> "1024x1792" // 9:16 and anything portrait
        }
    }

    private fun geminiBody(prompt: String, aspect: String?, withImage: ByteArray?): JSONObject {
        val parts = org.json.JSONArray()
        if (withImage != null) {
            parts.put(JSONObject().put("inlineData", JSONObject()
                .put("mimeType", "image/png")
                .put("data", Base64.encodeToString(withImage, Base64.NO_WRAP))))
        }
        parts.put(JSONObject().put("text", prompt))
        val gen = JSONObject().put("responseModalities", org.json.JSONArray().put("TEXT").put("IMAGE"))
        if (aspect != null && aspect.contains(":")) gen.put("imageConfig", JSONObject().put("aspectRatio", aspect))
        return JSONObject()
            .put("contents", org.json.JSONArray().put(JSONObject().put("parts", parts)))
            .put("generationConfig", gen)
    }

    private fun extractInlineImage(json: JSONObject): ByteArray? {
        val candidates = json.optJSONArray("candidates") ?: return null
        for (i in 0 until candidates.length()) {
            val parts = candidates.optJSONObject(i)?.optJSONObject("content")?.optJSONArray("parts") ?: continue
            for (p in 0 until parts.length()) {
                val inline = parts.optJSONObject(p)?.optJSONObject("inlineData") ?: continue
                val data = inline.optString("data", "")
                if (data.isNotBlank()) return Base64.decode(data, Base64.DEFAULT)
            }
        }
        return null
    }

    private fun extractOpenAiImage(json: JSONObject): ByteArray? {
        val arr = json.optJSONArray("data") ?: return null
        if (arr.length() == 0) return null
        val o = arr.optJSONObject(0) ?: return null
        val b64 = o.optString("b64_json", "")
        if (b64.isNotBlank()) return Base64.decode(b64, Base64.DEFAULT)
        val url = o.optString("url", "")
        if (url.isNotBlank()) return runCatching { download(url) }.getOrNull()
        return null
    }

    private fun download(url: String): ByteArray {
        val resp = http.newCall(Request.Builder().url(url).get().build()).execute()
        resp.use { r ->
            if (!r.isSuccessful) throw IllegalStateException("image download failed: HTTP ${r.code}")
            return r.body?.bytes() ?: throw IllegalStateException("empty image download")
        }
    }

    private fun postJson(url: String, body: String, apiKey: String, extraHeaders: Map<String, String> = emptyMap()): JSONObject {
        val req = Request.Builder().url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
        extraHeaders.forEach { (k, v) -> req.addHeader(k, v) }
        val resp = http.newCall(req.post(body.toRequestBody("application/json".toMediaType())).build()).execute()
        val text = resp.use { it.body?.string().orEmpty() }
        if (!resp.isSuccessful) {
            val msg = runCatching {
                val e = JSONObject(text).opt("error")
                if (e is JSONObject) e.optString("message", text) else text
            }.getOrDefault(text)
            throw IllegalStateException("image API HTTP ${resp.code}: ${msg.take(200)}")
        }
        return JSONObject(text)
    }

    private fun postMultipart(url: String, apiKey: String, prompt: String, model: String, image: ByteArray): JSONObject {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", model)
            .addFormDataPart("prompt", prompt)
            .addFormDataPart("n", "1")
            .addFormDataPart("image", "input.png", image.toRequestBody("image/png".toMediaType()))
            .build()
        val req = Request.Builder().url(url).addHeader("Authorization", "Bearer $apiKey").post(body)
        val resp = http.newCall(req.build()).execute()
        val text = resp.use { it.body?.string().orEmpty() }
        if (!resp.isSuccessful) throw IllegalStateException("image edit API HTTP ${resp.code}: ${text.take(200)}")
        return JSONObject(text)
    }

    /**
     * Generates ONE image. Returns the raw image bytes (png/jpg), or a failure with an honest reason.
     */
    suspend fun generate(
        prompt: String, aspect: String?, provider: String, apiKey: String, activeModel: String, baseUrl: String? = null
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            if (!canGenerate(provider, apiKey)) return@withContext Result.failure(
                IllegalStateException("The configured provider ($provider) cannot generate images with this key."))
            val p = provider.lowercase()
            val model = imageModel(provider, activeModel)
            val bytes: ByteArray? = when {
                p.contains("gemini") -> {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                    val resp = http.newCall(Request.Builder().url(url)
                        .post(geminiBody(prompt, aspect, null).toString().toRequestBody("application/json".toMediaType())).build()).execute()
                    val text = resp.use { it.body?.string().orEmpty() }
                    if (!resp.isSuccessful) throw IllegalStateException("Gemini image HTTP ${resp.code}: ${text.take(200)}")
                    extractInlineImage(JSONObject(text))
                }
                p.contains("openrouter") -> {
                    val body = JSONObject().put("model", model).put("prompt", prompt).put("n", 1)
                    extractOpenAiImage(postJson("https://openrouter.ai/api/v1/images/generations", body.toString(), apiKey,
                        mapOf("HTTP-Referer" to "https://github.com/shahudgg/saif-ai-director-", "X-Title" to "SAIF AI")))
                }
                else -> { // openai + custom OpenAI-compatible base urls
                    val base = baseUrl?.trim()?.takeIf { it.isNotBlank() }?.trimEnd('/') ?: "https://api.openai.com"
                    val body = JSONObject().put("model", model).put("prompt", prompt).put("n", 1)
                        .put("size", sizeFor(aspect, provider)).put("response_format", "b64_json")
                    extractOpenAiImage(postJson("$base/v1/images/generations", body.toString(), apiKey))
                }
            }
            if (bytes == null || bytes.isEmpty()) Result.failure(IllegalStateException("The image API returned no image data."))
            else Result.success(bytes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Edits an existing image with a text instruction (background removal, restyle, crop-out object, ...).
     */
    suspend fun edit(
        prompt: String, image: ByteArray, provider: String, apiKey: String, activeModel: String, baseUrl: String? = null
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            if (!canGenerate(provider, apiKey)) return@withContext Result.failure(
                IllegalStateException("The configured provider ($provider) cannot edit images with this key."))
            val p = provider.lowercase()
            val model = imageModel(provider, activeModel)
            val bytes: ByteArray? = when {
                p.contains("gemini") -> {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                    val resp = http.newCall(Request.Builder().url(url)
                        .post(geminiBody(prompt, null, image).toString().toRequestBody("application/json".toMediaType())).build()).execute()
                    val text = resp.use { it.body?.string().orEmpty() }
                    if (!resp.isSuccessful) throw IllegalStateException("Gemini edit HTTP ${resp.code}: ${text.take(200)}")
                    extractInlineImage(JSONObject(text))
                }
                p.contains("openrouter") -> {
                    // OpenRouter edits take JSON with the source image as a base64 data URL.
                    val body = JSONObject().put("model", model).put("prompt", prompt).put("n", 1)
                        .put("image", org.json.JSONArray().put("data:image/png;base64," + Base64.encodeToString(image, Base64.NO_WRAP)))
                    extractOpenAiImage(postJson("https://openrouter.ai/api/v1/images/edits", body.toString(), apiKey,
                        mapOf("HTTP-Referer" to "https://github.com/shahudgg/saif-ai-director-", "X-Title" to "SAIF AI")))
                }
                else -> {
                    val base = baseUrl?.trim()?.takeIf { it.isNotBlank() }?.trimEnd('/') ?: "https://api.openai.com"
                    extractOpenAiImage(postMultipart("$base/v1/images/edits", apiKey, prompt, model, image))
                }
            }
            if (bytes == null || bytes.isEmpty()) Result.failure(IllegalStateException("The image edit returned no image data."))
            else Result.success(bytes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
