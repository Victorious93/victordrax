package com.victorious.victordrax

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.victorious.victordrax.db.*
import com.victorious.victordrax.findings.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FindingsDaoTest {
    private lateinit var db: AppDb
    private lateinit var repo: FindingsRepo
    private var now = 1_000L
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDb::class.java)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ctx, AppDb::class.java).allowMainThreadQueries().build()
        repo = FindingsRepo(db) { now }
    }
    @After fun tearDown() = db.close()

    private suspend fun authorized() = db.engagements().insert(Engagement(name = "Lab", client = "ACME", scope = "10.0.0.0/24",
        authorizedBy = "Jane", authorizedDate = 1, authDocUri = "content://sow"))
    private suspend fun unauthorized() = db.engagements().insert(Engagement(name = "NoAuth", client = "X", scope = "10.9.9.9"))

    @Test fun crudRoundTripsAllFields() = runTest {
        val eid = authorized()
        val r = repo.save(Finding(engagementId = eid, title = "  Open SMB share ", severity = "high", status = "Confirmed",
            description = "Guest RW", tags = "smb, smb ,internal", target = "10.0.0.7", evidence = "smbclient -L", evidencePaths = "content://a/1.png\ncontent://a/2.txt"))
        val id = (r as SaveResult.Saved).id
        val got = db.findings().get(id)!!
        assertEquals("Open SMB share", got.title); assertEquals("High", got.severity); assertEquals("CONFIRMED", got.status)
        assertEquals("smb,internal", got.tags); assertEquals(listOf("content://a/1.png", "content://a/2.txt"), got.evidenceList())
        assertEquals(0L, got.updatedAt)
        now = 5_000
        repo.save(got.copy(status = FindingStatus.FIXED.name))
        val upd = db.findings().get(id)!!
        assertEquals(FindingStatus.FIXED, upd.state); assertEquals(5_000L, upd.updatedAt); assertEquals(5_000L, upd.lastChanged)
        assertEquals(1, repo.observe(eid).first().size)
        repo.delete(upd)
        assertNull(db.findings().get(id)); assertEquals(0, db.findings().countFor(eid))
    }

    @Test fun rejectsUnauthorizedEngagementAndBlankTitle() = runTest {
        val bad = unauthorized()
        assertTrue(repo.save(Finding(engagementId = bad, title = "x", severity = "Low")) is SaveResult.Rejected)
        assertTrue(repo.save(Finding(engagementId = 999, title = "x", severity = "Low")) is SaveResult.Rejected)
        assertTrue(repo.save(Finding(engagementId = authorized(), title = "  ", severity = "Low")) is SaveResult.Rejected)
        assertEquals(0, db.findings().allOnce().size)
        assertNull(repo.recordRun(bad, "nmap", "nmap 10.9.9.9"))
    }

    @Test fun cascadeDeleteWithEngagement() = runTest {
        val eid = authorized()
        repo.save(Finding(engagementId = eid, title = "a", severity = "Low"))
        repo.recordRun(eid, "whois", "whois acme.test")
        db.engagements().delete(db.engagements().get(eid)!!)
        assertEquals(0, db.findings().allOnce().size); assertEquals(0, db.toolRuns().forEngagementOnce(eid).size)
    }

    @Test fun findingFromToolRunKeepsLink() = runTest {
        val eid = authorized()
        now = 42
        val runId = repo.recordRun(eid, "nmap", "nmap -sV 10.0.0.5", "workflow:Recon", "10.0.0.5")!!
        val run = db.toolRuns().get(runId)!!
        assertEquals(42L, run.startedAt)
        val id = (repo.save(FindingsRepo.draftFromRun(run).copy(title = "SSH exposed")) as SaveResult.Saved).id
        assertEquals(runId, db.findings().get(id)!!.sourceRunId)
        // Deleting the run does not delete the finding (no FK on sourceRunId).
        db.toolRuns().delete(run)
        assertNotNull(db.findings().get(id))
    }

    @Test fun migrate2to3() {
        helper.createDatabase("mig3.db", 2).apply {
            execSQL("INSERT INTO engagements (id,name,client,scope,authorizedBy,authorizedDate,authDocUri,createdAt,execSummary) VALUES (1,'Old','C','10.0.0.1','',0,'',0,'')")
            execSQL("INSERT INTO findings (id,engagementId,title,severity,cvssVector,evidence,remediation,createdAt) VALUES (1,1,'Legacy','HIGH','','e','r',5)")
            close()
        }
        val m = helper.runMigrationsAndValidate("mig3.db", 3, true, AppDb.MIGRATION_2_3)
        m.query("SELECT status, tags, updatedAt, sourceRunId FROM findings WHERE id=1").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("OPEN", c.getString(0)); assertEquals("", c.getString(1))
            assertEquals(0L, c.getLong(2)); assertTrue(c.isNull(3))
        }
        m.query("SELECT COUNT(*) FROM tool_runs").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
    }

    @Test fun migrate3to4() {
        helper.createDatabase("mig4.db", 3).apply {
            execSQL("INSERT INTO engagements (id,name,client,scope,authorizedBy,authorizedDate,authDocUri,createdAt,execSummary) VALUES (1,'E','C','10.0.0.1','',0,'',0,'')")
            execSQL("INSERT INTO tool_runs (id,engagementId,label,command,source,target,startedAt,output) VALUES (1,1,'nmap','nmap -sV','tool','10.0.0.1',5,'')")
            close()
        }
        val m = helper.runMigrationsAndValidate("mig4.db", 4, true, AppDb.MIGRATION_3_4)
        m.query("SELECT exitCode, finishedAt, outputPath FROM tool_runs WHERE id=1").use { c ->
            assertTrue(c.moveToFirst()); assertTrue(c.isNull(0)); assertEquals(0L, c.getLong(1)); assertEquals("", c.getString(2))
        }
    }

    @Test fun attachOutputParsesAndStores() = runTest {
        val eid = authorized()
        val rid = repo.recordRun(eid, "nmap", "nmap -sV 10.0.0.5", "tool", "10.0.0.5", outputPath = "/sdcard/VictorDrax/runs/run-x.log")!!
        now = 2_000L
        assertTrue(repo.attachOutput(rid, "Nmap scan report\n22/tcp open ssh\n__VDX_EXIT__ 0"))
        val run = db.toolRuns().get(rid)!!
        assertEquals("Nmap scan report\n22/tcp open ssh", run.output)
        assertEquals(0, run.exitCode)
        assertEquals(2_000L, run.finishedAt)
        assertEquals("/sdcard/VictorDrax/runs/run-x.log", run.outputPath)
    }

    @Test fun completeRunFromExecStoresShellResult() = runTest {
        val eid = authorized()
        val rid = repo.recordRun(eid, "id", "id", "tool")!!
        now = 3_000L
        assertTrue(repo.completeRunFromExec(rid, com.victorious.victordrax.data.ShellResult(0, "uid=0(root)\n")))
        val run = db.toolRuns().get(rid)!!
        assertEquals("uid=0(root)", run.output); assertEquals(0, run.exitCode); assertEquals(3_000L, run.finishedAt)
    }

    @Test fun draftFromRunIncludesCapturedOutput() {
        val run = com.victorious.victordrax.db.ToolRun(id = 9, engagementId = 1, label = "nmap", command = "nmap -sV 10.0.0.5",
            source = "tool", target = "10.0.0.5", output = "22/tcp open ssh", exitCode = 0, finishedAt = 10)
        val f = FindingsRepo.draftFromRun(run)
        assertTrue(f.evidence.contains("nmap -sV 10.0.0.5"))
        assertTrue(f.evidence.contains("22/tcp open ssh"))
        assertEquals(9L, f.sourceRunId)
    }
}
