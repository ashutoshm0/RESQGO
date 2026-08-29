package com.example.resqgo.ui.history

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.resqgo.ResqgoApp
import com.example.resqgo.databinding.ActivityRideHistoryBinding
import kotlinx.coroutines.launch
import java.util.Locale

class RideHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRideHistoryBinding
    private lateinit var adapter: RideHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRideHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        adapter = RideHistoryAdapter { event ->
            if (event.latitude != 0.0 && event.longitude != 0.0) {
                val uri = String.format(Locale.ENGLISH, "geo:%f,%f?q=%f,%f", 
                    event.latitude, event.longitude, event.latitude, event.longitude)
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                intent.setPackage("com.google.android.apps.maps")
                try {
                    startActivity(intent)
                } catch (_: Exception) {
                    // Google Maps not installed — open in browser
                    val webUri = "https://maps.google.com/?q=${event.latitude},${event.longitude}"
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUri)))
                }
            } else {
                Toast.makeText(this, "Event: ${event.eventType}", Toast.LENGTH_SHORT).show()
            }
        }

        binding.rvRideEvents.layoutManager = LinearLayoutManager(this)
        binding.rvRideEvents.adapter = adapter

        val repository = (application as ResqgoApp).repository

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.allEvents.collect { events ->
                    val filteredEvents = events.filter { it.eventType == "RIDE_END" || it.eventType.startsWith("SOS_") }
                        .sortedByDescending { it.timestamp }
                    
                    adapter.submitList(filteredEvents)
                    
                    if (filteredEvents.isEmpty()) {
                        binding.llEmptyState.visibility = View.VISIBLE
                        binding.rvRideEvents.visibility = View.GONE
                    } else {
                        binding.llEmptyState.visibility = View.GONE
                        binding.rvRideEvents.visibility = View.VISIBLE
                    }
                }
            }
        }
    }
}
