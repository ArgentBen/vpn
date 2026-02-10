package ru.vpnconfig.paste

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import ru.vpnconfig.paste.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val vpnPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && pendingConfig != null) {
            startVpnService(pendingConfig!!)
            pendingConfig = null
        } else if (pendingConfig != null) {
            Toast.makeText(this, "Нужно разрешить VPN для подключения", Toast.LENGTH_LONG).show()
            pendingConfig = null
        }
    }

    private var pendingConfig: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        intent?.data?.toString()?.let { link ->
            binding.etLink.setText(link)
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
            val config = ConfigBuilder.buildFromSsLink(link)
            if (config != null && hasLibV2ray()) {
                pendingConfig = config
                val intent = VpnService.prepare(this)
                if (intent != null) {
                    vpnPermissionLauncher.launch(intent)
                } else {
                    startVpnService(config)
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
        return try {
            Class.forName("libv2ray.Libv2ray")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    private fun startVpnService(config: String) {
        startForegroundService(Intent(this, VpnServiceImpl::class.java).apply {
            putExtra(VpnServiceImpl.EXTRA_CONFIG, config)
        })
        Toast.makeText(this, "VPN запущен", Toast.LENGTH_SHORT).show()
        finish()
    }
}
