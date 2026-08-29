package com.example.resqgo.ui.onboarding

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.resqgo.databinding.ActivityOnboardingBinding

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val fragments = listOf(
            EmergencyContactFragment(),
            PermissionsFragment()
        )

        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = fragments.size
            override fun createFragment(position: Int): Fragment = fragments[position]
        }
        
        // Disable swipe so user must use buttons
        binding.viewPager.isUserInputEnabled = false
    }

    fun nextPage() {
        val nextIndex = binding.viewPager.currentItem + 1
        if (nextIndex < binding.viewPager.adapter?.itemCount ?: 0) {
            binding.viewPager.currentItem = nextIndex
        }
    }
}
