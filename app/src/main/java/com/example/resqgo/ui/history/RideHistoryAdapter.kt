package com.example.resqgo.ui.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.resqgo.R
import com.example.resqgo.data.local.RideEvent
import com.example.resqgo.databinding.ItemRideEventBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RideHistoryAdapter(private val onItemClick: (RideEvent) -> Unit) :
    ListAdapter<RideEvent, RideHistoryAdapter.RideEventViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RideEventViewHolder {
        val binding = ItemRideEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RideEventViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: RideEventViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class RideEventViewHolder(
        private val binding: ItemRideEventBinding,
        private val onItemClick: (RideEvent) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

        fun bind(event: RideEvent) {
            binding.root.setOnClickListener { onItemClick(event) }

            binding.tvDateTime.text = dateFormat.format(Date(event.timestamp))

            if (event.address.isNotEmpty()) {
                binding.tvAddress.visibility = View.VISIBLE
                binding.tvAddress.text = event.address
            } else {
                binding.tvAddress.visibility = View.GONE
            }

            val context = binding.root.context
            
            when (event.eventType) {
                "RIDE_START" -> {
                    binding.tvIcon.text = "🏍️"
                    binding.tvIcon.setTextColor(ContextCompat.getColor(context, R.color.resqgo_success))
                    binding.tvEventType.text = "Ride Started"
                    binding.tvEventType.setTextColor(ContextCompat.getColor(context, R.color.resqgo_success))
                    binding.llRideStats.visibility = View.GONE
                    binding.llSosStats.visibility = View.GONE
                }
                "RIDE_END" -> {
                    binding.tvIcon.text = "🏁"
                    binding.tvIcon.setTextColor(ContextCompat.getColor(context, R.color.resqgo_text_secondary))
                    binding.tvEventType.text = "Ride Ended"
                    binding.tvEventType.setTextColor(ContextCompat.getColor(context, R.color.resqgo_text_primary))
                    binding.llRideStats.visibility = View.VISIBLE
                    binding.llSosStats.visibility = View.GONE
                    
                    binding.tvDuration.text = formatDuration(event.durationMs)
                    binding.tvDistance.text = String.format(Locale.getDefault(), "%.1f km", event.distanceKm)
                }
                "SOS_MANUAL" -> {
                    binding.tvIcon.text = "🚨"
                    binding.tvIcon.setTextColor(ContextCompat.getColor(context, R.color.resqgo_alert))
                    binding.tvEventType.text = "SOS Triggered (Manual)"
                    binding.tvEventType.setTextColor(ContextCompat.getColor(context, R.color.resqgo_alert))
                    binding.llRideStats.visibility = View.GONE
                    binding.llSosStats.visibility = View.VISIBLE
                    
                    binding.tvContactsNotified.text = "👥 ${event.contactsNotified} contacts notified"
                    binding.tvSmsStatus.text = if (event.smsSent) "SMS Sent" else "SMS Failed"
                }
                "SOS_AUTO" -> {
                    binding.tvIcon.text = "🚨"
                    binding.tvIcon.setTextColor(ContextCompat.getColor(context, R.color.resqgo_alert))
                    binding.tvEventType.text = "SOS Triggered (Auto)"
                    binding.tvEventType.setTextColor(ContextCompat.getColor(context, R.color.resqgo_alert))
                    binding.llRideStats.visibility = View.GONE
                    binding.llSosStats.visibility = View.VISIBLE
                    
                    binding.tvContactsNotified.text = "👥 ${event.contactsNotified} contacts notified"
                    binding.tvSmsStatus.text = if (event.smsSent) "SMS Sent" else "SMS Failed"
                }
            }
        }

        private fun formatDuration(durationMs: Long): String {
            val totalSeconds = durationMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            
            return when {
                hours > 0 -> "${hours}h ${minutes}m"
                minutes > 0 -> "${minutes}m ${seconds}s"
                else -> "${seconds}s"
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<RideEvent>() {
        override fun areItemsTheSame(oldItem: RideEvent, newItem: RideEvent): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: RideEvent, newItem: RideEvent): Boolean {
            return oldItem == newItem
        }
    }
}
