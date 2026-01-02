package com.restaurant.manageralerts

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.restaurant.manageralerts.data.SupabaseApi
import com.restaurant.manageralerts.util.DeviceId
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var nameInput: EditText
    private lateinit var subscribeBtn: Button
    private lateinit var statusText: TextView

    private val requestNotifPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) subscribeFlow()
        else setStatus("❌ Permiso de notificaciones denegado.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        nameInput = findViewById(R.id.nameInput)
        subscribeBtn = findViewById(R.id.subscribeBtn)
        statusText = findViewById(R.id.statusText)

        nameInput.setText(getSharedPreferences("prefs", MODE_PRIVATE).getString("name", "") ?: "")

        subscribeBtn.setOnClickListener {
            val name = nameInput.text.toString().trim()
            if (name.isEmpty()) {
                setStatus("❌ Pon tu nombre primero 🙂")
                return@setOnClickListener
            }
            getSharedPreferences("prefs", MODE_PRIVATE).edit().putString("name", name).apply()

            if (Build.VERSION.SDK_INT >= 33) {
                val has = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
                if (!has) {
                    requestNotifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return@setOnClickListener
                }
            }
            subscribeFlow()
        }

        setStatus("Listo para activar.")
    }

    private fun subscribeFlow() {
        setStatus("Activando…")

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                val name = nameInput.text.toString().trim()
                val deviceId = DeviceId.get(this)

                thread {
                    val ok = SupabaseApi.registerDevice(
                        deviceId = deviceId,
                        name = name,
                        fcmToken = token,
                        platform = "android"
                    )
                    runOnUiThread {
                        if (ok) setStatus("✅ Listo. Notificaciones activadas.")
                        else setStatus("❌ No se pudo registrar el dispositivo.")
                    }
                }
            }
            .addOnFailureListener {
                setStatus("❌ No se pudo obtener token FCM.")
            }
    }

    private fun setStatus(msg: String) {
        statusText.text = msg
    }
}
