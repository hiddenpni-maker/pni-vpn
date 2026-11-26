package ir.pnifox.core.database.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ir.pnifox.core.database.PnifoxDatabase
import ir.pnifox.core.database.dao.ProfileDao
import ir.pnifox.core.database.dao.ScanResultDao
import ir.pnifox.core.database.dao.SubscriptionDao
import ir.pnifox.core.database.dao.TrafficStatsDao
import ir.pnifox.core.database.dao.WarpKeyDao
import javax.inject.Singleton

private val Context.pnifoxDataStore: DataStore<Preferences> by preferencesDataStore(name = "pnifox_prefs")

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.pnifoxDataStore

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PnifoxDatabase =
        Room.databaseBuilder(
            context,
            PnifoxDatabase::class.java,
            "pnifox_main.db",
        ).fallbackToDestructiveMigration().build()

    @Provides
    fun provideProfileDao(db: PnifoxDatabase): ProfileDao = db.profileDao()

    @Provides
    fun provideSubscriptionDao(db: PnifoxDatabase): SubscriptionDao = db.subscriptionDao()

    @Provides
    fun provideScanResultDao(db: PnifoxDatabase): ScanResultDao = db.scanResultDao()

    @Provides
    fun provideTrafficStatsDao(db: PnifoxDatabase): TrafficStatsDao = db.trafficStatsDao()

    @Provides
    fun provideWarpKeyDao(db: PnifoxDatabase): WarpKeyDao = db.warpKeyDao()
}
