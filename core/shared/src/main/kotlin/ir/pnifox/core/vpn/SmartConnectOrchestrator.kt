package ir.pnifox.core.vpn

import ir.pnifox.core.model.ScenarioGoalId
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.ln
import kotlin.math.sqrt

@Serializable
enum class StrategyArm(
    val titleEn: String,
    val titleFa: String,
) {
    AETHER_MASQUE_H2("AETHER MASQUE H2", "مسک H2"),
    AETHER_MASQUE_H3("AETHER MASQUE H3", "مسک H3 (QUIC v2)"),
    AETHER_WG_NOIZE("AETHER WireGuard+Noise", "وایرگارد + نویز"),
    AETHER_PSIPHON("AETHER Psiphon Clean IP", "سایفون آی‌پی تمیز"),
    LOCAL_DPI_DESYNC("Local DPI Desync", "ضدDPI محلی (بدون سرور)"),
    GAMING_DNS("Gaming Anti-Sanction DNS", "DNS ضدتحریم گیم"),
    MHR_RELAY("MHR Relay Bridge", "رله MHR"),
}

@Serializable
data class ArmStat(
    val attempts: Int = 0,
    val successes: Int = 0,
    val avgLatencyMs: Double = 350.0,
)

@Serializable
data class BanditState(
    val totalPulls: Int = 0,
    val stats: Map<String, ArmStat> = emptyMap(),
)

/**
 * Multi-Armed Bandit (UCB1 + Goal Prior) orchestrator for Smart Auto-Connect.
 * Remembers which strategy works reliably on the user's current network & ranks
 * fallback candidates objectively with real connection outcomes.
 */
object SmartConnectOrchestrator {

    private val json = Json { ignoreUnknownKeys = true }

    fun rankedArms(
        goal: ScenarioGoalId? = null,
        stateJson: String = "",
    ): List<StrategyArm> {
        val state = parseState(stateJson)
        val total = state.totalPulls.coerceAtLeast(1)

        return StrategyArm.entries.sortedByDescending { arm ->
            val st = state.stats[arm.name] ?: ArmStat()
            val priorBonus = goalPrior(goal, arm)
            val empirical = if (st.attempts == 0) {
                0.75 + priorBonus
            } else {
                val successRate = st.successes.toDouble() / st.attempts.toDouble()
                val latencyPenalty = (st.avgLatencyMs / 2500.0).coerceIn(0.0, 0.35)
                val ucbExplore = 0.45 * sqrt(ln(total.toDouble() + 1.0) / st.attempts.toDouble())
                successRate - latencyPenalty + ucbExplore + priorBonus
            }
            empirical
        }
    }

    fun recordOutcome(
        stateJson: String,
        arm: StrategyArm,
        success: Boolean,
        latencyMs: Long,
    ): String {
        val prev = parseState(stateJson)
        val old = prev.stats[arm.name] ?: ArmStat()
        val nextAttempts = old.attempts + 1
        val nextSuccesses = old.successes + if (success) 1 else 0
        val sampleLat = latencyMs.coerceIn(20L, 10_000L).toDouble()
        val nextLat = if (old.attempts == 0) sampleLat else (old.avgLatencyMs * 0.65 + sampleLat * 0.35)
        val nextMap = prev.stats.toMutableMap()
        nextMap[arm.name] = ArmStat(
            attempts = nextAttempts,
            successes = nextSuccesses,
            avgLatencyMs = nextLat,
        )
        return json.encodeToString(
            BanditState(
                totalPulls = prev.totalPulls + 1,
                stats = nextMap,
            ),
        )
    }

    private fun parseState(raw: String): BanditState =
        if (raw.isBlank()) BanditState()
        else runCatching { json.decodeFromString<BanditState>(raw) }.getOrDefault(BanditState())

    private fun goalPrior(goal: ScenarioGoalId?, arm: StrategyArm): Double = when (goal) {
        ScenarioGoalId.GAMING_LOW_PING -> when (arm) {
            StrategyArm.GAMING_DNS -> 1.4
            StrategyArm.LOCAL_DPI_DESYNC -> 1.1
            StrategyArm.AETHER_MASQUE_H3 -> 0.8
            else -> 0.0
        }
        ScenarioGoalId.STREAMING_VIDEO -> when (arm) {
            StrategyArm.AETHER_MASQUE_H3 -> 1.2
            StrategyArm.AETHER_MASQUE_H2 -> 1.0
            else -> 0.1
        }
        ScenarioGoalId.AI_REGION_SENSITIVE -> when (arm) {
            StrategyArm.AETHER_PSIPHON -> 1.3
            StrategyArm.MHR_RELAY -> 0.7
            StrategyArm.AETHER_MASQUE_H2 -> 0.4
            else -> 0.0
        }
        ScenarioGoalId.LOCAL_DPI_ZERO_SERVER -> when (arm) {
            StrategyArm.LOCAL_DPI_DESYNC -> 1.5
            StrategyArm.GAMING_DNS -> 0.8
            else -> 0.0
        }
        ScenarioGoalId.HEAVY_DPI_BYPASS -> when (arm) {
            StrategyArm.MHR_RELAY -> 1.1
            StrategyArm.AETHER_WG_NOIZE -> 1.0
            StrategyArm.AETHER_PSIPHON -> 0.8
            else -> 0.2
        }
        else -> when (arm) {
            StrategyArm.AETHER_MASQUE_H2 -> 0.6
            StrategyArm.AETHER_MASQUE_H3 -> 0.5
            StrategyArm.LOCAL_DPI_DESYNC -> 0.35
            else -> 0.15
        }
    }
}
