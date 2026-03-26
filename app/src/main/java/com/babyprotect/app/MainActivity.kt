package com.babyprotect.app

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var audioManager: AudioManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("BabyProtect", Context.MODE_PRIVATE)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // First launch - ask to set password
        if (prefs.getString("password", null) == null) {
            prefs.edit().putString("password", "1234").apply()
        }

        setupUI()
        checkWriteSettingsPermission()
    }

    private fun setupUI() {
        val isLocked = prefs.getBoolean("locked", false)

        val statusText = findViewById<TextView>(R.id.statusText)
        val statusIcon = findViewById<TextView>(R.id.statusIcon)
        val lockBtn = findViewById<Button>(R.id.lockBtn)
        val unlockBtn = findViewById<Button>(R.id.unlockBtn)
        val changePassBtn = findViewById<Button>(R.id.changePassBtn)
        val brightnessInfo = findViewById<TextView>(R.id.brightnessInfo)
        val volumeInfo = findViewById<TextView>(R.id.volumeInfo)

        val brightnessPercent = prefs.getInt("brightness", 25)
        val volumePercent = prefs.getInt("volume", 25)

        brightnessInfo.text = "Brightness lock: $brightnessPercent%"
        volumeInfo.text = "Volume lock: $volumePercent%"

        if (isLocked) {
            statusText.text = "🔒 LOCKED\nBrightness & Volume are protected"
            statusIcon.text = "🔒"
            lockBtn.isEnabled = false
            unlockBtn.isEnabled = true
        } else {
            statusText.text = "🔓 UNLOCKED\nYour baby can change brightness & volume"
            statusIcon.text = "🔓"
            lockBtn.isEnabled = true
            unlockBtn.isEnabled = false
        }

        lockBtn.setOnClickListener { enableLock() }
        unlockBtn.setOnClickListener { showUnlockDialog() }
        changePassBtn.setOnClickListener { showChangePasswordDialog() }

        val brightnessBtn = findViewById<Button>(R.id.setBrightnessBtn)
        val volumeBtn = findViewById<Button>(R.id.setVolumeBtn)

        brightnessBtn.setOnClickListener { showBrightnessDialog() }
        volumeBtn.setOnClickListener { showVolumeDialog() }
    }

    private fun enableLock() {
        if (!Settings.System.canWrite(this)) {
            checkWriteSettingsPermission()
            return
        }
        prefs.edit().putBoolean("locked", true).apply()
        applyLock()
        startLockService()
        setupUI()
        Toast.makeText(this, "🔒 Lock enabled! Baby is protected.", Toast.LENGTH_SHORT).show()
    }

    private fun applyLock() {
        val brightnessPercent = prefs.getInt("brightness", 25)
        val volumePercent = prefs.getInt("volume", 25)

        // Apply brightness (0-255 scale)
        val brightnessValue = (brightnessPercent / 100f * 255).toInt().coerceIn(1, 255)
        try {
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, brightnessValue)
        } catch (e: Exception) {
            Toast.makeText(this, "Need Write Settings permission", Toast.LENGTH_SHORT).show()
        }

        // Apply volume
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val volumeValue = (volumePercent / 100f * maxVolume).toInt().coerceIn(0, maxVolume)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeValue, 0)
        audioManager.setStreamVolume(AudioManager.STREAM_RING, volumeValue, 0)
    }

    private fun startLockService() {
        val intent = Intent(this, LockService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun showUnlockDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_password, null)
        val passwordInput = dialogView.findViewById<EditText>(R.id.passwordInput)

        AlertDialog.Builder(this)
            .setTitle("🔑 Enter Password to Unlock")
            .setView(dialogView)
            .setPositiveButton("Unlock") { _, _ ->
                val entered = passwordInput.text.toString()
                val saved = prefs.getString("password", "1234")
                if (entered == saved) {
                    prefs.edit().putBoolean("locked", false).apply()
                    stopService(Intent(this, LockService::class.java))
                    setupUI()
                    Toast.makeText(this, "🔓 Unlocked!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "❌ Wrong password!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSetPasswordDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_set_password, null)
        val passInput = dialogView.findViewById<EditText>(R.id.newPasswordInput)
        val confirmInput = dialogView.findViewById<EditText>(R.id.confirmPasswordInput)

        val dialog = AlertDialog.Builder(this)
            .setTitle("👶 Welcome! Set Your Password")
            .setMessage("This password will protect brightness & volume settings from your baby.")
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("Set Password", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pass = passInput.text.toString()
                val confirm = confirmInput.text.toString()
                when {
                    pass.length < 4 -> Toast.makeText(this, "Password must be at least 4 characters", Toast.LENGTH_SHORT).show()
                    pass != confirm -> Toast.makeText(this, "Passwords don't match!", Toast.LENGTH_SHORT).show()
                    else -> {
                        prefs.edit().putString("password", pass).apply()
                        Toast.makeText(this, "✅ Password set!", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showChangePasswordDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_password, null)
        val currentInput = dialogView.findViewById<EditText>(R.id.currentPasswordInput)
        val newInput = dialogView.findViewById<EditText>(R.id.newPasswordInput2)
        val confirmInput = dialogView.findViewById<EditText>(R.id.confirmPasswordInput2)

        val dialog = AlertDialog.Builder(this)
            .setTitle("🔑 Change Password")
            .setView(dialogView)
            .setPositiveButton("Change", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val current = currentInput.text.toString()
                val newPass = newInput.text.toString()
                val confirm = confirmInput.text.toString()
                val saved = prefs.getString("password", "1234")
                when {
                    current != saved -> Toast.makeText(this, "❌ Wrong current password!", Toast.LENGTH_SHORT).show()
                    newPass.length < 4 -> Toast.makeText(this, "Password must be at least 4 characters", Toast.LENGTH_SHORT).show()
                    newPass != confirm -> Toast.makeText(this, "New passwords don't match!", Toast.LENGTH_SHORT).show()
                    else -> {
                        prefs.edit().putString("password", newPass).apply()
                        Toast.makeText(this, "✅ Password changed!", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showBrightnessDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_slider, null)
        val slider = dialogView.findViewById<SeekBar>(R.id.slider)
        val valueText = dialogView.findViewById<TextView>(R.id.sliderValue)
        val current = prefs.getInt("brightness", 20)

        slider.max = 50  // max 50% for baby
        slider.progress = current
        valueText.text = "$current%"

        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                val v = p.coerceAtLeast(5)
                valueText.text = "$v%"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        AlertDialog.Builder(this)
            .setTitle("☀️ Set Brightness Lock Level")
            .setMessage("Maximum brightness your baby can use (5% - 50%)")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val value = slider.progress.coerceAtLeast(5)
                prefs.edit().putInt("brightness", value).apply()
                if (prefs.getBoolean("locked", false)) applyLock()
                setupUI()
                Toast.makeText(this, "Brightness set to $value%", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showVolumeDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_slider, null)
        val slider = dialogView.findViewById<SeekBar>(R.id.slider)
        val valueText = dialogView.findViewById<TextView>(R.id.sliderValue)
        val current = prefs.getInt("volume", 10)

        slider.max = 30  // max 30% for baby
        slider.progress = current
        valueText.text = "$current%"

        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                valueText.text = "$p%"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        AlertDialog.Builder(this)
            .setTitle("🔊 Set Volume Lock Level")
            .setMessage("Maximum volume your baby can use (0% - 30%)")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val value = slider.progress
                prefs.edit().putInt("volume", value).apply()
                if (prefs.getBoolean("locked", false)) applyLock()
                setupUI()
                Toast.makeText(this, "Volume set to $value%", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkWriteSettingsPermission() {
        if (!Settings.System.canWrite(this)) {
            AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("Baby Protect needs permission to modify system settings (brightness). Please enable it on the next screen.")
                .setPositiveButton("Grant Permission") { _, _ ->
                    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                }
                .setCancelable(false)
                .show()
        }
    }
}
