package net.fribbynetwork.iamhere.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import android.content.Context
import kotlinx.coroutines.flow.Flow

@Dao
interface PoiDao {
    @Query("SELECT * FROM poi ORDER BY name COLLATE NOCASE")
    fun all(): Flow<List<Poi>>

    @Query("SELECT * FROM poi WHERE id = :id")
    suspend fun byId(id: Long): Poi?

    @Insert
    suspend fun insert(poi: Poi): Long

    @Update
    suspend fun update(poi: Poi)

    @Query("DELETE FROM poi WHERE id = :id")
    suspend fun delete(id: Long)

    /** Per il salvataggio: serve la lista in un colpo solo, non un flusso. */
    @Query("SELECT * FROM poi ORDER BY name COLLATE NOCASE")
    suspend fun tutti(): List<Poi>

    @Query("DELETE FROM poi")
    suspend fun cancellaTutti()
}

@Dao
interface TripDao {
    @Insert
    suspend fun insert(trip: Trip)

    @Update
    suspend fun update(trip: Trip)

    @Query("SELECT * FROM trip ORDER BY startedAt DESC LIMIT 100")
    fun recent(): Flow<List<Trip>>

    @Query("SELECT * FROM trip WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun openTrip(): Trip?
}

@Dao
interface SampleDao {
    @Insert
    suspend fun insert(s: Sample): Long

    @Update
    suspend fun update(s: Sample)

    /** In coda = mai inviato. Ordine cronologico: il server li riceve in fila. */
    @Query("SELECT * FROM outbox WHERE sentAt IS NULL ORDER BY fixTime ASC, id ASC LIMIT :limit")
    suspend fun pending(limit: Int): List<Sample>

    @Query("SELECT COUNT(*) FROM outbox WHERE sentAt IS NULL")
    fun pendingCount(): Flow<Int>

    @Query("SELECT * FROM outbox WHERE tripId = :tripId ORDER BY fixTime ASC")
    fun forTrip(tripId: Long): Flow<List<Sample>>

    @Query("SELECT * FROM outbox ORDER BY id DESC LIMIT :limit")
    fun latest(limit: Int): Flow<List<Sample>>

    @Query("DELETE FROM outbox WHERE sentAt IS NOT NULL AND sentAt < :before")
    suspend fun purgeSentBefore(before: Long)

    @Query("DELETE FROM outbox")
    suspend fun clearAll()
}

@Database(entities = [Poi::class, Trip::class, Sample::class], version = 1, exportSchema = false)
abstract class Db : RoomDatabase() {
    abstract fun poi(): PoiDao
    abstract fun trips(): TripDao
    abstract fun samples(): SampleDao

    companion object {
        @Volatile private var instance: Db? = null
        fun get(context: Context): Db = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, Db::class.java, "tracker.db"
            ).fallbackToDestructiveMigration().build().also { instance = it }
        }
    }
}
