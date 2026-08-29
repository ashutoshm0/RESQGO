package com.example.resqgo.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.resqgo.data.local.EmergencyContact
import com.example.resqgo.data.local.UserPreferences
import com.example.resqgo.databinding.FragmentEmergencyContactBinding

class EmergencyContactFragment : Fragment() {
    private var _binding: FragmentEmergencyContactBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEmergencyContactBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnNext.setOnClickListener {
            val name = binding.etContactName.text.toString().trim()
            val phone = binding.etContactPhone.text.toString().trim()
            val relation = binding.etContactRelation.text.toString().trim()

            if (name.isNotBlank() && phone.isNotBlank()) {
                val prefs = UserPreferences(requireContext())
                val contact = EmergencyContact(
                    name = name,
                    phone = phone,
                    relation = relation.ifBlank { "Family" }
                )
                prefs.saveEmergencyContacts(listOf(contact))
                (activity as? OnboardingActivity)?.nextPage()
            } else {
                Toast.makeText(requireContext(), "Please enter a name and phone number", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
