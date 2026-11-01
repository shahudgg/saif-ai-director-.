package com.example.studio.director

import com.example.studio.composer.StyleDNA

/** Narrative shape chosen deterministically from the creative brief's style seed. */
object StoryStyles {
    val ALL = listOf(
        "COUNTDOWN: build the video as a countdown of the most important points; every point hits harder than the one before.",
        "MYSTERY: open with an unanswered puzzle, drop one clue per beat, reveal the answer near the end.",
        "MYTH-BUSTING: take the 3-4 most common wrong beliefs about the topic and break each one with a hard fact.",
        "BEFORE AND AFTER: how things were, the turning point, how they are now, with numbers for each stage.",
        "ONE PERSON'S STORY: follow one concrete person or company through the topic so every idea is seen through their story.",
        "INVESTIGATION: ask one big question, collect evidence step by step like a detective, end with a clear verdict.",
        "VERSUS: set two sides against each other and score them round by round with facts.",
        "WHAT HAPPENS NEXT: explain the present in two beats, then walk through three realistic future scenarios.",
        "LEVELS: level 1 the simple truth, level 2 the deeper mechanism, level 3 the insider detail most people never hear.",
        "PROBLEM, FAILED FIXES, REAL FIX, PROOF: a sharp problem, why the common fixes fail, the real fix and the evidence."
    )

    fun forSeed(seed: String?): String = ALL[Math.floorMod(seed?.trim()?.toIntOrNull() ?: 0, ALL.size)]
}

/** A deterministic menu of motion-graphic archetypes, each suggested in three distinct layout variants. */
object SceneArchetypes {
    val ALL = listOf(
        "a giant animated number (text fx counter) inside a donut ring with a one-line label and two small supporting stat chips",
        "a horizontal bar race: 4-6 labelled bars growing at different speeds, the winner highlighted",
        "a vertical timeline: 4 dots on a line, each popping in with a year and a short label",
        "a flow diagram: 3-4 labelled rounded boxes joined by arrows that draw themselves",
        "a split comparison: two big cards (left vs right) with a VS badge and two short points on each side",
        "a hub-and-spoke: one centre circle and 4-5 labelled satellites connected by lines drawing out",
        "a line chart that draws itself with a glowing last point and a callout label",
        "a checklist: 4 rows, each with a check icon appearing one after the other",
        "three stacked cards sliding in with offset, each with a bold title and a number",
        "a gauge: a big ring or bar filling to a percentage with the percentage in huge type",
        "kinetic typography: 2-3 huge glowing words popping in one by one over drifting particles",
        "a pyramid / funnel: 3-4 stacked bands of different widths, each labelled",
        "a dot-grid map where a share of the dots light up to show a region or a percentage",
        "a network of nodes that light up in sequence to show how something spreads or connects",
        "a before/after pair of panels joined by an arrow, with the change in number between them",
        "an orbit: a core icon with two rotating rings and three labelled satellites",
        "a radar sweep: a disc with range rings and blips that light up as the sweep passes them, plus a short callout",
        "a gauge with a needle: a semicircular arcdial filling to a real value, the number huge in the middle",
        "a data table: 3 columns x 3 rows of real numbers with a highlighted header row",
        "a numbered staircase: 4 rising steps, each with its number and a two-word title",
        "a heat map: a grid of cells shaded by value, with the two extremes labelled",
        "a stacked composition: 3-4 bands whose heights are the real shares, each labelled",
        "a before/after split: one box cut by a divider knob, left and right labelled, the delta in the centre",
        "a spiral that draws itself with a fact at its centre and three markers along the arm",
        "a flowing ribbon: 3 sine bands drifting at different speeds with one label riding the top band",
        "a pull quote: a giant quotation mark, the quote wrapped in three lines and the speaker under it",
        "a scrolling ticker strip of 5 short items over a halftone dot screen",
        "a barcode motif: vertical bars of varying width with a code under them and one highlighted bar",
        "a spotlight reveal: a moving cone of light over a dark field, one icon and one number inside it",
        "a badge/rosette with a two-word claim inside and a small stat chip below",
        "confetti over a giant counter number that rolls up to the real value",
        "a volumetric light beam from the top with three facts stacked inside it",
        "a CRT scanline readout box with 3 rows of data and a sweeping scan bar"
    )

    fun forScene(seed: String?, index: Int): String {
        val n = seed?.trim()?.toIntOrNull() ?: 0
        val order = ALL.indices.toMutableList().also { java.util.Collections.shuffle(it, java.util.Random(n.toLong() * 31L + 7L)) }
        val layout = when (Math.floorMod(index / order.size + n, 3)) {
            0 -> "centered hero, headline above, supporting detail below"
            1 -> "hero left, text in a right-hand column"
            else -> "hero right, text in a left-hand column"
        }
        return ALL[order[Math.floorMod(index, order.size)]] + " — layout: " + layout
    }
}

/** Prompt builders. Every prompt is generated from the live brief: no fixed templates or canned scenes. */
object DirectorPrompts {

    const val SYSTEM_DIRECTOR = "You are SAIF AI Director, a professional video producer. Follow instructions exactly. " +
        "Never invent facts, statistics, quotes or sources. When unsure, say so. Output exactly the format requested."

    fun script(b: DirectorBrief, researchDigest: String?, targetWords: Int): String = buildString {
        appendLine("Write the narration script for a video.")
        appendLine("Request: ${b.prompt}")
        b.durationSec?.let { appendLine("Target duration: $it seconds (~$targetWords spoken words in ${b.narrationLanguage ?: "the user's language"}).") }
        appendLine("Narration language: ${b.narrationLanguage ?: "same as the request"}. Do NOT switch language.")
        b.audience?.let { appendLine("Audience: $it") }
        b.style?.let { appendLine("Style: $it") }
        b.avoid?.takeIf { !it.startsWith("Nothing", true) }?.let { appendLine("Avoid: $it") }
        when (b.scriptMode) {
            ScriptMode.FULL_SCRIPT_PRESERVE -> appendLine("The user supplied a COMPLETE script. Preserve their wording exactly; only split it into scenes. Mark any necessary edits as [EDIT: reason].\n---\n${b.userScript}\n---")
            ScriptMode.PARTIAL_SCRIPT_EXPAND -> appendLine("The user supplied a PARTIAL script. Keep their sentences and expand around them.\n---\n${b.userScript}\n---")
            else -> {}
        }
        if (researchDigest != null) {
            appendLine("Build the script around these RESEARCHED FACTS. Use their numbers, names and dates exactly; never contradict them and never invent statistics, quotes or sources (cite source numbers like [1]):\n$researchDigest")
            appendLine("Every two or three sentences must deliver something NEW and specific from the facts (a number, a name, a date, a place, a comparison). Cut anything generic.")
        } else {
            appendLine("No live research is available. Use only well-established knowledge and avoid specific statistics or recent claims; flag anything uncertain.")
        }
        appendLine("Structure: strong hook, coherent narrative, clear examples, natural transitions, effective conclusion.")
        appendLine("Format: plain text, one paragraph per scene, separated by a blank line. No headings, no markdown.")
    }

    fun hookOptions(b: DirectorBrief, researchDigest: String?): String = buildString {
        appendLine("Write 5 different opening hooks (first spoken line) for a short vertical video. Each at most 14 words, in ${b.narrationLanguage ?: "the user's language"}.")
        appendLine("Topic: ${b.prompt}")
        b.audience?.let { appendLine("Audience: $it") }
        appendLine("Use 5 different patterns: surprising fact, bold claim, direct question, 'nobody tells you', mini-story. Never start with a greeting or 'in this video'.")
        if (researchDigest != null) appendLine("You may use only facts from this research:\n$researchDigest") else appendLine("Do not use specific statistics.")
        appendLine("Reply with ONLY a JSON array of 5 strings.")
    }

    fun scriptV2(b: DirectorBrief, researchDigest: String?, targetWords: Int, chosenHook: String?): String = buildString {
        append(script(b, researchDigest, targetWords))
        appendLine("Pacing rules: spoken sentences of at most 14 words, one idea per sentence, active voice, vary the rhythm, speak directly to the viewer.")
        appendLine("Open a new beat roughly every 3 seconds of speech (a new fact, example, question or contrast). Include one mini pattern-interrupt around the middle (a question or a surprising twist).")
        appendLine("Structure: hook -> why it matters -> the key idea explained simply -> a concrete example -> proof or twist -> payoff -> a closing line that asks the viewer to follow and to comment on a specific question about this topic.")
        if (b.scriptMode != ScriptMode.FULL_SCRIPT_PRESERVE) {
            appendLine("Tell it in this shape (it overrides the default structure where they differ): ${StoryStyles.forSeed(StyleDNA.fromBrief(b).seed.toString())}")
            appendLine("Originality: write like a human creator with a point of view, not like an encyclopedia. No filler such as 'in today's world' or 'let's dive in'. Do not reuse stock phrases. Show the idea through a specific example, number or short story from the facts.")
            appendLine("Make the viewer picture it: prefer concrete nouns, comparisons the viewer already knows, and one vivid image per beat so each beat can be drawn as a graphic.")
        }
        if (chosenHook != null && b.scriptMode != ScriptMode.FULL_SCRIPT_PRESERVE) appendLine("Start the script with exactly this hook line: \"$chosenHook\"")
        appendLine("Output the spoken narration only: no labels, no brackets, no citations, no stage directions.")
    }

    fun rewrite(b: DirectorBrief, script: String, issues: List<String>, targetWords: Int): String = buildString {
        appendLine("Improve this narration script. Keep the topic, language (${b.narrationLanguage ?: "unchanged"}) and facts; fix ONLY these problems:")
        issues.forEach { appendLine("- $it") }
        appendLine("Target length about $targetWords words. Output the full improved narration only, no labels or commentary.")
        appendLine("---\n$script\n---")
    }

    fun sceneManifest(b: DirectorBrief, script: String, totalMs: Long, fps: Int, transcriptJson: String? = null): String = buildString {
        appendLine("Convert the approved narration into a scene manifest as ONE JSON object (no prose, no markdown).")
        appendLine("Project: aspect ${b.aspect ?: "9:16"}, fps $fps, target total ${totalMs} ms, style ${b.style ?: "explainer"}, motion level ${b.motionLevel ?: "balanced"}.")
        if (transcriptJson != null) appendLine("Scene boundaries MUST follow these transcript segments (ms): $transcriptJson")
        appendLine("""Schema: {"title":str,"fps":int,"scenes":[{"id":"s1","startMs":int,"endMs":int,"narration":str,"visualObjective":str,"caption":str,"transition":"fade|slideLeft|slideUp|zoom|wipe|dissolve|pageflip|cubeSpin|depthWipe|glitchDissolve|none","transitionMs":int,"assets":[{"id":str,"kind":"image|video|overlay|audio","description":"precise searchable description","fallback":"procedural"}],"cues":[{"kind":"sfx|music","description":str,"atMs":int,"durationMs":int}],"graph":{"bgTop":"#RRGGBB","bgBottom":"#RRGGBB","camera":{"zoomFrom":1,"zoomTo":1.15,"panXFrom":0,"panXTo":0.02,"panYFrom":0,"panYTo":0},"elements":[...]}}]}""")
        appendLine("Element types (normalized 0..1 coords): text{text,x,y,w,size,color} rect{x,y,w,h,color} circle{x,y,w,h,color} ring{x,y,w,color} line/arrow{x,y,x2,y2,color} bars{x,y,w,h,values,labels,color} linechart{x,y,w,h,values,color} nodes{x,y,w,h,values=[nodes per layer],color,color2} grid{x,y,w,h,count,color} particles{x,y,w,h,count,seed,color}.")
        appendLine("3D CANVAS ELEMENTS: depthStack{count,labels} parallax{count,labels} extrusion{text,extrusion} perspectiveQuad{text} perspectiveCard{text} cube{text,color,color2}. They are projected, shaded shapes. Every element can set depth -1..1 (positive is nearer); 3D shapes can set rotationX/rotationY in degrees, extrusion/specular/rimLight/shadow 0..1. Camera can set dollyFrom/dollyTo (-1..1), orbitFrom/orbitTo and tiltFrom/tiltTo (degrees, -75..75). Animate rotationX, rotationY and depth in anims. Use distinct depths to make camera motion produce genuine parallax; default 0 preserves flat scenes.")
        appendLine("Each element may have anims:[{prop:x|y|w|h|alpha|scale|rotation|rotationX|rotationY|depth|progress,from,to,startMs,endMs,easing:linear|easeIn|easeOut|easeInOut|overshoot}] with times relative to scene start.")
        appendLine("Design each scene's graphics specifically for what its narration explains (diagrams, charts, flows, labels). Do not reuse the same layout for every scene. Use at most 25 elements per scene and colors as #RRGGBB.")
        appendLine("Scene durations must follow narration length and pacing; they are NOT equal. Scenes must be contiguous from 0.")
        if (b.workflow.forbidsGeneratedNarration) appendLine("IMPORTANT: narration fields are transcript/summary text for timing only; no new narration will be generated.")
        appendLine("Narration:\n$script")
    }

    /** Visual-only planning for a small batch of scenes. Narration and timing are fixed by the app and must not be returned. */
    fun sceneBatch(b: DirectorBrief, batch: List<SceneOutline>, totalScenes: Int, fps: Int, problem: String): String = buildString {
        appendLine("Design the visuals for ${batch.size} scenes of a $totalScenes-scene video. Reply with ONE complete JSON object only (no prose, no markdown).")
        appendLine("Project: aspect ${b.aspect ?: "9:16"}, fps $fps, style ${b.style ?: "explainer"}, motion level ${b.motionLevel ?: "balanced"}.")
        appendLine("""Schema: {"scenes":[{"id":"s1","visualObjective":str,"caption":str,"transition":"fade|slideLeft|slideUp|zoom|wipe|dissolve|pageflip|cubeSpin|depthWipe|glitchDissolve|none","transitionMs":int,"assets":[{"id":str,"kind":"image|video|overlay|audio","description":"precise searchable description","fallback":"procedural"}],"cues":[{"kind":"sfx|music","description":str,"atMs":int,"durationMs":int}],"graph":{"bgTop":"#RRGGBB","bgBottom":"#RRGGBB","camera":{"zoomFrom":1,"zoomTo":1.15,"panXFrom":0,"panXTo":0.02,"panYFrom":0,"panYTo":0},"elements":[...]}}]}""")
        appendLine("Return exactly ${batch.size} scenes, in this order, using exactly these ids. Do NOT return narration, startMs or endMs.")
        appendLine("Element types (normalized 0..1 coords): text{text,x,y,w,size,color} rect{x,y,w,h,color} circle{x,y,w,h,color} ring{x,y,w,color} line/arrow{x,y,x2,y2,color} bars{x,y,w,h,values,labels,color} linechart{x,y,w,h,values,color} nodes{x,y,w,h,values=[nodes per layer],color,color2} grid{x,y,w,h,count,color} particles{x,y,w,h,count,seed,color}.")
        appendLine("3D CANVAS ELEMENTS: depthStack{count,labels} parallax{count,labels} extrusion{text,extrusion} perspectiveQuad{text} perspectiveCard{text} cube{text,color,color2}. They are projected, shaded shapes. Every element can set depth -1..1 (positive is nearer); 3D shapes can set rotationX/rotationY in degrees, extrusion/specular/rimLight/shadow 0..1. Camera can set dollyFrom/dollyTo (-1..1), orbitFrom/orbitTo and tiltFrom/tiltTo (degrees, -75..75). Animate rotationX, rotationY and depth in anims. Use distinct depths to make camera motion produce genuine parallax; default 0 preserves flat scenes.")
        appendLine("Each element may have anims:[{prop:x|y|w|h|alpha|scale|rotation|rotationX|rotationY|depth|progress,from,to,startMs,endMs,easing:linear|easeIn|easeOut|easeInOut|overshoot}] with times relative to scene start and never later than the scene duration.")
        appendLine("Design each scene's graphics for what its narration explains (diagrams, charts, flows, labels). Do not reuse one layout. Use at most 14 elements per scene, colors as #RRGGBB, keep the JSON compact.")
        appendLine("DESIGN RULES (scenes that break them are rejected and redone): every scene must SHOW the idea of its narration with at least 4 visible elements (e.g. a labelled diagram, a chart with real values, a flow of 3-4 labelled steps, a comparison). Every rect that is not a thin bar must contain a text label whose centre lies inside it and whose text fits inside it (short labels, max 4 words). No empty blocks, no full-screen flat rectangles, never the same text twice. Keep all text inside x 0.08..0.92 and y 0.06..0.64. Add atmosphere with glow, particles, grid or ring elements and give every element an entrance animation (alpha/scale/progress). Neighbouring scenes must look different: change layout, shapes and colours.")
        StyleDNA.fromBrief(b).let { d ->
            val pal = d.palettes
            appendLine("Colour mood for this video (use these, they are unique to it): " + pal.joinToString("; ") { "bg ${it.bgTop}->${it.bgBottom}, accents ${it.accent} and ${it.accent2}" } + ".")
        }
        batch.forEach { appendLine("Scene ${it.id} (duration ${it.endMs - it.startMs} ms): ${it.narration}") }
        if (problem.isNotBlank()) appendLine("IMPORTANT correction: $problem")
    }

    /**
     * Motion-graphics design for a few scenes at a time. Each scene gets its own facts (so the graphics show real numbers and
     * names) and its own suggested layout (shuffled per video), so scenes and videos do not repeat each other.
     */
    fun sceneBatchV2(b: DirectorBrief, batch: List<Pair<Int, SceneOutline>>, totalScenes: Int, fps: Int, problem: String, factsFor: (SceneOutline) -> List<String>): String = buildString {
        appendLine("You are an award-winning motion-graphics director. Design ${batch.size} scenes of a $totalScenes-scene explainer video. Reply with ONE complete JSON object only (no prose, no markdown).")
        appendLine("Project: aspect ${b.aspect ?: "9:16"}, fps $fps, style ${b.style ?: "explainer"}, motion level ${b.motionLevel ?: "balanced"}. Topic: ${b.prompt.take(140)}")
        appendLine("""Schema: {"scenes":[{"id":"s1","visualObjective":str,"imagePrompt":str,"caption":str,"transition":"fade|slideLeft|slideUp|zoom|wipe|dissolve|push|whip|flash|iris|zoomBlur|glitch|pageflip|cubeSpin|depthWipe|glitchDissolve","transitionMs":int,"assets":[{"id":str,"kind":"image|video","description":"what a stock clip/picture for this beat must show","fallback":"procedural"}],"cues":[{"kind":"whoosh|pop|riser|hit|ding|tick|glitch","description":str,"atMs":int,"durationMs":int}],"graph":{"bgTop":"#RRGGBB","bgBottom":"#RRGGBB","camera":{"zoomFrom":1,"zoomTo":1.12,"panXFrom":0,"panXTo":0.02,"panYFrom":0,"panYTo":0},"elements":[...]}}]}""")
        appendLine("Return exactly ${batch.size} scenes, in this order, with exactly these ids. Do NOT return narration, startMs or endMs.")
        appendLine("CORE ELEMENTS (normalized 0..1 coords, x/y = centre): text{text,x,y,w,size,color,fx} rect{x,y,w,h,color,radius} circle ring donut{values:[percent]} line arrow bars{values,labels} linechart nodes grid particles glow burst sweep flash glitch grain vignette icon{text=chart|coin|shield|lock|bulb|clock|heart|rocket|check|warning|globe|target|brain|phone|star}.")
        appendLine("ADVANCED ELEMENTS — these exist so every scene can be built differently. Pick the one that really matches the idea:")
        appendLine("  wave{values:[amplitude,harmonics]} · radar{values:[blip radii 0..1],count} · steps{count,labels} (numbered staircase of stages) · table{count=columns,labels=row-major cells} · quote{text,labels:[who]} · ticker{labels=[scrolling items]} · arcdial{values:[0-100],labels:[unit]} (gauge with needle) · spotlight · halftone{count,values} · scanline · confetti{count} · beam{values:[spread]} · ribbon{count} · compare{values:[divider 0-100],labels:[left,right]} · heat{count=columns,values:[0..1 per cell]} · spiral{values:[turns]} · orbit{count,labels} · stack{count,values,labels} (stacked composition) · badge{text,count=points} · barcode{text,count}.")
        appendLine("TEXT FX: rise|pop|fade|typewriter|words|counter|glow|outline|scramble|neon|shadow3d|karaoke|slide|scale|bounce.")
        appendLine("EVERY element also accepts these LOOK controls, use them like a real designer: glow 0..1 (neon bloom), shadow 0..1, blend Normal|Screen|Multiply|Overlay|Add|Lighten|Darken, color2 + gradient (angle in degrees) for a two-colour fill, radius 0..0.5 (rounded corners), dash 0..1 (dashed stroke), outline 0..1, skew -1..1, weight light|normal|bold|black, align center|left|right, letterSpacing, path sine|circle|figure8|drift|linear (the element floats on its own).")
        appendLine("3D CANVAS ELEMENTS: depthStack{count,labels} parallax{count,labels} extrusion{text,extrusion} perspectiveQuad{text} perspectiveCard{text} cube{text,color,color2}. They are projected, shaded shapes. Every element can set depth -1..1 (positive is nearer); 3D shapes can set rotationX/rotationY in degrees, extrusion/specular/rimLight/shadow 0..1. Camera can set dollyFrom/dollyTo (-1..1), orbitFrom/orbitTo and tiltFrom/tiltTo (degrees, -75..75). Animate rotationX, rotationY and depth in anims. Use distinct depths to make camera motion produce genuine parallax; default 0 preserves flat scenes.")
        appendLine("Each element may have anims:[{prop:x|y|w|h|alpha|scale|rotation|rotationX|rotationY|depth|progress|value,from,to,startMs,endMs,easing:linear|easeIn|easeOut|easeInOut|overshoot|expoOut|backOut|bounce|spring|cubicInOut}] with times relative to scene start and never later than the scene duration. Stagger entrances (each element 120-250 ms after the previous) so the scene BUILDS while the narrator speaks.")
        appendLine("QUALITY BAR: a scene is rejected unless it (1) SHOWS the idea of its narration with at least 6 visible elements, (2) carries at least one real number, name or date taken from the narration or its facts, (3) has every non-thin rect holding a short label (max 4 words) whose centre is inside it and that fits, (4) has no empty block, no full-screen flat rectangle and no repeated text, (5) keeps all text inside x 0.08..0.92 and y 0.06..0.66, (6) has a glow / particles / ring / grid atmosphere plus an entrance animation on every element, (7) looks clearly different from its neighbours in layout, shapes and colours.")
        appendLine("ORIGINALITY IS NOT OPTIONAL. Do not reuse a layout. Every scene must be designed from scratch for its own sentence, using a DIFFERENT primitive family from the scenes before it (if the previous scene was bars+cards, this one must be e.g. a radar, a gauge, a table, an orbit, a heat map, a ribbon, a staircase...). A scene that repeats an earlier scene's primitive recipe, layout or palette is REJECTED and you will be asked to redesign it. Prefer one strong custom composition per scene over several generic boxes.")
        appendLine("Use the suggested layout as a starting point; change it if the narration needs something else, but never reuse the previous scene's layout. Write on-screen text in ${b.onScreenLanguage ?: b.narrationLanguage ?: "the narration language"}; keep labels short.")
        appendLine("Optionally add one entry in assets (kind video or image) ONLY when real footage or a picture of the subject would make the beat stronger than a graphic (people, places, products, reactions); describe exactly what it must show. Add 1-3 cues where a sound belongs (whoosh at a transition, hit when a number lands, pop for list items).")
        appendLine("IMAGE-FIRST RULE: for EVERY scene also write \"imagePrompt\" — a detailed ENGLISH prompt for an image-generation model that will create ONE key visual for this scene (it becomes the scene's backdrop, and your animated graphics are drawn on top of it). Describe the subject, action, setting, lighting, camera angle and lens, and the colour mood of the palette below; choose photorealistic OR a premium 3D/illustration style that matches the video style ${b.style ?: "explainer"}, aspect ${b.aspect ?: "9:16"}. Every scene's image must look DIFFERENT from the others (different subject, angle, lighting and palette mix). Do NOT put text, watermarks or logos in the image. Leave \"imagePrompt\" empty ONLY for a purely abstract motion-graphics beat.")
        StyleDNA.fromBrief(b).let { d ->
            appendLine("Colour mood for this video (unique to it): " + d.palettes.joinToString("; ") { "bg ${it.bgTop}->${it.bgBottom}, accents ${it.accent} and ${it.accent2}" } + ".")
        }
        val seed = StyleDNA.fromBrief(b).seed.toString()
        batch.forEach { (idx, o) ->
            appendLine("Scene ${o.id} (duration ${o.endMs - o.startMs} ms) narration: ${o.narration}")
            appendLine("  suggested layout: ${SceneArchetypes.forScene(seed, idx)}")
            factsFor(o).takeIf { it.isNotEmpty() }?.let { f -> appendLine("  facts you may show: " + f.joinToString(" | ")) }
        }
        if (problem.isNotBlank()) appendLine("IMPORTANT correction: $problem")
    }

    fun footageAnalysisPlan(b: DirectorBrief, analysisJson: String, transcript: String?): String = buildString {
        appendLine("You are editing the user's EXISTING footage. Never invent footage or events that are not in the analysis.")
        appendLine("Goal: ${b.answers["goal"] ?: b.prompt}. Target length: ${b.answers["duration"] ?: "keep reasonable"}. Aspect ${b.aspect ?: "keep source"}. Style ${b.style ?: "clean"}.")
        appendLine("Technical analysis (JSON): $analysisJson")
        transcript?.let { appendLine("Transcript with timestamps: $it") }
        appendLine("""Return ONE JSON object: {"summary":str,"keep":[{"startMs":int,"endMs":int,"why":str}],"cut":[{"startMs":int,"endMs":int,"why":str}],"overlays":[{"atMs":int,"durationMs":int,"text":str,"why":str}],"musicMood":str,"captions":bool}.""")
        appendLine("keep/cut ranges are in SOURCE time and must lie within the source duration. Do not output narration.")
    }

    fun narrationFromFootage(b: DirectorBrief, analysisJson: String, transcript: String?, keepJson: String): String = buildString {
        appendLine("Write narration describing ONLY what is visible/audible in the footage analysis. No invented facts.")
        appendLine("Language: ${b.narrationLanguage ?: "English"}. Pace: ${b.pace ?: "normal"}. Mode: ${b.narrationMode ?: "Add narration, keep original audio"}.")
        appendLine("Analysis: $analysisJson")
        transcript?.let { appendLine("Existing speech: $it") }
        appendLine("Kept segments (ms, source time): $keepJson")
        appendLine("""Return ONE JSON object: {"segments":[{"atMs":int,"maxMs":int,"text":str}]} where each segment fits inside its window (maxMs) at normal speaking speed.""")
    }

    fun assetMatch(description: String, sceneObjective: String): String =
        "Does this image match the needed asset? Needed: \"$description\". Scene: \"$sceneObjective\". " +
            """Reply ONLY JSON: {"match":true|false,"score":0-10,"issues":["low quality|wrong subject|unsafe|watermark|wrong aspect|text unreadable"]}"""

    /**
     * Situation-aware check used by the sequential asset hunter: the model has just downloaded ONE file and must
     * decide whether it is really the asset this exact moment of the video needs — not merely "an image of money".
     */
    fun assetMatchAt(what: String, situation: String, kind: String, alreadyTried: List<String>): String = buildString {
        appendLine("You are the asset verifier of a video editor. ONE file was just downloaded from the user's Telegram library.")
        appendLine("The editor needs: \"$what\" (wanted format: $kind).")
        appendLine("It will be used at this exact place in the video:")
        appendLine(situation)
        if (alreadyTried.isNotEmpty()) {
            appendLine("Already rejected for this place: ${alreadyTried.take(4).joinToString(" | ") { it.take(60) }}")
            appendLine("Do not accept another file that has the same problem.")
        }
        appendLine()
        appendLine("Look at the file and judge ONLY whether it can be cut into this moment of this video:")
        appendLine("- Is the SUBJECT right (the thing/person/action the narration is talking about at this moment)?")
        appendLine("- Is it usable technically: in focus, not a tiny thumbnail, no watermark, no burned-in subtitles or channel logo, not a screenshot of a chat, not a contact sheet or a collage of many pictures?")
        appendLine("- Would a viewer understand it in under one second, with no explanation?")
        appendLine("- Is the content safe (no nudity, gore, hate symbols, real private documents)?")
        appendLine("Be strict. A loosely related file is a rejection: score it below 6. Only accept when you would cut it in yourself.")
        appendLine("""Reply ONLY this JSON: {"match":true|false,"score":0-10,"issues":["wrong subject|watermark|low quality|collage|subtitles|unreadable|wrong aspect|unsafe|too short"],"betterKeywords":["two","three"]}""")
        appendLine("betterKeywords: only when match=false — 2 or 3 different search words that would find the right file in this library.")
    }
}
