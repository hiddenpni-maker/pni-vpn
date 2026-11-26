package ir.pnifox.core.database

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
import ir.pnifox.core.model.ProxyProfile
import ir.pnifox.core.model.ScanProvider
import ir.pnifox.core.model.ScanResultItem
import ir.pnifox.core.model.SubscriptionGroup
import ir.pnifox.core.model.TrafficChartPoint
import ir.pnifox.core.model.TrafficSummary
import ir.pnifox.core.model.WarpKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PnifoxDataRepository @Inject constructor(
    private val profileDao: ProfileDao,
    private val subscriptionDao: SubscriptionDao,
    private val scanResultDao: ScanResultDao,
    private val trafficStatsDao: TrafficStatsDao,
    private val warpKeyDao: WarpKeyDao,
) {
    fun observeProfiles(): Flow<List<ProxyProfile>> =
        profileDao.observeAll().map { rows -> rows.map { it.toModel() } }

    suspend fun getProfile(id: String): ProxyProfile? =
        profileDao.getById(id)?.toModel()

    suspend fun saveProfile(profile: ProxyProfile) {
        profileDao.upsert(ProfileEntity.fromModel(profile))
    }

    suspend fun saveProfiles(profiles: List<ProxyProfile>) {
        profileDao.upsertAll(profiles.map { ProfileEntity.fromModel(it) })
    }

    suspend fun deleteProfile(id: String) {
        profileDao.deleteById(id)
    }

    suspend fun deleteGroupProfiles(groupId: String) {
        profileDao.deleteByGroup(groupId)
    }

    fun observeSubscriptions(): Flow<List<SubscriptionGroup>> =
        subscriptionDao.observeAll().map { rows -> rows.map { it.toModel() } }

    suspend fun saveSubscription(sub: SubscriptionGroup) {
        subscriptionDao.upsert(SubscriptionEntity.fromModel(sub))
    }

    suspend fun deleteSubscription(id: String) {
        profileDao.deleteByGroup(id)
        subscriptionDao.deleteById(id)
    }

    fun observeScanResults(): Flow<List<ScanResultItem>> =
        scanResultDao.observeBestResults().map { rows -> rows.map { it.toModel() } }

    suspend fun replaceScanResults(provider: ScanProvider, results: List<ScanResultItem>) {
        scanResultDao.clearByProvider(provider.name)
        scanResultDao.insertAll(results.map { ScanResultEntity.fromModel(it) })
    }

    suspend fun clearScanResults() {
        scanResultDao.clearAll()
    }

    fun observeWarpKeys(): Flow<List<WarpKey>> =
        warpKeyDao.observeAll().map { rows -> rows.map { it.toModel() } }

    suspend fun saveWarpKey(key: WarpKey) {
        warpKeyDao.upsert(WarpKeyEntity.fromModel(key))
    }

    suspend fun recordTrafficDelta(rxDelta: Long, txDelta: Long) {
        if (rxDelta <= 0L && txDelta <= 0L) return
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val epochDay = startOfDayEpochMs(now)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val dayKey = dayFmt.format(Date(now))
        val key = "$dayKey-${hour.toString().padStart(2, '0')}"
        val existing = trafficStatsDao.getBucket(key)
        val updated = TrafficBucketEntity(
            dayHourKey = key,
            epochDay = epochDay,
            hourOfDay = hour,
            rxBytes = (existing?.rxBytes ?: 0L) + rxDelta.coerceAtLeast(0L),
            txBytes = (existing?.txBytes ?: 0L) + txDelta.coerceAtLeast(0L),
        )
        trafficStatsDao.upsertBucket(updated)
    }

    fun observeTrafficBuckets30Days(): Flow<TrafficSummary> {
        val now = System.currentTimeMillis()
        val todayStart = startOfDayEpochMs(now)
        val weekStart = todayStart - 6L * DAY_MS
        val monthStart = todayStart - 29L * DAY_MS

        return trafficStatsDao.observeSinceDay(monthStart).map { rows ->
            val todayRows = rows.filter { it.epochDay >= todayStart }
            val weekRows = rows.filter { it.epochDay >= weekStart }

            val todayRx = todayRows.sumOf { it.rxBytes }
            val todayTx = todayRows.sumOf { it.txBytes }
            val weekRx = weekRows.sumOf { it.rxBytes }
            val weekTx = weekRows.sumOf { it.txBytes }
            val monthRx = rows.sumOf { it.rxBytes }
            val monthTx = rows.sumOf { it.txBytes }

            val points = (0..6).map { offset ->
                val dayEpoch = todayStart - (6 - offset) * DAY_MS
                val dayRows = rows.filter { it.epochDay == dayEpoch }
                val label = shortDayFmt.format(Date(dayEpoch))
                TrafficChartPoint(
                    label = label,
                    rxBytes = dayRows.sumOf { it.rxBytes },
                    txBytes = dayRows.sumOf { it.txBytes },
                )
            }

            TrafficSummary(
                todayRxBytes = todayRx,
                todayTxBytes = todayTx,
                weekRxBytes = weekRx,
                weekTxBytes = weekTx,
                monthRxBytes = monthRx,
                monthTxBytes = monthTx,
                chartPoints = points,
            )
        }
    }

    suspend fun resetTrafficStats() {
        trafficStatsDao.resetAll()
    }

    private fun startOfDayEpochMs(nowMs: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private companion object {
        const val DAY_MS = 86_400_000L
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val shortDayFmt = SimpleDateFormat("MM/dd", Locale.US)
    }
}
