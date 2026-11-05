package com.victorious.victordrax.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "engagements")
@kotlinx.serialization.Serializable
data class Engagement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val client: String,
    /** Newline-separated targets / CIDR ranges. */
    val scope: String,
    val authorizedBy: String = "",
    /** Epoch millis of the authorization date; 0 = not set. */
    val authorizedDate: Long = 0,
    /** SAF content:// URI of the signed authorization document. */
    val authDocUri: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /** Optional executive summary override for the pro report (blank = auto-generated). */
    val execSummary: String = "",
) {
    val isAuthorized: Boolean get() = authorizedBy.isNotBlank() && authorizedDate > 0 && authDocUri.isNotBlank()
    fun scopeList(): List<String> = scope.lines().map { it.trim() }.filter { it.isNotEmpty() }
}

@Entity(
    tableName = "notes",
    foreignKeys = [ForeignKey(entity = Engagement::class, parentColumns = ["id"], childColumns = ["engagementId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("engagementId")],
)
@kotlinx.serialization.Serializable
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val engagementId: Long,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "findings",
    foreignKeys = [ForeignKey(entity = Engagement::class, parentColumns = ["id"], childColumns = ["engagementId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("engagementId")],
)
@kotlinx.serialization.Serializable
data class Finding(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val engagementId: Long,
    val title: String,
    /** CRITICAL/HIGH/MEDIUM/LOW/INFO */
    val severity: String,
    /** Optional CVSS 3.1 vector string, e.g. CVSS:3.1/AV:N/... */
    val cvssVector: String = "",
    val evidence: String = "",
    val remediation: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    // ---- v3: findings manager ----
    val description: String = "",
    /** Comma-separated free-form tags, e.g. "web,auth". Use [tagList]. */
    val tags: String = "",
    /** Affected target / asset (host, URL, app). Should be inside the engagement scope. */
    val target: String = "",
    /** FindingStatus name: OPEN/CONFIRMED/FIXED/WONT_FIX. */
    val status: String = "OPEN",
    /** Newline-separated SAF content:// URIs (files / screenshots) attached as evidence. */
    val evidencePaths: String = "",
    /** Epoch millis of the last edit; 0 = never edited (use createdAt). */
    val updatedAt: Long = 0,
    /** Optional [ToolRun] this finding was spawned from (no FK so deleting a run keeps the finding). */
    val sourceRunId: Long? = null,
) {
    fun tagList(): List<String> = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    fun evidenceList(): List<String> = evidencePaths.lines().map { it.trim() }.filter { it.isNotEmpty() }
}

/**
 * A tool or workflow step launched against an engagement. VictorDrax hands commands to VictorSuite,
 * so [output] is only filled when the operator pastes it (or a future integration captures it).
 */
@Entity(
    tableName = "tool_runs",
    foreignKeys = [ForeignKey(entity = Engagement::class, parentColumns = ["id"], childColumns = ["engagementId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("engagementId")],
)
@kotlinx.serialization.Serializable
data class ToolRun(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val engagementId: Long,
    /** Tool name or workflow step label. */
    val label: String,
    val command: String,
    /** "tool" or "workflow:<playbook name>". */
    val source: String = "tool",
    val target: String = "",
    val startedAt: Long = System.currentTimeMillis(),
    val output: String = "",
)

@Entity(tableName = "playbooks")
@kotlinx.serialization.Serializable
data class Playbook(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val builtIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "playbook_steps",
    foreignKeys = [ForeignKey(entity = Playbook::class, parentColumns = ["id"], childColumns = ["playbookId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("playbookId")],
)
@kotlinx.serialization.Serializable
data class PlaybookStep(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playbookId: Long,
    val ordinal: Int,
    val label: String,
    /** Command template, may contain variables like {target}. */
    val command: String,
)

@Entity(
    tableName = "scan_imports",
    foreignKeys = [ForeignKey(entity = Engagement::class, parentColumns = ["id"], childColumns = ["engagementId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("engagementId")],
)
@kotlinx.serialization.Serializable
data class ScanImport(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val engagementId: Long,
    val name: String,
    /** nmap-xml / nmap-grep / json / csv */
    val format: String,
    /** Parsed rows serialized as JSON (List<ScanRow>). */
    val rowsJson: String,
    val importedAt: Long = System.currentTimeMillis(),
)

@Dao
interface EngagementDao {
    @Query("SELECT * FROM engagements ORDER BY createdAt DESC") fun all(): Flow<List<Engagement>>
    @Query("SELECT * FROM engagements WHERE id = :id") fun observe(id: Long): Flow<Engagement?>
    @Query("SELECT * FROM engagements WHERE id = :id") suspend fun get(id: Long): Engagement?
    @Query("SELECT * FROM engagements ORDER BY createdAt DESC") suspend fun allOnce(): List<Engagement>
    @Insert suspend fun insert(e: Engagement): Long
    @Update suspend fun update(e: Engagement)
    @Delete suspend fun delete(e: Engagement)

    @Query("SELECT * FROM notes WHERE engagementId = :id ORDER BY createdAt ASC") fun notes(id: Long): Flow<List<Note>>
    @Query("SELECT * FROM notes WHERE engagementId = :id ORDER BY createdAt ASC") suspend fun notesOnce(id: Long): List<Note>
    @Insert suspend fun addNote(n: Note): Long
    @Delete suspend fun deleteNote(n: Note)
    @Query("SELECT * FROM notes") suspend fun allNotesOnce(): List<Note>
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE) suspend fun upsert(e: Engagement): Long
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE) suspend fun upsertNote(n: Note): Long
    @Query("DELETE FROM engagements") suspend fun deleteAll()
}

@Dao
interface FindingDao {
    @Query("SELECT * FROM findings WHERE engagementId = :id ORDER BY createdAt DESC") fun forEngagement(id: Long): Flow<List<Finding>>
    @Query("SELECT * FROM findings WHERE engagementId = :id ORDER BY createdAt DESC") suspend fun forEngagementOnce(id: Long): List<Finding>
    @Insert suspend fun insert(f: Finding): Long
    @Update suspend fun update(f: Finding)
    @Delete suspend fun delete(f: Finding)
    @Query("SELECT * FROM findings") suspend fun allOnce(): List<Finding>
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE) suspend fun upsert(f: Finding): Long
    @Query("SELECT * FROM findings WHERE id = :id") suspend fun get(id: Long): Finding?
    @Query("SELECT COUNT(*) FROM findings WHERE engagementId = :id") suspend fun countFor(id: Long): Int
}

@Dao
interface ToolRunDao {
    @Query("SELECT * FROM tool_runs WHERE engagementId = :id ORDER BY startedAt DESC") fun forEngagement(id: Long): Flow<List<ToolRun>>
    @Query("SELECT * FROM tool_runs WHERE engagementId = :id ORDER BY startedAt ASC") suspend fun forEngagementOnce(id: Long): List<ToolRun>
    @Query("SELECT * FROM tool_runs WHERE id = :id") suspend fun get(id: Long): ToolRun?
    @Insert suspend fun insert(r: ToolRun): Long
    @Update suspend fun update(r: ToolRun)
    @Delete suspend fun delete(r: ToolRun)
}

@Dao
interface PlaybookDao {
    @Query("SELECT * FROM playbooks ORDER BY builtIn DESC, name ASC") fun all(): Flow<List<Playbook>>
    @Query("SELECT * FROM playbooks ORDER BY builtIn DESC, name ASC") suspend fun allOnce(): List<Playbook>
    @Query("SELECT * FROM playbooks WHERE id = :id") suspend fun get(id: Long): Playbook?
    @Query("SELECT * FROM playbook_steps WHERE playbookId = :id ORDER BY ordinal ASC") fun steps(id: Long): Flow<List<PlaybookStep>>
    @Query("SELECT * FROM playbook_steps WHERE playbookId = :id ORDER BY ordinal ASC") suspend fun stepsOnce(id: Long): List<PlaybookStep>
    @Query("SELECT COUNT(*) FROM playbooks WHERE builtIn = 1") suspend fun builtInCount(): Int
    @Insert suspend fun insert(p: Playbook): Long
    @Insert suspend fun insertStep(s: PlaybookStep): Long
    @Update suspend fun update(p: Playbook)
    @Delete suspend fun delete(p: Playbook)
    @Query("DELETE FROM playbook_steps WHERE playbookId = :id") suspend fun deleteSteps(id: Long)
    @Query("SELECT * FROM playbook_steps") suspend fun allStepsOnce(): List<PlaybookStep>
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE) suspend fun upsert(p: Playbook): Long
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE) suspend fun upsertStep(s: PlaybookStep): Long
    @Query("DELETE FROM playbooks WHERE builtIn = 0") suspend fun deleteUserPlaybooks()
}

@Dao
interface ScanDao {
    @Query("SELECT * FROM scan_imports WHERE engagementId = :id ORDER BY importedAt DESC") fun forEngagement(id: Long): Flow<List<ScanImport>>
    @Query("SELECT * FROM scan_imports WHERE engagementId = :id ORDER BY importedAt DESC") suspend fun forEngagementOnce(id: Long): List<ScanImport>
    @Query("SELECT * FROM scan_imports WHERE id = :id") suspend fun get(id: Long): ScanImport?
    @Insert suspend fun insert(s: ScanImport): Long
    @Delete suspend fun delete(s: ScanImport)
}

@Database(
    entities = [Engagement::class, Note::class, Finding::class, Playbook::class, PlaybookStep::class, ScanImport::class, ToolRun::class],
    version = 3, exportSchema = true,
)
abstract class AppDb : RoomDatabase() {
    abstract fun engagements(): EngagementDao
    abstract fun findings(): FindingDao
    abstract fun playbooks(): PlaybookDao
    abstract fun scans(): ScanDao
    abstract fun toolRuns(): ToolRunDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE engagements ADD COLUMN execSummary TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE TABLE IF NOT EXISTS findings (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, engagementId INTEGER NOT NULL, title TEXT NOT NULL, severity TEXT NOT NULL, cvssVector TEXT NOT NULL DEFAULT '', evidence TEXT NOT NULL DEFAULT '', remediation TEXT NOT NULL DEFAULT '', createdAt INTEGER NOT NULL, FOREIGN KEY(engagementId) REFERENCES engagements(id) ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_findings_engagementId ON findings(engagementId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS playbooks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, description TEXT NOT NULL DEFAULT '', builtIn INTEGER NOT NULL DEFAULT 0, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS playbook_steps (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, playbookId INTEGER NOT NULL, ordinal INTEGER NOT NULL, label TEXT NOT NULL, command TEXT NOT NULL, FOREIGN KEY(playbookId) REFERENCES playbooks(id) ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playbook_steps_playbookId ON playbook_steps(playbookId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS scan_imports (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, engagementId INTEGER NOT NULL, name TEXT NOT NULL, format TEXT NOT NULL, rowsJson TEXT NOT NULL, importedAt INTEGER NOT NULL, FOREIGN KEY(engagementId) REFERENCES engagements(id) ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_scan_imports_engagementId ON scan_imports(engagementId)")
            }
        }

        /** v3: findings manager columns + tool_runs table. Additive only; existing findings become OPEN. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("description", "tags", "target", "evidencePaths").forEach {
                    db.execSQL("ALTER TABLE findings ADD COLUMN $it TEXT NOT NULL DEFAULT ''")
                }
                db.execSQL("ALTER TABLE findings ADD COLUMN status TEXT NOT NULL DEFAULT 'OPEN'")
                db.execSQL("ALTER TABLE findings ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE findings ADD COLUMN sourceRunId INTEGER DEFAULT NULL")
                db.execSQL("CREATE TABLE IF NOT EXISTS tool_runs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, engagementId INTEGER NOT NULL, label TEXT NOT NULL, command TEXT NOT NULL, source TEXT NOT NULL, target TEXT NOT NULL, startedAt INTEGER NOT NULL, output TEXT NOT NULL, FOREIGN KEY(engagementId) REFERENCES engagements(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tool_runs_engagementId ON tool_runs(engagementId)")
            }
        }

        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "victorious.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { inst = it }
        }
    }
}
