package com.victorious.andrax.db

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "engagements")
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
) {
    val isAuthorized: Boolean get() = authorizedBy.isNotBlank() && authorizedDate > 0 && authDocUri.isNotBlank()
    fun scopeList(): List<String> = scope.lines().map { it.trim() }.filter { it.isNotEmpty() }
}

@Entity(
    tableName = "notes",
    foreignKeys = [ForeignKey(entity = Engagement::class, parentColumns = ["id"], childColumns = ["engagementId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("engagementId")],
)
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val engagementId: Long,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface EngagementDao {
    @Query("SELECT * FROM engagements ORDER BY createdAt DESC") fun all(): Flow<List<Engagement>>
    @Query("SELECT * FROM engagements WHERE id = :id") fun observe(id: Long): Flow<Engagement?>
    @Query("SELECT * FROM engagements WHERE id = :id") suspend fun get(id: Long): Engagement?
    @Insert suspend fun insert(e: Engagement): Long
    @Update suspend fun update(e: Engagement)
    @Delete suspend fun delete(e: Engagement)

    @Query("SELECT * FROM notes WHERE engagementId = :id ORDER BY createdAt ASC") fun notes(id: Long): Flow<List<Note>>
    @Query("SELECT * FROM notes WHERE engagementId = :id ORDER BY createdAt ASC") suspend fun notesOnce(id: Long): List<Note>
    @Insert suspend fun addNote(n: Note): Long
    @Delete suspend fun deleteNote(n: Note)
}

@Database(entities = [Engagement::class, Note::class], version = 1, exportSchema = true)
abstract class AppDb : RoomDatabase() {
    abstract fun engagements(): EngagementDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "victorious.db").build().also { inst = it }
        }
    }
}
