package com.luna.assistant
import android.content.SharedPreferences
import org.json.*
import java.net.*

object AiClient {
    val provs = linkedMapOf(
        "GitHub" to "https://github.com/settings/tokens",
        "Groq" to "https://console.groq.com/keys",
        "Cerebras" to "https://cloud.cerebras.ai",
        "Gemini" to "https://aistudio.google.com/apikey",
        "Claude" to "https://console.anthropic.com/settings/keys",
        "ChatGPT" to "https://platform.openai.com/api-keys")
    private const val SYS = "You are Luna, a voice assistant. Reply briefly, in the same language the user speaks (Hindi, Hinglish, English, any)."
    private fun post(url: String, h: Map<String, String>, body: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 15000; c.readTimeout = 30000
        c.setRequestProperty("Content-Type", "application/json")
        h.forEach { (k, v) -> c.setRequestProperty(k, v) }
        c.outputStream.use { it.write(body.toByteArray()) }
        val s = if (c.responseCode < 400) c.inputStream else c.errorStream
        return s.bufferedReader().readText()
    }
    private fun chat(url: String, key: String, model: String, q: String): String {
        val b = JSONObject().put("model", model).put("messages", JSONArray()
            .put(JSONObject().put("role", "system").put("content", SYS))
            .put(JSONObject().put("role", "user").put("content", q)))
        val r = JSONObject(post(url, mapOf("Authorization" to "Bearer $key"), b.toString()))
        return r.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    }
    private var cachedClaude: String? = null
    // Newest Sonnet is auto-picked from the API's model list (newest first), so 5.5 -> 5.6 needs no app update
    private fun claudeModel(key: String): String {
        cachedClaude?.let { return it }
        try {
            val c = URL("https://api.anthropic.com/v1/models").openConnection() as HttpURLConnection
            c.setRequestProperty("x-api-key", key); c.setRequestProperty("anthropic-version", "2023-06-01")
            val d = JSONObject(c.inputStream.bufferedReader().readText()).getJSONArray("data")
            for (i in 0 until d.length()) { val id = d.getJSONObject(i).getString("id"); if (id.contains("sonnet")) { cachedClaude = id; return id } }
        } catch (e: Exception) {}
        return "claude-sonnet-5-5"
    }
    fun ask(provider: String, key: String, q: String): String = when (provider) {
        "Gemini" -> {
            val b = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts",
                JSONArray().put(JSONObject().put("text", "$SYS\n\n$q")))))
            JSONObject(post("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$key", mapOf(), b.toString()))
                .getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
        }
        "Claude" -> {
            val b = JSONObject().put("model", claudeModel(key)).put("max_tokens", 500).put("system", SYS)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", q)))
            JSONObject(post("https://api.anthropic.com/v1/messages", mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01"), b.toString()))
                .getJSONArray("content").getJSONObject(0).getString("text")
        }
        "GitHub" -> chat("https://models.github.ai/inference/chat/completions", key, "openai/gpt-4o-mini", q)
        "Groq" -> chat("https://api.groq.com/openai/v1/chat/completions", key, "llama-3.3-70b-versatile", q)
        "Cerebras" -> chat("https://api.cerebras.ai/v1/chat/completions", key, "llama3.1-8b", q)
        else -> chat("https://api.openai.com/v1/chat/completions", key, "gpt-4o-mini", q)
    }
    // Selected AI first, then auto-failover to any other AI that has a saved key
    fun askAny(p: SharedPreferences, q: String): String {
        val first = p.getString("prov", "GitHub") ?: "GitHub"
        var err = ""; var any = false
        for (n in listOf(first) + provs.keys.filter { it != first }) {
            val k = p.getString("key_$n", "") ?: ""
            if (k.isEmpty()) continue
            any = true
            try { return ask(n, k, q) } catch (e: Exception) { err = "$n: ${e.message}" }
        }
        return if (!any) "Sir, pehle app mein koi AI key ya token add kijiye" else "Sir, sab AI fail ho gaye ($err)"
    }
}
