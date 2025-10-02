package com.capstone.healthradar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QueryDocumentSnapshot

class RecordFragment : Fragment() {

    private lateinit var listViewDiseases: ListView
    private lateinit var spinnerMunicipality: Spinner
    private lateinit var firestore: FirebaseFirestore

    private val diseaseList = mutableListOf<String>()
    private lateinit var diseaseAdapter: ArrayAdapter<String>

    // 🔹 Match Firestore exactly
    private val municipalities = listOf("All", "Lilo-an", "Consolacion", "Mandaue")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_record, container, false)

        listViewDiseases = rootView.findViewById(R.id.listViewDiseases)
        spinnerMunicipality = rootView.findViewById(R.id.spinnerMunicipality)

        firestore = FirebaseFirestore.getInstance()

        // Set up adapter for ListView
        diseaseAdapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, diseaseList)
        listViewDiseases.adapter = diseaseAdapter

        // Set up Spinner
        val spinnerAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            municipalities
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMunicipality.adapter = spinnerAdapter

        // Load diseases initially for "All"
        loadDiseases("All")

        // Listener for Spinner
        spinnerMunicipality.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val selectedMunicipality = municipalities[position]
                loadDiseases(selectedMunicipality)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        return rootView
    }

    private fun loadDiseases(municipality: String) {
        diseaseList.clear()

        var query: Query = firestore.collection("healthradarDB")
            .document("centralizedData")
            .collection("allCases")

        if (municipality != "All") {
            if (municipality == "Consolacion") {
                query = query.whereIn("Municipality", listOf("Consolacion", "consolacion", "Consolacion "))
            } else {
                query = query.whereEqualTo("Municipality", municipality)
            }
        }


        query.get()
            .addOnSuccessListener { documents ->
                for (doc: QueryDocumentSnapshot in documents) {
                    val diseaseName = doc.getString("DiseaseName") ?: "Unknown Disease"
                    val caseCount = doc.getString("CaseCount") ?: "0"
                    val muni = doc.getString("Municipality") ?: "Unknown"

                    diseaseList.add("$diseaseName - $caseCount cases ($muni)")
                }
                diseaseAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
