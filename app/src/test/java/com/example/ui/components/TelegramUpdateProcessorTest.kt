package com.example.ui.components

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TelegramUpdateProcessorTest {

    private val response = JSONObject("""
    {"ok":true,"result":[
      {"update_id":101,"channel_post":{"message_id":11,"chat":{"id":-1001,"username":"saif_assets"},
        "caption":"[ASSET_INDEX]\nTitle: Neon Glitch\nType: video\nExtension: mp4\nCategory: overlay_graphic\nSubject: glitch\nSearch Keywords: glitch, neon\nTags: #neon\nUser_ID: all\n[/ASSET_INDEX]",
        "video":{"file_id":"VID1","file_size":900000}}},
      {"update_id":102,"channel_post":{"message_id":12,"chat":{"id":-1001},"audio":{"file_id":"AUD1","file_name":"whoosh_fast_01.mp3","mime_type":"audio/mpeg","duration":2,"file_size":40000}}},
      {"update_id":103,"channel_post":{"message_id":13,"chat":{"id":-1001},"text":"hello channel"}},
      {"update_id":104,"channel_post":{"message_id":14,"chat":{"id":-999},"document":{"file_id":"DOC1","file_name":"x.png","mime_type":"image/png"}}},
      {"update_id":105,"channel_post":{"message_id":15,"chat":{"id":-1001},"photo":[{"file_id":"P_small"},{"file_id":"P_big","file_size":12345}]}},
      {"update_id":106,"channel_post":{"message_id":16,"chat":{"id":-1001},"document":{"file_id":"DOC2","file_name":"notes.pdf","mime_type":"application/pdf"}}}
    ]}
    """.trimIndent())

    @Test fun `caption posts, file-name posts and photos are indexed, everything else is counted as skipped`() {
        val p = TelegramUpdateProcessor.process(response, "-1001")
        assertEquals(6, p.received)
        assertEquals(106L, p.maxUpdateId)
        assertEquals(1, p.fromCaption)
        assertEquals(2, p.fromFileName)
        assertEquals(3, p.items.size)
        assertEquals(1, p.skipped["no_file"]); assertEquals(1, p.skipped["other_chat"]); assertEquals(1, p.skipped["unrecognised_file"])
    }

    @Test fun `a short audio file named like a sound effect becomes an sfx`() {
        val sfx = TelegramUpdateProcessor.process(response, "-1001").items.first { it.fileId == "AUD1" }
        assertEquals("audio", sfx.type); assertEquals("sfx", sfx.category); assertTrue(sfx.searchKeywords.contains("whoosh"))
    }

    @Test fun `largest photo size is used`() {
        assertEquals(12345L, TelegramUpdateProcessor.process(response, "-1001").items.first { it.fileId == "P_big" }.fileSize)
    }

    @Test fun `caption fields are parsed into the item`() {
        val a = TelegramUpdateProcessor.process(response, "-1001").items.first { it.fileId == "VID1" }
        assertEquals("Neon Glitch", a.title); assertEquals("overlay_graphic", a.category); assertTrue(a.searchKeywords.contains("glitch"))
    }

    @Test fun `channel username also matches`() {
        assertEquals(1, TelegramUpdateProcessor.process(response, "@saif_assets").fromCaption)
    }

    @Test fun `empty channel id accepts every chat`() {
        assertEquals(0, TelegramUpdateProcessor.process(response, "").skipped["other_chat"] ?: 0)
    }
}
