package com.kore2.shortcutime.ui

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.kore2.shortcutime.databinding.ActivityHostBinding

class HostActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHostBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHostBinding.inflate(layoutInflater)
        setContentView(binding.root)
        showCrashIfAny()
    }

    private fun showCrashIfAny() {
        val prefs = getSharedPreferences("crash_diag", Context.MODE_PRIVATE)
        val trace = prefs.getString("crash_trace", null) ?: return
        prefs.edit().remove("crash_trace").apply()
        AlertDialog.Builder(this)
            .setTitle("Crash detected")
            .setMessage(trace)
            .setPositiveButton("OK", null)
            .show()
    }
}
