package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.example.data.local.ChatSessionEntity
import com.example.data.local.ChatMessageEntity
import com.example.data.local.SaifDatabase
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SAIF AI", appName)
  }

  @Test
  fun `test database session creation`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = SaifDatabase.getDatabase(context)
    val dao = db.chatDao()

    val session = ChatSessionEntity(title = "Test Session", mode = "coding")
    dao.insertSession(session)

    val fetched = dao.getSessionById(session.id)
    assertNotNull(fetched)
    assertEquals("Test Session", fetched?.title)
    assertEquals("coding", fetched?.mode)
  }
}
