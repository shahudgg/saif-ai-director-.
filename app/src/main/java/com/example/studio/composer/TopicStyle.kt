package com.example.studio.composer

/**
 * ROUND 16.0 — TOPIC-AWARE ART DIRECTION.
 *
 * The owner asked for "topic-appropriate visual treatment": a space video must not look like a business
 * explainer, and a history video must not look like a tech demo. This object reads the brief's own words
 * (English + Hinglish), decides which world the video lives in, and hands the rest of the pipeline a
 * concrete art direction:
 *
 *   • which palettes (out of [StyleDNA.BANK]) belong to that world,
 *   • which visual families read correctly there,
 *   • which composition grammars (of [SceneGrammar]) suit it,
 *   • the camera temperament (drift through space vs a tight orbit around a product),
 *   • the wording the image model should use for generated key visuals.
 *
 * Everything is deterministic: the same brief always produces the same direction, and nothing here
 * overrides a choice the owner made explicitly (palette seeds still shuffle inside the chosen family).
 */
enum class TopicDomain(val label: String) {
    SPACE("space & cosmos"),
    TECH("technology"),
    HISTORY("history"),
    NATURE("nature"),
    EDUCATION("explainer"),
    BUSINESS("business"),
    HEALTH("health"),
    SCIENCE("science"),
    GENERAL("general");
}

data class TopicStyle(
    val domain: TopicDomain,
    /** Palette indexes into [StyleDNA.BANK], most characteristic first. */
    val paletteIds: List<Int>,
    /** Element families that read correctly for this topic. */
    val archetypes: List<String>,
    /** Preferred composition grammars (see [SceneGrammar]). */
    val grammars: List<Int>,
    /** How the camera should behave. */
    val camera: String,
    /** Wording appended to every generated image prompt. */
    val imageTreatment: String
) {
    companion object {

        fun general(): TopicStyle = TopicStyle(
            TopicDomain.GENERAL, listOf(0, 3, 5, 7),
            listOf("steps", "bars", "table", "nodes", "ticker", "compare", "wave"),
            listOf(0, 1, 5), "steady push",
            "clean editorial composition, one clear focal point, generous negative space, no text"
        )

        private fun of(domain: TopicDomain): TopicStyle = when (domain) {
            TopicDomain.SPACE -> TopicStyle(
                domain, listOf(0, 7, 2, 3),
                listOf("orbit", "spiral", "radar", "ring", "spotlight", "wave", "particles", "nodes"),
                listOf(0, 2, 4), "slow drift + orbit",
                "deep space vista, volumetric nebula light, stars with subtle bloom, a single hero object, " +
                    "astronomical scale, no text, no logos"
            )
            TopicDomain.TECH -> TopicStyle(
                domain, listOf(1, 3, 10, 7),
                listOf("nodes", "scanline", "barcode", "ticker", "cube", "table", "linechart", "grid"),
                listOf(1, 3, 5), "tight push-in with slight orbit",
                "clean technology visual, precise geometry, cool rim light, brushed metal and glass, " +
                    "isometric depth, no text, no brand marks"
            )
            TopicDomain.HISTORY -> TopicStyle(
                domain, listOf(11, 5, 4, 8),
                listOf("table", "steps", "quote", "ribbon", "badge", "heat", "stack"),
                listOf(0, 5, 3), "very slow lateral drift",
                "historical editorial illustration, archival texture and paper grain, warm amber light, " +
                    "painterly detail, period-accurate objects, no text"
            )
            TopicDomain.NATURE -> TopicStyle(
                domain, listOf(8, 5, 10, 1),
                listOf("wave", "halftone", "ribbon", "orbit", "stack", "particles", "heat"),
                listOf(2, 4, 1), "organic drift, gentle parallax",
                "natural world photography look, rich greens and earth tones, soft daylight, water and " +
                    "foliage detail, shallow depth of field, no text"
            )
            TopicDomain.EDUCATION -> TopicStyle(
                domain, listOf(3, 10, 6, 0),
                listOf("steps", "table", "bars", "compare", "badge", "ticker", "linechart"),
                listOf(0, 1, 5), "steady, readable push",
                "clean educational illustration, flat 3D shapes, friendly lighting, crystal clear subject, " +
                    "lots of empty space for labels, no text"
            )
            TopicDomain.BUSINESS -> TopicStyle(
                domain, listOf(11, 6, 3, 4),
                listOf("bars", "linechart", "arcdial", "table", "heat", "compare", "ticker"),
                listOf(0, 3, 5), "confident push + slight lateral pan",
                "premium finance editorial look, dark charcoal surfaces, gold and white accents, glass " +
                    "reflections, minimal props, no text, no currency logos"
            )
            TopicDomain.HEALTH -> TopicStyle(
                domain, listOf(9, 4, 5, 6),
                listOf("arcdial", "compare", "steps", "heat", "halftone", "wave"),
                listOf(1, 4, 0), "calm, soft push",
                "calm medical illustration, soft studio light, clean surfaces, human-scale detail, " +
                    "trustworthy and warm, no text, no gore"
            )
            TopicDomain.SCIENCE -> TopicStyle(
                domain, listOf(2, 6, 10, 0),
                listOf("radar", "heat", "spiral", "orbit", "table", "linechart", "beam"),
                listOf(4, 2, 3), "precise orbit, measured push",
                "scientific visualisation, laboratory precision, diagram-like clarity, controlled rim light, " +
                    "microscopic detail, no text"
            )
            TopicDomain.GENERAL -> general()
        }

        private val KEYWORDS: List<Pair<TopicDomain, List<String>>> = listOf(
            TopicDomain.SPACE to listOf(
                "space", "universe", "galaxy", "planet", "black hole", "blackhole", "nasa", "rocket", "cosmos",
                "asteroid", "mars", "moon", "sun", "star", "orbit", "gravity", "antariksh", "brahmand", "grah",
                "taara", "chandrama", "surya", "akash", "rocketry"
            ),
            TopicDomain.TECH to listOf(
                "ai", "a.i", "artificial intelligence", "computer", "code", "coding", "software", "app", "internet",
                "upi", "blockchain", "crypto", "robot", "chip", "smartphone", "algorithm", "data", "cloud", "5g",
                "cyber", "hacking", "server", "machine learning", "chatgpt", "technology", "tech", "gadget", "tecnology"
            ),
            TopicDomain.HISTORY to listOf(
                "history", "empire", "ancient", "mughal", "british", "civilization", "king", "dynasty", "revolution",
                "1947", "independence", "war", "medieval", "itihas", "rajvansh", "raja", "samrajya", "puratatva",
                "harappa", "maurya", "gupta", "colonial", "century"
            ),
            TopicDomain.NATURE to listOf(
                "nature", "ocean", "sea", "forest", "animal", "climate", "wildlife", "coral", "jungle", "river",
                "mountain", "pollution", "environment", "birds", "tiger", "amazon", "rainforest", "prakriti",
                "van", "nadi", "paryavaran", "jal", "bee", "ecosystem", "biodiversity"
            ),
            TopicDomain.BUSINESS to listOf(
                "business", "startup", "money", "market", "brand", "profit", "sales", "marketing", "economy",
                "finance", "investment", "stock", "share", "gst", "tax", "paisa", "vyapar", "karobar", "bazar",
                "nivesh", "company", "revenue", "customer", "ecommerce", "entrepreneur"
            ),
            TopicDomain.HEALTH to listOf(
                "health", "brain", "sleep", "food", "diet", "exercise", "disease", "medicine", "body", "mental",
                "stress", "yoga", "heart", "diabetes", "immunity", "swasthya", "bimari", "dawa", "neend",
                "khana", "motapa", "vitamin", "protein"
            ),
            TopicDomain.SCIENCE to listOf(
                "physics", "chemistry", "biology", "experiment", "energy", "atom", "dna", "quantum", "molecule",
                "gravity wave", "relativity", "evolution", "cell", "electron", "vigyan", "rasayan", "jeev",
                "parmanu", "urja", "theory", "scientist"
            ),
            TopicDomain.EDUCATION to listOf(
                "how to", "kaise", "learn", "guide", "tutorial", "exam", "study", "explained", "explain", "basics",
                "sikho", "seekhein", "samjho", "samjhao", "step by step", "course", "lesson", "tips", "beginner"
            )
        )

        /** Detect the topic world from the brief's words. First match wins; ties go to the earlier list. */
        fun detect(text: String): TopicDomain {
            val t = text.lowercase()
            if (t.isBlank()) return TopicDomain.GENERAL
            for ((domain, words) in KEYWORDS) {
                if (words.any { w -> t.contains(w) }) return domain
            }
            return TopicDomain.GENERAL
        }

        fun forTopic(text: String): TopicStyle = of(detect(text))
    }
}
