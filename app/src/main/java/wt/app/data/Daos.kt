package wt.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface WeightDao {
    @Query("SELECT * FROM weight ORDER BY date")
    fun observeAll(): Flow<List<WeightEntity>>

    @Query("SELECT * FROM weight ORDER BY date")
    suspend fun all(): List<WeightEntity>

    @Query("SELECT * FROM weight WHERE date = :date")
    suspend fun get(date: LocalDate): WeightEntity?

    @Upsert suspend fun upsert(entry: WeightEntity)

    @Insert suspend fun insertAll(entries: List<WeightEntity>)

    @Query("DELETE FROM weight WHERE date = :date")
    suspend fun delete(date: LocalDate)

    @Query("DELETE FROM weight")
    suspend fun deleteAll()
}

@Dao
interface BodyCompDao {
    @Query("SELECT * FROM body_comp ORDER BY date")
    fun observeAll(): Flow<List<BodyCompEntity>>

    @Query("SELECT * FROM body_comp ORDER BY date")
    suspend fun all(): List<BodyCompEntity>

    @Upsert suspend fun upsert(entry: BodyCompEntity)

    @Insert suspend fun insertAll(entries: List<BodyCompEntity>)

    @Query("DELETE FROM body_comp WHERE date = :date")
    suspend fun delete(date: LocalDate)

    @Query("DELETE FROM body_comp")
    suspend fun deleteAll()
}

@Dao
interface RunDao {
    @Query("SELECT * FROM run ORDER BY date IS NULL DESC, date DESC, id DESC")
    fun observeAll(): Flow<List<RunEntity>>

    @Query("SELECT * FROM run ORDER BY date, id")
    suspend fun all(): List<RunEntity>

    @Upsert suspend fun upsert(run: RunEntity): Long

    /** Returns -1 for runs whose externalId already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringDuplicates(runs: List<RunEntity>): List<Long>

    @Delete suspend fun delete(run: RunEntity)

    @Query("DELETE FROM run")
    suspend fun deleteAll()

    /** Deletes undated manual runs with exactly this distance and time (the old sample runs). */
    @Query("DELETE FROM run WHERE date IS NULL AND source = 'manual' AND km = :km AND durationSec = :durationSec")
    suspend fun deleteUndatedManual(km: Double, durationSec: Int)
}

@Dao
interface RestDayDao {
    @Query("SELECT * FROM rest_day ORDER BY date")
    fun observeAll(): Flow<List<RestDayEntity>>

    @Query("SELECT * FROM rest_day ORDER BY date")
    suspend fun all(): List<RestDayEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(day: RestDayEntity)

    @Query("DELETE FROM rest_day WHERE date = :date")
    suspend fun delete(date: LocalDate)

    @Query("DELETE FROM rest_day")
    suspend fun deleteAll()
}

@Dao
abstract class PlanDao {
    @Query("SELECT c.* FROM plan_checkpoint c JOIN plan p ON p.id = c.planId WHERE p.active = 1 ORDER BY c.date")
    abstract fun observeActiveCheckpoints(): Flow<List<CheckpointEntity>>

    @Query("SELECT * FROM plan ORDER BY id DESC")
    abstract fun observePlans(): Flow<List<PlanEntity>>

    @Query("SELECT * FROM plan ORDER BY id")
    abstract suspend fun allPlans(): List<PlanEntity>

    @Query("SELECT * FROM plan_checkpoint ORDER BY planId, date")
    abstract suspend fun allCheckpoints(): List<CheckpointEntity>

    @Insert abstract suspend fun insertPlan(plan: PlanEntity): Long

    @Insert abstract suspend fun insertCheckpoints(points: List<CheckpointEntity>)

    @Query("UPDATE plan SET active = 0")
    abstract suspend fun deactivateAll()

    @Query("DELETE FROM plan_checkpoint WHERE planId = :planId")
    abstract suspend fun clearCheckpoints(planId: Long)

    @Query("SELECT id FROM plan WHERE active = 1 LIMIT 1")
    abstract suspend fun activePlanId(): Long?

    /** Checkpoints cascade-delete with their plan. */
    @Query("DELETE FROM plan")
    abstract suspend fun deleteAll()

    /** Makes a new plan active; older plans are kept as history. */
    @Transaction
    open suspend fun activateNewPlan(plan: PlanEntity, points: List<CheckpointEntity>) {
        deactivateAll()
        val id = insertPlan(plan.copy(active = true))
        insertCheckpoints(points.map { it.copy(planId = id) })
    }

    /** Edits the active plan's checkpoints in place. */
    @Transaction
    open suspend fun replaceActiveCheckpoints(points: List<CheckpointEntity>) {
        val id = activePlanId() ?: return
        clearCheckpoints(id)
        insertCheckpoints(points.map { it.copy(planId = id) })
    }
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun get(): ProfileEntity?

    @Upsert suspend fun upsert(profile: ProfileEntity)
}
