package com.victorious.victordrax

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.victorious.victordrax.db.AppDb
import com.victorious.victordrax.db.Engagement
import com.victorious.victordrax.db.Note
import com.victorious.victordrax.engage.Report
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EngagementDaoTest {
    private lateinit var db: AppDb

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDb::class.java).allowMainThreadQueries().build()
    }
    @After fun tearDown() = db.close()

    @Test fun insertUpdateAndAuthorization() = runTest {
        val dao = db.engagements()
        val id = dao.insert(Engagement(name = "Lab", client = "Me", scope = "10.0.0.0/24\n\n 192.168.1.10 "))
        val e = dao.get(id)!!
        assertFalse(e.isAuthorized)
        assertEquals(listOf("10.0.0.0/24", "192.168.1.10"), e.scopeList())
        dao.update(e.copy(authorizedBy = "Jane, CISO", authorizedDate = 1_700_000_000_000, authDocUri = "content://doc/1"))
        assertTrue(dao.get(id)!!.isAuthorized)
        assertEquals(1, dao.all().first().size)
    }

    @Test fun notesCascadeOnDelete() = runTest {
        val dao = db.engagements()
        val id = dao.insert(Engagement(name = "A", client = "B", scope = ""))
        dao.addNote(Note(engagementId = id, text = "one", createdAt = 1))
        dao.addNote(Note(engagementId = id, text = "two", createdAt = 2))
        assertEquals(listOf("one", "two"), dao.notes(id).first().map { it.text })
        dao.delete(dao.get(id)!!)
        assertTrue(dao.notesOnce(id).isEmpty())
    }

    @Test fun markdownReport() = runTest {
        val dao = db.engagements()
        val id = dao.insert(Engagement(name = "Lab", client = "Me", scope = "10.0.0.1", createdAt = 0))
        dao.addNote(Note(engagementId = id, text = "found\nthing", createdAt = 0))
        val md = Report.markdown(dao.get(id)!!, dao.notesOnce(id), TimeZone.getTimeZone("UTC"))
        assertTrue(md.startsWith("# Engagement report: Lab"))
        assertTrue(md.contains("NO AUTHORIZATION RECORDED"))
        assertTrue(md.contains("- `10.0.0.1`"))
        assertTrue(md.contains("found thing"))
    }
}
