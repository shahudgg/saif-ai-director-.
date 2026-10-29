package com.example.studio.composer

/** Chooses a topic glyph (drawn natively by the scene renderer) from the words of a beat. English and Hinglish keywords. */
object IconPicker {
    val NAMES = listOf("chart", "coin", "shield", "lock", "bulb", "clock", "heart", "rocket", "check", "warning", "globe", "target", "brain", "phone", "star")

    private val RULES: List<Pair<String, Set<String>>> = listOf(
        "warning" to setOf("warning", "danger", "mistake", "mistakes", "avoid", "wrong", "scam", "fraud", "fake", "khatra", "galti", "dhoka", "loss", "nuksan"),
        "lock" to setOf("private", "password", "hack", "hacker", "privacy", "encrypt", "lock", "secret"),
        "shield" to setOf("safe", "security", "protect", "secure", "safety", "insurance", "guard", "risk", "surakshit"),
        "chart" to setOf("price", "market", "trading", "trade", "stock", "stocks", "chart", "profit", "sales", "revenue", "graph", "data", "analysis", "nifty", "forex"),
        "coin" to setOf("money", "cash", "dollar", "dollars", "rupee", "rupees", "pay", "income", "earn", "earning", "invest", "investment", "fund", "paisa", "paise", "bitcoin", "crypto", "salary"),
        "brain" to setOf("ai", "brain", "mind", "smart", "intelligence", "model", "neural", "machine", "algorithm", "gpt", "learning"),
        "bulb" to setOf("idea", "ideas", "tip", "tips", "learn", "think", "trick", "tricks", "knowledge", "solution", "samajh", "seekho"),
        "clock" to setOf("time", "speed", "fast", "minute", "minutes", "second", "seconds", "hour", "deadline", "wait", "instant", "daily", "samay"),
        "rocket" to setOf("grow", "growth", "launch", "start", "boost", "scale", "success", "rise", "increase", "badhao"),
        "heart" to setOf("health", "love", "life", "care", "fitness", "family", "pyaar"),
        "globe" to setOf("world", "global", "internet", "web", "country", "online", "network", "duniya"),
        "target" to setOf("goal", "focus", "aim", "strategy", "plan", "target", "lakshya"),
        "phone" to setOf("phone", "app", "apps", "mobile", "android", "instagram", "social", "youtube", "whatsapp"),
        "check" to setOf("done", "correct", "right", "verified", "confirm", "yes", "sahi")
    )

    /** First rule (in priority order) that matches any word, or null when nothing fits. */
    fun pick(words: List<String>): String? {
        val set = words.flatMap { it.lowercase().split(Regex("[^a-z0-9]+")) }.filter { it.isNotBlank() }.toSet()
        if (set.isEmpty()) return null
        return RULES.firstOrNull { (_, keys) -> keys.any { it in set } }?.first
    }

    fun forBeat(beat: Beat): String? = pick(beat.keywords + beat.headline.split(" ") + beat.support.split(" "))
}
