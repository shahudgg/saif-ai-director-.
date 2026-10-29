package com.example.studio.director

import com.example.ui.components.TelegramAssetItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 9: the asset hunt asks Telegram for ONE asset at a time with only two or three keywords in the exact
 * file format the timeline needs, verifies what came back, and only then places it. These tests cover the pure
 * decision logic behind that loop - format matching, library ranking, slot ordering and the keyword retry ladder.
 */
class AssetHunterTest {

    private fun item(
        title: String, type: String, ext: String, category: String, subject: String,
        keywords: List<String> = emptyList(), tags: List<String> = emptyList(),
        userId: String = "all", fileId: String = "F_$title", size: Long = 1_000_000L,
        assetId: String = ""
    ) = TelegramAssetItem(
        assetId = assetId, title = title, type = type, extension = ext, category = category,
        subject = subject, searchKeywords = keywords, tags = tags, userId = userId,
        fileId = fileId, messageId = title.hashCode().toLong(), fileSize = size
    )

    private val library = listOf(
        item("Cash Money PNG", "image", "png", "overlay_graphic", "cash", listOf("cash", "money"), listOf("#cash")),
        item("Rupee Notes Loop", "video", "mp4", "b_roll", "money", listOf("money", "rupee")),
        item("Whoosh Fast", "audio", "mp3", "sfx", "whoosh", listOf("whoosh", "fast")),
        item("Other Tenant Cash", "image", "png", "overlay_graphic", "cash", listOf("cash"), userId = "someoneelse"),
        item("Pending Placeholder", "image", "png", "overlay_graphic", "cash", listOf("cash"), fileId = "FILE_ID_PENDING"),
        item("Huge Clip", "video", "mp4", "b_roll", "money", listOf("money"), size = 30L * 1024 * 1024)
    )

    // ---------------------------------------------------------------- AssetFormats

    @Test fun kindOfGroupsEveryExtensionTheHunterCanAskFor() {
        assertEquals("image", AssetFormats.kindOf("png"))
        assertEquals("image", AssetFormats.kindOf("JPG"))
        assertEquals("image", AssetFormats.kindOf(".webp"))
        assertEquals("video", AssetFormats.kindOf("mp4"))
        assertEquals("video", AssetFormats.kindOf("MOV"))
        assertEquals("audio", AssetFormats.kindOf("mp3"))
        assertEquals("audio", AssetFormats.kindOf("wav"))
        assertEquals("unknown", AssetFormats.kindOf("ttf"))
        assertEquals("unknown", AssetFormats.kindOf(""))
    }

    @Test fun telegramTypeFallsBackToAllForUnknownFormats() {
        assertEquals("image", AssetFormats.telegramType("png"))
        assertEquals("video", AssetFormats.telegramType("mp4"))
        assertEquals("audio", AssetFormats.telegramType("mp3"))
        assertEquals("all", AssetFormats.telegramType("ttf"))
    }

    @Test fun matchesTreatsJpegAndJpgAsTheSameFile() {
        assertTrue(AssetFormats.matches("jpeg", "jpg", allowKindFallback = false))
        assertTrue(AssetFormats.matches("jpg", "jpeg", allowKindFallback = false))
        assertTrue(AssetFormats.matches("PNG", "png", allowKindFallback = false))
    }

    /**
     * The core of the owner's request: when the editor asked for a PNG (a transparent sticker) an MP4 must NOT be
     * accepted in its place. Only the relaxed second pass may substitute inside the same media kind.
     */
    @Test fun strictFormatRefusesADifferentFileInTheSameKind() {
        assertFalse("png is not jpg when the format is strict", AssetFormats.matches("png", "jpg", allowKindFallback = false))
        assertTrue("png may stand in for jpg only in the relaxed pass", AssetFormats.matches("png", "jpg", allowKindFallback = true))
        assertFalse("a picture never stands in for a sound", AssetFormats.matches("png", "mp3", allowKindFallback = true))
        assertFalse("a clip never stands in for a sound", AssetFormats.matches("mp4", "mp3", allowKindFallback = true))
        assertFalse("unknown formats never match by kind", AssetFormats.matches("ttf", "png", allowKindFallback = true))
    }

    @Test fun normalizeAlwaysReturnsSomethingTheRendererCanOpen() {
        assertEquals("jpg", AssetFormats.normalize("JPEG"))
        assertEquals("jpg", AssetFormats.normalize(".Jpg"))
        assertEquals("mp3", AssetFormats.normalize(".MP3"))
        assertEquals("png", AssetFormats.normalize(""))
        assertEquals("png", AssetFormats.normalize("unknown"))
        assertEquals("png", AssetFormats.normalize("ttf"))
        assertTrue(AssetFormats.normalize("anything") in AssetFormats.ALL)
    }

    // ---------------------------------------------------------------- AssetLibrary.usable

    @Test fun usableDropsOtherTenantsPlaceholdersAndOversizedFiles() {
        val usable = AssetLibrary.usable(library, "default")
        val titles = usable.map { it.title }
        assertEquals(3, usable.size)
        assertTrue("Cash Money PNG" in titles)
        assertTrue("Rupee Notes Loop" in titles)
        assertTrue("Whoosh Fast" in titles)
        assertFalse("another tenant's asset must never be offered", "Other Tenant Cash" in titles)
        assertFalse("a placeholder file id cannot be downloaded", "Pending Placeholder" in titles)
        assertFalse("Telegram bots cap downloads at 20 MB", "Huge Clip" in titles)
    }

    @Test fun usableAcceptsAssetsOwnedByTheCallingTenant() {
        val usable = AssetLibrary.usable(library, "someoneelse")
        assertTrue(usable.any { it.title == "Other Tenant Cash" })
    }

    @Test fun usableRejectsZeroSizedAndUnknownTypes() {
        val odd = listOf(
            item("No File Id", "image", "png", "overlay", "x", fileId = ""),
            item("A Font", "font", "ttf", "font", "x"),
            item("Free Size", "image", "png", "overlay", "x", size = 0L)
        )
        val usable = AssetLibrary.usable(odd, "default")
        assertFalse(usable.any { it.title == "No File Id" })
        assertFalse(usable.any { it.title == "A Font" })
        assertTrue("size 0 means unknown, not empty", usable.any { it.title == "Free Size" })
    }

    // ---------------------------------------------------------------- AssetLibrary.search

    @Test fun searchRanksATitleHitAboveATagHit() {
        val pool = AssetLibrary.usable(
            library + item("Abstract Shapes", "image", "png", "overlay_graphic", "shapes", tags = listOf("#cash")),
            "default"
        )
        val hits = AssetLibrary.search(pool, listOf("cash"), "png", limit = 2)
        assertTrue(hits.isNotEmpty())
        assertEquals("the asset named 'cash' outranks one that merely carries the tag", "Cash Money PNG", hits.first().title)
    }

    @Test fun searchReturnsNothingWhenNoKeywordIsLongEnough() {
        val pool = AssetLibrary.usable(library, "default")
        assertTrue(AssetLibrary.search(pool, emptyList(), "png").isEmpty())
        assertTrue("single characters are not keywords", AssetLibrary.search(pool, listOf("a", "b"), "png").isEmpty())
    }

    @Test fun searchRespectsTheRequestedFormatAndThenRelaxes() {
        val pool = AssetLibrary.usable(library, "default")
        // The editor asked for a jpg. The library only has a png of cash.
        assertTrue("strict format finds nothing", AssetLibrary.search(pool, listOf("cash"), "jpg", strictFormat = true).isEmpty())
        val relaxed = AssetLibrary.search(pool, listOf("cash"), "jpg", strictFormat = false)
        assertTrue("the relaxed second pass accepts the png", relaxed.any { it.title == "Cash Money PNG" })
    }

    @Test fun searchNeverOffersAnAssetAlreadyUsedInThisVideo() {
        val pool = AssetLibrary.usable(library, "default")
        val first = AssetLibrary.search(pool, listOf("cash"), "png").first()
        val used = setOf(AssetLibrary.idOf(first))
        val again = AssetLibrary.search(pool, listOf("cash"), "png", avoidIds = used)
        assertTrue("the used asset is not offered twice", again.none { AssetLibrary.idOf(it) == used.first() })
    }

    @Test fun searchPrefersTheSfxCategoryForSoundRequests() {
        val pool = AssetLibrary.usable(
            library + item("Money Talk Track", "audio", "mp3", "bgm", "money", listOf("money")), "default"
        )
        val hits = AssetLibrary.search(pool, listOf("whoosh"), "mp3", limit = 2)
        assertTrue(hits.any { it.title == "Whoosh Fast" })
    }

    @Test fun searchNeverCrossesMediaKinds() {
        val pool = AssetLibrary.usable(library, "default")
        assertTrue("asking for a sound must not return the cash picture",
            AssetLibrary.search(pool, listOf("cash"), "mp3").isEmpty())
        assertTrue("asking for a picture must not return the whoosh",
            AssetLibrary.search(pool, listOf("whoosh"), "png").isEmpty())
    }

    @Test fun searchHonoursTheLimit() {
        val many = (0 until 12).map { item("Cash Pack $it", "image", "png", "overlay_graphic", "cash", listOf("cash")) }
        val hits = AssetLibrary.search(AssetLibrary.usable(many, "default"), listOf("cash"), "png", limit = 3)
        assertEquals(3, hits.size)
    }

    @Test fun idOfFallsBackToTheTitleWhenThereIsNoAssetId() {
        val withId = item("Named", "image", "png", "overlay", "x", assetId = "AID_123")
        val without = item("Only Title", "image", "png", "overlay", "x", assetId = "")
        assertEquals("AID_123", AssetLibrary.idOf(withId))
        assertEquals("Only Title", AssetLibrary.idOf(without))
    }

    @Test fun sampleTitlesShowsOnlyTheRequestedKind() {
        val pool = AssetLibrary.usable(library, "default")
        val pictures = AssetLibrary.sampleTitles(pool, "png", 10)
        assertTrue(pictures.all { it.endsWith(".png") })
        assertTrue(pictures.any { it.contains("Cash Money") })
        val sounds = AssetLibrary.sampleTitles(pool, "mp3", 10)
        assertTrue(sounds.all { it.endsWith(".mp3") })
        assertTrue(AssetLibrary.sampleTitles(pool, "png", 1).size <= 1)
    }

    // ---------------------------------------------------------------- HuntSlots

    private fun scene(i: Int, narration: String, objective: String, cues: List<AudioCue> = emptyList()) =
        Scene(
            id = "s$i", startMs = i * 3000L, endMs = i * 3000L + 3000L,
            narration = narration, visualObjective = objective, cues = cues
        )

    @Test fun slotsAreBuiltInTimelineOrderVisualBeforeSound() {
        val m = SceneManifest(
            scenes = listOf(
                scene(0, "The market crashed hard", "STATEMENT: crash", listOf(
                    AudioCue("whoosh", "transition", 200L), AudioCue("boom", "impact", 900L)
                )),
                scene(1, "Here is what it means for you", "STATEMENT: meaning")
            )
        )
        val slots = HuntSlots.build(m, "9:16")
        assertEquals(4, slots.size)
        assertEquals(listOf("s0:visual", "s0:sfx0", "s0:sfx1", "s1:visual"), slots.map { it.slotId })
        assertEquals(listOf(false, true, true, false), slots.map { it.isSfx })
        assertTrue(slots.all { it.sceneCount == 2 })
        assertEquals(0, slots[0].sceneIndex)
        assertEquals(1, slots[3].sceneIndex)
        assertEquals("9:16", slots[0].aspect)
        assertEquals(3000L, slots[0].durationMs)
    }

    @Test fun slotsSortScenesByStartTimeEvenWhenTheManifestIsOutOfOrder() {
        val m = SceneManifest(scenes = listOf(scene(1, "second", "x"), scene(0, "first", "y")))
        val slots = HuntSlots.build(m, "1:1")
        assertEquals(listOf("s0:visual", "s1:visual"), slots.map { it.slotId })
        assertEquals("first", slots[0].narration)
        assertEquals("1:1", slots[0].aspect)
    }

    @Test fun slotDefaultsToVerticalAndCapsSoundCuesPerScene() {
        val m = SceneManifest(scenes = listOf(
            scene(0, "n", "o", (0 until 5).map { AudioCue("k$it", "d", it * 100L) })
        ))
        val capped = HuntSlots.build(m, null)
        assertEquals("1 visual + at most 2 sound cues", 3, capped.size)
        assertTrue(capped.all { it.aspect == "9:16" })
        val explicit = HuntSlots.build(m, "16:9", maxSfxPerScene = 0)
        assertEquals(1, explicit.size)
    }

    @Test fun soundCueTimeIsPulledInsideTheScene() {
        val m = SceneManifest(scenes = listOf(scene(0, "n", "o", listOf(AudioCue("boom", "d", 99_999L)))))
        val slots = HuntSlots.build(m, "9:16")
        val sfx = slots.first { it.isSfx }
        assertTrue("the cue must land inside its own scene", sfx.atMs in 0L..(3000L - 120L))
        assertEquals("boom", sfx.cueKind)
    }

    @Test fun unfilledKeepsOnlyThePlacesThatGotNoAsset() {
        val m = SceneManifest(scenes = listOf(scene(0, "a", "x"), scene(1, "b", "y")))
        val slots = HuntSlots.build(m, "9:16")
        val placed = listOf(
            ResolvedAsset("s0", "lib-s0:visual", "image", "/tmp/a.png", "telegram:x", true)
        )
        val left = HuntSlots.unfilled(slots, placed)
        assertEquals(1, left.size)
        assertEquals("s1", left.first().sceneId)
    }

    @Test fun unfilledDoesNotLetAPictureCoverAMissingSound() {
        val m = SceneManifest(scenes = listOf(scene(0, "a", "x", listOf(AudioCue("whoosh", "d", 300L)))))
        val slots = HuntSlots.build(m, "9:16")
        val pictureOnly = listOf(ResolvedAsset("s0", "lib-s0:visual", "image", "/tmp/a.png", "t", true))
        val left = HuntSlots.unfilled(slots, pictureOnly)
        assertTrue("the sound slot is still empty", left.any { it.isSfx })
        assertFalse("the picture slot is filled", left.any { !it.isSfx })
    }

    // ---------------------------------------------------------------- AssetHunter

    private val visualSlot = HuntSlot(
        slotId = "s0:visual", sceneId = "s0", purpose = "visual", sceneIndex = 0, sceneCount = 3,
        startMs = 0L, durationMs = 4000L, narration = "The stock market crashed hard yesterday",
        visualObjective = "STATEMENT: crash", graphicsSummary = "red arrow", aspect = "9:16"
    )

    private val sfxSlot = visualSlot.copy(
        slotId = "s0:sfx0", purpose = "sfx", atMs = 500L, cueKind = "whoosh"
    )

    @Test fun seedNeedUsesTwoOrThreeKeywordsFromTheNarration() {
        val need = AssetHunter.seedNeed(visualSlot)
        assertTrue("the hunt must still run without a model", need.want)
        assertEquals("mp4", need.ext)
        assertEquals("video", need.kind)
        assertTrue("only two or three keywords, never a list of everything", need.keywords.size in 1..3)
        assertTrue(need.keywords.all { it.length > 3 })
        assertFalse("filler words are not keywords", "the" in need.keywords)
        assertEquals(0L, need.durMs)
    }

    @Test fun seedNeedBuildsASoundRequestFromTheCueKind() {
        val need = AssetHunter.seedNeed(sfxSlot)
        assertEquals("mp3", need.ext)
        assertEquals("audio", need.kind)
        assertEquals(listOf("whoosh", "sfx"), need.keywords)
        assertEquals(500L, need.atMs)
        assertEquals(900L, need.durMs)
        assertTrue("a sound needs no placement box", need.placement == null)
    }

    @Test fun seedNeedGivesAStickerPlacementToPictures() {
        val need = AssetHunter.seedNeed(visualSlot)
        assertTrue("a picture must arrive with a placement", need.placement != null)
    }

    @Test fun keywordLadderAlwaysOffersShortAlternativeSearches() {
        val need = HuntNeed(visualSlot.slotId, true, "png", "a burst of falling cash", listOf("cash", "money"))
        val ladder = AssetHunter.keywordLadder(need, visualSlot)
        assertTrue("the ladder must offer a retry", ladder.isNotEmpty())
        assertTrue("capped so the hunt cannot loop forever", ladder.size <= AssetHunter.MAX_TRIES + 2)
        assertTrue("every rung is a two-or-three keyword search", ladder.all { it.isNotEmpty() && it.size <= 3 })
        assertEquals("the first rung is what the model asked for", listOf("cash", "money"), ladder.first())
        assertEquals("no rung is repeated", ladder.size, ladder.distinctBy { it.joinToString(" ") }.size)
    }

    @Test fun keywordLadderAddsSoundSpecificRungsForCues() {
        val need = HuntNeed(sfxSlot.slotId, true, "mp3", "a fast whoosh", listOf("whoosh"))
        val ladder = AssetHunter.keywordLadder(need, sfxSlot)
        assertTrue(ladder.any { it == listOf("whoosh", "sfx") })
        assertTrue(ladder.any { it == listOf("whoosh", "sound") })
    }

    @Test fun keywordLadderSurvivesANeedWithNoKeywords() {
        val need = HuntNeed(visualSlot.slotId, true, "png", "falling rupee notes", emptyList())
        val ladder = AssetHunter.keywordLadder(need, visualSlot)
        assertTrue(ladder.isNotEmpty())
        assertTrue(ladder.all { it.isNotEmpty() })
    }

    @Test fun stepTextTellsTheOwnerWhichSceneAndWhichKeywordsAreBeingSearched() {
        val need = HuntNeed(visualSlot.slotId, true, "png", "cash", listOf("cash", "money"))
        val text = AssetHunter.stepText(visualSlot, need, need.keywords, 2, 3)
        assertTrue(text.contains("Scene 1/3"))
        assertTrue("the keywords are shown", text.contains("cash money"))
        assertTrue("the format is shown", text.contains("png"))
        assertTrue("the retry number is shown", text.contains("try 2"))
        assertTrue("progress is shown", text.contains("3 asset(s)"))

        val first = AssetHunter.stepText(sfxSlot, null, emptyList(), 1, 0)
        assertTrue(first.contains("sound @0.5s"))
        assertFalse("try 1 is not announced as a retry", first.contains("try 1"))
    }

    @Test fun maxTriesIsSmallSoAHuntCannotSpinForever() {
        assertEquals(3, AssetHunter.MAX_TRIES)
    }
}
