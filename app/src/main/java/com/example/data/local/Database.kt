package com.example.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "ai_portraits")
data class AIPortrait(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val templateName: String,
    val filePath: String,
    val timestamp: Long = System.currentTimeMillis(),
    val promptUsed: String,
    val modelUsed: String,
    val isWatermarked: Boolean
)

@Entity(tableName = "user_metrics")
data class UserMetrics(
    @PrimaryKey val id: Int = 1,
    val coins: Int = 3,
    val isPremium: Boolean = false,
    val customApiKey: String = "",
    val lastResetTimestamp: Long = System.currentTimeMillis()
)

@Dao
interface PortraitDao {
    @Query("SELECT * FROM ai_portraits ORDER BY timestamp DESC")
    fun getAllPortraits(): Flow<List<AIPortrait>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPortrait(portrait: AIPortrait)

    @Query("DELETE FROM ai_portraits WHERE id = :id")
    suspend fun deletePortraitById(id: Int)
}

@Dao
interface UserMetricsDao {
    @Query("SELECT * FROM user_metrics WHERE id = 1 LIMIT 1")
    fun getUserMetrics(): Flow<UserMetrics?>

    @Query("SELECT * FROM user_metrics WHERE id = 1 LIMIT 1")
    suspend fun getUserMetricsDirect(): UserMetrics?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserMetrics(metrics: UserMetrics)

    @Update
    suspend fun updateUserMetrics(metrics: UserMetrics)
}

@Database(entities = [AIPortrait::class, UserMetrics::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun portraitDao(): PortraitDao
    abstract fun userMetricsDao(): UserMetricsDao
}
