package ir.pnifox.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ir.pnifox.core.database.entity.ProfileEntity
import ir.pnifox.core.database.entity.ScanResultEntity
import ir.pnifox.core.database.entity.SubscriptionEntity
import ir.pnifox.core.database.entity.TrafficBucketEntity
import ir.pnifox.core.database.entity.WarpKeyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY isPinned DESC, updatedAtEpochMs DESC")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(profiles: List<ProfileEntity>)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM profiles WHERE groupId = :groupId")
    suspend fun deleteByGroup(groupId: String)
}

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY lastUpdatedEpochMs DESC")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(sub: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ScanResultDao {
    @Query("SELECT * FROM scan_results ORDER BY stabilityScore DESC, latencyMs ASC LIMIT 120")
    fun observeBestResults(): Flow<List<ScanResultEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(results: List<ScanResultEntity>)

    @Query("DELETE FROM scan_results WHERE providerName = :providerName")
    suspend fun clearByProvider(providerName: String)

    @Query("DELETE FROM scan_results")
    suspend fun clearAll()
}

@Dao
interface TrafficStatsDao {
    @Query("SELECT * FROM traffic_buckets WHERE epochDay >= :sinceEpochDay ORDER BY epochDay ASC, hourOfDay ASC")
    fun observeSinceDay(sinceEpochDay: Long): Flow<List<TrafficBucketEntity>>

    @Query("SELECT * FROM traffic_buckets WHERE dayHourKey = :key LIMIT 1")
    suspend fun getBucket(key: String): TrafficBucketEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBucket(bucket: TrafficBucketEntity)

    @Query("DELETE FROM traffic_buckets")
    suspend fun resetAll()
}

@Dao
interface WarpKeyDao {
    @Query("SELECT * FROM warp_keys ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<WarpKeyEntity>>

    @Query("SELECT * FROM warp_keys WHERE protocolTag = :protocolTag ORDER BY createdAtEpochMs DESC LIMIT 1")
    suspend fun getLatestForProtocol(protocolTag: String): WarpKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WarpKeyEntity)

    @Query("DELETE FROM warp_keys WHERE protocolTag = :protocolTag")
    suspend fun clearProtocol(protocolTag: String)
}
