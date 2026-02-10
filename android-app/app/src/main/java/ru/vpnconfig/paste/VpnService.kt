package ru.vpnconfig.paste

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import java.io.FileOutputStream

/**
 * VPN-сервис: поднимает TUN, передаёт трафик в ядро Xray (libv2ray.aar).
 * Конфиг и путь к ассетам передаются через intent.
 */
class VpnServiceImpl : VpnService() {

    private var tunFd: ParcelFileDescriptor? = null
    private var coreController: Any? = null
    private var stopCallback: (() -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            stopVpn()
            stopSelf()
            return START_NOT_STICKY
        }
        val config = intent?.getStringExtra(EXTRA_CONFIG) ?: return START_NOT_STICKY
        startForeground(NOTIFICATION_ID, buildNotification())
        startVpn(config)
        return START_STICKY
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun startVpn(configJson: String) {
        try {
            val envPath = prepareAssets()
            initCore(envPath)
            val tun = establishTun()
            tunFd = tun
            val fd = tun?.fd?.toInt() ?: 0
            startCore(configJson, fd)
        } catch (e: Throwable) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun prepareAssets(): String {
        val dir = filesDir
        val geoip = java.io.File(dir, "geoip.dat")
        val geosite = java.io.File(dir, "geosite.dat")
        if (!geoip.exists()) copyAsset("geoip.dat", geoip)
        if (!geosite.exists()) copyAsset("geosite.dat", geosite)
        return dir.absolutePath
    }

    private fun copyAsset(name: String, out: java.io.File) {
        try {
            assets.open(name).use { input ->
                FileOutputStream(out).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (_: Exception) {
            // Если ассетов нет — ядро может работать без них (без geo-маршрутизации)
        }
    }

    private fun establishTun(): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession("VPN Config")
            .setMtu(1500)
            .addAddress("10.0.0.2", 30)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .addDnsServer("1.1.1.1")
            .setBlocking(false)
        return builder.establish()
    }

    private fun initCore(envPath: String) {
        val libName = listOf("libv2ray.Libv2ray", "go.libv2ray.Libv2ray").firstOrNull { name ->
            try { Class.forName(name); true } catch (_: ClassNotFoundException) { false }
        } ?: throw IllegalStateException("Добавьте libv2ray.aar в app/libs/ (см. README)")
        val libClass = Class.forName(libName)
        val method = libClass.getMethod("initCoreEnv", String::class.java, String::class.java)
        method.invoke(null, envPath, "")
    }

    private fun startCore(configJson: String, tunFd: Int) {
        val libName = listOf("libv2ray.Libv2ray", "go.libv2ray.Libv2ray").firstOrNull { name ->
            try { Class.forName(name); true } catch (_: ClassNotFoundException) { false }
        } ?: throw IllegalStateException("Добавьте libv2ray.aar в app/libs/ (см. README)")
        val handlerName = listOf("libv2ray.CoreCallbackHandler", "go.libv2ray.CoreCallbackHandler").firstOrNull { name ->
            try { Class.forName(name); true } catch (_: ClassNotFoundException) { false }
        } ?: throw IllegalStateException("libv2ray.aar несовместим")
        val handlerClass = Class.forName(handlerName)
        val proxy = java.lang.reflect.Proxy.newProxyInstance(
            handlerClass.classLoader,
            arrayOf(handlerClass)
        ) { _, method, args ->
            val name = method.name.lowercase()
            if (name == "startup" || name == "shutdown" || name.contains("emitstatus")) 0 else null
        }
        val libClass = Class.forName(libName)
        val newController = libClass.getMethod("newCoreController", handlerClass)
        val controller = newController.invoke(null, proxy)
        coreController = controller
        stopCallback = {
            try {
                controller?.javaClass?.getMethod("stopLoop")?.invoke(controller)
            } catch (_: Throwable) {}
        }
        val startLoop = controller?.javaClass?.getMethod("startLoop", String::class.java, Int::class.javaPrimitiveType)
            ?: controller?.javaClass?.getMethod("startLoop", String::class.java, Int::class.java)
        startLoop?.invoke(controller, configJson, tunFd)
    }

    private fun stopVpn() {
        try {
            stopCallback?.invoke()
        } catch (_: Throwable) {}
        stopCallback = null
        coreController = null
        try {
            tunFd?.close()
        } catch (_: Throwable) {}
        tunFd = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "VPN", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pending = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val disconnect = PendingIntent.getService(
            this, 0,
            Intent(this, VpnServiceImpl::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VPN включён")
            .setContentText("Трафик идёт через прокси")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pending)
            .addAction(android.R.drawable.ic_delete, "Отключить", disconnect)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_DISCONNECT = "ru.vpnconfig.paste.DISCONNECT"
        const val EXTRA_CONFIG = "config_json"
        private const val CHANNEL_ID = "vpn_channel"
        private const val NOTIFICATION_ID = 1
    }
}
