package com.example.resqgo.ui.contacts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.resqgo.R
import com.example.resqgo.data.local.EmergencyContact
import com.example.resqgo.data.local.UserPreferences
import com.example.resqgo.databinding.ActivityEmergencyContactsBinding

class EmergencyContactsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEmergencyContactsBinding
    private lateinit var prefs: UserPreferences
    private var contacts = mutableListOf<EmergencyContact>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmergencyContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = UserPreferences(this)

        binding.btnAddContact.setOnClickListener {
            showContactDialog(null, -1)
        }

        setupCardListeners()
    }

    override fun onResume() {
        super.onResume()
        contacts = prefs.getEmergencyContacts().toMutableList()
        refreshContactsUI()
    }

    private fun setupCardListeners() {
        // Use underscore-based IDs matching the XML: card_contact_1, btn_edit_contact_1, etc.
        binding.btnEditContact1.setOnClickListener { showContactDialog(contacts.getOrNull(0), 0) }
        binding.btnDeleteContact1.setOnClickListener { deleteContact(0) }
        binding.btnEditContact2.setOnClickListener { showContactDialog(contacts.getOrNull(1), 1) }
        binding.btnDeleteContact2.setOnClickListener { deleteContact(1) }
        binding.btnEditContact3.setOnClickListener { showContactDialog(contacts.getOrNull(2), 2) }
        binding.btnDeleteContact3.setOnClickListener { deleteContact(2) }
    }

    private fun refreshContactsUI() {
        binding.btnAddContact.visibility = if (contacts.size < 3) View.VISIBLE else View.GONE

        // Card 1
        if (contacts.size > 0) {
            binding.cardContact1.visibility = View.VISIBLE
            binding.tvContact1Name.text = contacts[0].name
            binding.tvContact1Phone.text = contacts[0].phone
            binding.tvContact1Relation.text = contacts[0].relation
            binding.tvContact1Primary.visibility = View.VISIBLE
        } else {
            binding.cardContact1.visibility = View.GONE
        }

        // Card 2
        if (contacts.size > 1) {
            binding.cardContact2.visibility = View.VISIBLE
            binding.tvContact2Name.text = contacts[1].name
            binding.tvContact2Phone.text = contacts[1].phone
            binding.tvContact2Relation.text = contacts[1].relation
            binding.tvContact2Primary.visibility = View.GONE
        } else {
            binding.cardContact2.visibility = View.GONE
        }

        // Card 3
        if (contacts.size > 2) {
            binding.cardContact3.visibility = View.VISIBLE
            binding.tvContact3Name.text = contacts[2].name
            binding.tvContact3Phone.text = contacts[2].phone
            binding.tvContact3Relation.text = contacts[2].relation
            binding.tvContact3Primary.visibility = View.GONE
        } else {
            binding.cardContact3.visibility = View.GONE
        }
    }

    private fun showContactDialog(existingContact: EmergencyContact?, index: Int) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_contact, null)
        val etName = dialogView.findViewById<EditText>(R.id.etContactName)
        val etPhone = dialogView.findViewById<EditText>(R.id.etContactPhone)
        val etRelation = dialogView.findViewById<EditText>(R.id.etContactRelation)

        if (existingContact != null) {
            etName.setText(existingContact.name)
            etPhone.setText(existingContact.phone)
            etRelation.setText(existingContact.relation)
        }

        AlertDialog.Builder(this)
            .setTitle(if (existingContact == null) "Add Contact" else "Edit Contact")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = etName.text.toString().trim()
                val phone = etPhone.text.toString().trim()
                val relation = etRelation.text.toString().trim()
                if (name.isNotEmpty() && phone.isNotEmpty()) {
                    val newContact = EmergencyContact(name, phone, relation.ifBlank { "Family" })
                    if (index >= 0 && index < contacts.size) {
                        contacts[index] = newContact
                    } else {
                        contacts.add(newContact)
                    }
                    prefs.saveEmergencyContacts(contacts)
                    refreshContactsUI()
                } else {
                    Toast.makeText(this, "Name and phone are required", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteContact(index: Int) {
        if (index >= 0 && index < contacts.size) {
            AlertDialog.Builder(this)
                .setTitle("Remove Contact")
                .setMessage("Remove ${contacts[index].name} from emergency contacts?")
                .setPositiveButton("Remove") { _, _ ->
                    contacts.removeAt(index)
                    prefs.saveEmergencyContacts(contacts)
                    refreshContactsUI()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}
