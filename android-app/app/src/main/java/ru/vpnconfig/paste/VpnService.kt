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
import android.os.Handler
import android.os.Looper
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * VPN-сервис: поднимает TUN, передаёт трафик в ядро Xray (libv2ray.aar).
 * Конфиг и путь к ассетам передаются через intent.
 */
class VpnServiceImpl : VpnService() {

    private var tunFd: ParcelFileDescriptor? = null
    private var coreController: Any? = null
    private var stopCallback: (() -> Unit)? = null
    private val coreRunning = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

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
        val link = intent?.getStringExtra(EXTRA_LINK)
        startForeground(NOTIFICATION_ID, buildNotification())
        try {
            startVpn(config, link)
        } catch (e: Throwable) {
            e.printStackTrace()
            notifyVpnFailed(link)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun startVpn(configJson: String, link: String?) {
        val envPath = prepareAssets()
        initCore(envPath)
        val tun = establishTun()
        tunFd = tun
        val fd = tun?.fd?.toInt() ?: 0
        startCore(configJson, fd, link)
    }

    private fun notifyVpnFailed(link: String?) {
        val i = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_VPN_FAILED, true)
            putExtra(EXTRA_LINK, link)
        }
        startActivity(i)
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
        val libName = listOf("go.libv2ray.Libv2ray", "libv2ray.Libv2ray").firstOrNull { name ->
            try { Class.forName(name); true } catch (_: ClassNotFoundException) { false }
        } ?: throw IllegalStateException("Добавьте libv2ray.aar в app/libs/ (см. README)")
        val libClass = Class.forName(libName)
        val method = libClass.methods.firstOrNull { it.name.equals("initCoreEnv", ignoreCase = true) && it.parameterCount == 2 }
            ?: throw IllegalStateException("libv2ray.aar: initCoreEnv не найден")
        method.invoke(null, envPath, "")
    }

    private fun startCore(configJson: String, fd: Int, link: String?) {
        val libName = listOf("go.libv2ray.Libv2ray", "libv2ray.Libv2ray").firstOrNull { name ->
            try { Class.forName(name); true } catch (_: ClassNotFoundException) { false }
        } ?: throw IllegalStateException("Добавьте libv2ray.aar в app/libs/ (см. README)")
        val handlerName = listOf("go.libv2ray.CoreCallbackHandler", "libv2ray.CoreCallbackHandler").firstOrNull { name ->
            try { Class.forName(name); true } catch (_: ClassNotFoundException) { false }
        } ?: throw IllegalStateException("libv2ray.aar несовместим")
        val handlerClass = Class.forName(handlerName)
        val proxy = java.lang.reflect.Proxy.newProxyInstance(
            handlerClass.classLoader,
            arrayOf(handlerClass)
        ) { _, method, args ->
            val ret = method.returnType
            if (ret == Int::class.javaPrimitiveType || ret == Int::class.java) 0 else null
        }
        val libClass = Class.forName(libName)
        val newController = libClass.getMethod("newCoreController", handlerClass)
        val controller = newController.invoke(null, proxy)
        coreController = controller
        stopCallback = {
            coreRunning.set(false)
            try {
                controller?.javaClass?.getMethod("stopLoop")?.invoke(controller)
            } catch (_: Throwable) {}
        }
        val startLoop = controller?.javaClass?.methods?.firstOrNull { m ->
            m.name == "startLoop" && m.parameterCount == 2 && m.parameterTypes.getOrNull(0) == String::class.java
        } ?: throw IllegalStateException("libv2ray.aar: метод startLoop не найден")
        val param1 = configJson
        val param2 = if (startLoop.parameterTypes.getOrNull(1) == Integer::class.java) Integer.valueOf(fd) else fd
        coreRunning.set(true)
        Thread {
            try {
                startLoop.invoke(controller, param1, param2)
            } catch (e: Throwable) {
                e.printStackTrace()
                if (coreRunning.getAndSet(false)) {
                    mainHandler.post {
                        notifyVpnFailed(link)
                        stopSelf()
                    }
                }
            }
        }.start()
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
        const val EXTRA_LINK = "ss_link"
        const val EXTRA_VPN_FAILED = "vpn_failed"
        private const val CHANNEL_ID = "vpn_channel"
        private const val NOTIFICATION_ID = 1
    }
}
