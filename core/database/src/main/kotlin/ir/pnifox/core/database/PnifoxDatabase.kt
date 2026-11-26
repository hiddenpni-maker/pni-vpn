package ir.pnifox.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import ir.pnifox.core.database.dao.ProfileDao
import ir.pnifox.core.database.dao.ScanResultDao
import ir.pnifox.core.database.dao.SubscriptionDao
import ir.pnifox.core.database.dao.TrafficStatsDao
import ir.pnifox.core.database.dao.WarpKeyDao
import ir.pnifox.core.database.entity.ProfileEntity
import ir.pnifox.core.database.entity.ScanResultEntity
import ir.pnifox.core.database.entity.SubscriptionEntity
import ir.pnifox.core.database.entity.TrafficBucketEntity
import ir.pnifox.core.database.entity.WarpKeyEntity

@Database(
    entities = [
        ProfileEntity::class,
        SubscriptionEntity::class,
        ScanResultEntity::class,
        TrafficBucketEntity::class,
        WarpKeyEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class PnifoxDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun scanResultDao(): ScanResultDao
    abstract fun trafficStatsDao(): TrafficStatsDao
    abstract fun warpKeyDao(): WarpKeyDao
}
