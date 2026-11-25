package ir.pni.vpn

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import ir.pni.vpn.core.CoreBridge

class PniApplication : Application() {

    companion object {
        const val CHANNEL_VPN = "pni_vpn_channel"
        const val CHANNEL_TOOLS = "pni_tools_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        CoreBridge.initAssets(this)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_VPN,
                    getString(R.string.notif_channel_vpn),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.notif_channel_vpn_desc)
                    setShowBadge(false)
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_TOOLS,
                    getString(R.string.notif_channel_tools),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.notif_channel_tools_desc)
                    setShowBadge(false)
                }
            )
        }
    }
}
