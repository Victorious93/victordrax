package com.victorious.victordrax

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.victorious.victordrax.backup.BackupStore
import com.victorious.victordrax.backup.RestoreMode
import com.victorious.victordrax.db.*
import com.victorious.victordrax.report.FindingReport
import com.victorious.victordrax.report.Severity
import com.victorious.victordrax.workflow.Playbooks
import com.victorious.victordrax.workflow.seedBuiltIns
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class DataLayerTest {
    private lateinit var db: AppDb
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDb::class.java)

    @Before fun setUp() { db = Room.inMemoryDatabaseBuilder(ctx, AppDb::class.java).allowMainThreadQueries().build() }
    @After fun tearDown() = db.close()

    private suspend fun seed(): Long {
        val id = db.engagements().insert(Engagement(name = "Lab", client = "ACME", scope = "10.0.0.0/24", authorizedBy = "Jane", authorizedDate = 1, authDocUri = "content://a"))
        db.engagements().addNote(Note(engagementId = id, text = "n1"))
        db.findings().insert(Finding(engagementId = id, title = "Weak TLS", severity = "LOW"))
        db.findings().insert(Finding(engagementId = id, title = "RCE", severity = "CRITICAL", cvssVector = "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H", remediation = "Patch"))
        val pb = db.playbooks().insert(Playbook(name = "Mine"))
        db.playbooks().insertStep(PlaybookStep(playbookId = pb, ordinal = 0, label = "w", command = "whois {target}"))
        return id
    }

    @Test fun findingsAndReport() = runTest {
        val id = seed()
        val e = db.engagements().get(id)!!; val f = db.findings().forEngagementOnce(id)
        assertEquals(Severity.CRITICAL, FindingReport.topSeverity(f))
        assertEquals(1, FindingReport.severityCounts(f)[Severity.LOW])
        val md = FindingReport.markdown(e, f)
        assertTrue(md.indexOf("RCE") < md.indexOf("Weak TLS")) // sorted by severity
        assertTrue(md.contains("(base 9.8)")); assertTrue(md.contains("Executive summary"))
        assertTrue(FindingReport.executiveSummary(e.copy(execSummary = "Custom"), f) == "Custom")
        // PDF rendering (android.graphics.pdf) has no Robolectric implementation; verified on device only.
    }

    @Test fun seedOnce() = runTest {
        seedBuiltIns(db); seedBuiltIns(db)
        assertEquals(Playbooks.builtIns.size, db.playbooks().builtInCount())
    }

    @Test fun restoreMergeAndReplace() = runTest {
        seed(); seedBuiltIns(db)
        val store = BackupStore(db)
        val bundle = store.collect()
        assertEquals(1, bundle.playbooks.size) // built-ins excluded
        store.restore(bundle, RestoreMode.MERGE)
        assertEquals(2, db.engagements().allOnce().size); assertEquals(4, db.findings().allOnce().size)
        assertEquals(1, db.playbooks().allOnce().count { !it.builtIn }) // same name skipped
        store.restore(bundle, RestoreMode.REPLACE)
        assertEquals(1, db.engagements().allOnce().size); assertEquals(2, db.findings().allOnce().size)
        assertEquals(1, db.engagements().allNotesOnce().size)
        assertEquals(1, db.playbooks().allOnce().count { !it.builtIn })
        assertEquals(Playbooks.builtIns.size, db.playbooks().builtInCount()) // built-ins untouched
    }

    @Test fun migrate1to2() {
        helper.createDatabase("mig.db", 1).apply {
            execSQL("INSERT INTO engagements (id,name,client,scope,authorizedBy,authorizedDate,authDocUri,createdAt) VALUES (1,'Old','C','10.0.0.1','',0,'',0)")
            close()
        }
        val m = helper.runMigrationsAndValidate("mig.db", 2, true, AppDb.MIGRATION_1_2)
        m.query("SELECT execSummary FROM engagements WHERE id=1").use { c -> assertTrue(c.moveToFirst()); assertEquals("", c.getString(0)) }
    }
}
