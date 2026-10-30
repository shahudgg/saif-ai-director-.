package com.example.studio.composer

/**
 * Per-video visual identity: palettes, backdrop and ornaments are derived from a stable normalized creative brief.
 * All scenes in one video belong together; a changed brief deterministically creates a different look.
 */
data class StyleDNA(
    val seed: Int,
    val palettes: List<Palette>,
    val variantShift: Int,
    val backdrop: Int,          // 0 particles+glow, 1 grid, 2 rings, 3 aurora, 4 scan sweep
    val ornament: Int,          // 0 progress bar + tag pill, 1 corner brackets + tag pill, 2 progress bar only, 3 brackets + progress bar
    val signature: String = "" // stable identity of normalized creative brief
) {
    /** Palette for beat [index]; alert/excited beats pick the warm entries so emotion still reads. */
    fun palette(index: Int, emotion: String): Palette {
        val warm = palettes.firstOrNull { it.accent.uppercase() in WARM_ACCENTS }
        if (emotion == "alert" && warm != null) return warm
        return palettes[(index + seed.mod(palettes.size)).mod(palettes.size)]
    }

    companion object {
        private val WARM_ACCENTS = setOf("#FF8A3D", "#FF5C3D", "#FFB300", "#FF4D6D")

        val BANK: List<Palette> = listOf(
            Palette("#0B0A14", "#1B1330", "#7C4DFF", "#00E5FF"),
            Palette("#071A1F", "#0B2F3A", "#00E5FF", "#FFD54F"),
            Palette("#160B1E", "#2D1240", "#FF4DA6", "#7C4DFF"),
            Palette("#0A1420", "#13294B", "#4DA3FF", "#00F5A0"),
            Palette("#1A0F0A", "#33180F", "#FF8A3D", "#FFD54F"),
            Palette("#081611", "#0F2E22", "#00F5A0", "#4DA3FF"),
            Palette("#0D0D12", "#1E1E2A", "#FF4D6D", "#FFD54F"),
            Palette("#0A0F1F", "#1A1F45", "#6C8CFF", "#FF4DA6"),
            Palette("#10130A", "#232B12", "#B6FF3D", "#00E5FF"),
            Palette("#140A12", "#3A1230", "#FF6BD6", "#00F5A0"),
            Palette("#061218", "#0A2A33", "#2EE6D6", "#FF8A3D"),
            Palette("#120C06", "#2B1D0C", "#FFB300", "#7C4DFF")
        )

        /** Deterministic for a given [seed], different for different seeds. */
        fun from(seed: Int): StyleDNA {
            val r = java.util.Random(seed.toLong() * 6364136223846793005L + 1442695040888963407L)
            val order = BANK.indices.toMutableList().also { java.util.Collections.shuffle(it, r) }
            return StyleDNA(
                seed = seed,
                palettes = order.take(4).map { BANK[it] },
                variantShift = r.nextInt(3),
                backdrop = r.nextInt(5),
                ornament = r.nextInt(4)
            )
        }

        fun fromAnswer(value: String?): StyleDNA? = value?.trim()?.toIntOrNull()?.let { from(it) }

        private fun normalize(value: String?): String = java.text.Normalizer.normalize(value.orEmpty(), java.text.Normalizer.Form.NFKC)
            .trim().lowercase(java.util.Locale.ROOT).replace(Regex("\\s+"), " ")

        /** Derive v2 identity from user-facing creative inputs, not generated styleSeed or timestamps. */
        fun fromBrief(brief: com.example.studio.director.DirectorBrief): StyleDNA {
            val fields = listOf(brief.prompt, brief.userScript, brief.style, brief.audience, brief.aspect,
                brief.motionLevel, brief.pace, brief.narrationLanguage, brief.onScreenLanguage, brief.assetPolicy, brief.avoid)
            val canonical = fields.map(::normalize).joinToString("") { "${it.length}:$it" }
            val hash = java.security.MessageDigest.getInstance("SHA-256").digest(("saif-style-v2\u001f" + canonical).toByteArray(Charsets.UTF_8))
            val seed = ((hash[0].toInt() and 255) shl 24) or ((hash[1].toInt() and 255) shl 16) or
                ((hash[2].toInt() and 255) shl 8) or (hash[3].toInt() and 255)
            val signature = "v2-" + hash.joinToString("") { "%02x".format(java.util.Locale.ROOT, it.toInt() and 255) }
            return from(seed).copy(signature = signature)
        }

        /** A fresh random seed, stored as text in the brief's answers. */
        fun newSeed(): String = (java.util.Random().nextInt(Int.MAX_VALUE)).toString()
    }
}
