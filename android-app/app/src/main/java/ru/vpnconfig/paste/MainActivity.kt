package ru.vpnconfig.paste

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import ru.vpnconfig.paste.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    private val vpnPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && pendingConfig != null) {
            val link = pendingConfig!!
            val config = ConfigBuilder.buildFromSsLink(link)
            if (config != null) startVpnService(config, link)
            pendingConfig = null
        } else if (pendingConfig != null) {
            Toast.makeText(this, "Нужно разрешить VPN для подключения", Toast.LENGTH_LONG).show()
            pendingConfig = null
        }
    }

    private var pendingConfig: String? = null  // при ss:// храним ссылку

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = getSharedPreferences("vpn_config", Context.MODE_PRIVATE)

        intent?.data?.toString()?.let { link ->
            binding.etLink.setText(link)
        } ?: run {
            prefs.getString("last_link", null)?.takeIf { it.isNotBlank() }?.let {
                binding.etLink.setText(it)
            }
        }

        binding.btnApply.setOnClickListener { applyOrConnect() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.toString()?.let { binding.etLink.setText(it) }
    }

    private fun applyOrConnect() {
        val link = binding.etLink.text?.toString()?.trim() ?: ""
        if (link.isBlank()) {
            Toast.makeText(this, R.string.toast_need_link, Toast.LENGTH_SHORT).show()
            return
        }

        // Пробуем собрать конфиг и подключиться встроенным ядром (если есть libv2ray.aar)
        if (link.startsWith("ss://")) {
            saveLastLink(link)
            val config = ConfigBuilder.buildFromSsLink(link)
            if (config != null && hasLibV2ray()) {
                pendingConfig = link  // храним ссылку для передачи в сервис (и fallback при ошибке)
                val intent = VpnService.prepare(this)
                if (intent != null) {
                    vpnPermissionLauncher.launch(intent)
                } else {
                    startVpnService(config, link)
                }
                return
            }
        }

        // Иначе: копируем и открываем v2rayNG
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("vpn_config", link))
        val encoded = Uri.encode(link)
        val v2rayIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("v2rayng://install-config/?url=$encoded")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(v2rayIntent)
            Toast.makeText(this, R.string.toast_open_v2ray, Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW).setPackage("com.v2ray.ang").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                Toast.makeText(this, R.string.toast_copied, Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
                Toast.makeText(this, R.string.toast_no_app, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun hasLibV2ray(): Boolean {
        return listOf("go.libv2ray.Libv2ray", "libv2ray.Libv2ray").any { name ->
            try {
                Class.forName(name)
                true
            } catch (_: ClassNotFoundException) {
                false
            }
        }
    }

    private fun saveLastLink(link: String) {
        prefs.edit().putString("last_link", link.trim()).apply()
    }

    private fun startVpnService(config: String, link: String) {
        startForegroundService(Intent(this, VpnServiceImpl::class.java).apply {
            putExtra(VpnServiceImpl.EXTRA_CONFIG, config)
            putExtra(VpnServiceImpl.EXTRA_LINK, link)
        })
        Toast.makeText(this, "VPN запущен", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onResume() {
        super.onResume()
        // Если сервис не смог запустить ядро — пришёл сюда с флагом, открываем v2rayNG
        intent?.takeIf { it.getBooleanExtra(VpnServiceImpl.EXTRA_VPN_FAILED, false) }?.let { i ->
            i.removeExtra(VpnServiceImpl.EXTRA_VPN_FAILED)
            val link = i.getStringExtra(VpnServiceImpl.EXTRA_LINK)
            Toast.makeText(this, "Встроенное ядро недоступно. Откройте v2rayNG — ссылка вставлена.", Toast.LENGTH_LONG).show()
            if (!link.isNullOrBlank()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("vpn_config", link))
                val v2rayIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("v2rayng://install-config/?url=${Uri.encode(link)}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try { startActivity(v2rayIntent) } catch (_: Exception) {
                    try { startActivity(Intent(Intent.ACTION_VIEW).setPackage("com.v2ray.ang").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
                }
            }
        }
    }
}
