package com.capstone.healthradar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class RecordFragment : Fragment() {

    private lateinit var listViewDiseases: ListView
    private lateinit var spinnerMunicipality: Spinner
    private lateinit var firestore: FirebaseFirestore

    // Use the single CaseItem defined in CaseItem.kt
    private val caseList = mutableListOf<CaseItem>()
    private lateinit var adapter: CaseListAdapter

    private val municipalities = listOf("All", "Lilo-an", "Consolacion", "Mandaue")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_record, container, false)

        listViewDiseases = rootView.findViewById(R.id.listViewDiseases)
        spinnerMunicipality = rootView.findViewById(R.id.spinnerMunicipality)
        firestore = FirebaseFirestore.getInstance()

        // Adapter setup once, backed by mutable caseList
        adapter = CaseListAdapter(requireContext(), caseList)
        listViewDiseases.adapter = adapter

        // Spinner setup
        val spinnerAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            municipalities
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMunicipality.adapter = spinnerAdapter

        // Load initial data
        loadCases("All")

        // Spinner listener
        spinnerMunicipality.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedMunicipality = municipalities[position]
                loadCases(selectedMunicipality)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        return rootView
    }

    private fun loadCases(municipality: String) {
        caseList.clear()

        var query: Query = firestore.collection("healthradarDB")
            .document("centralizedData")
            .collection("allCases")

        if (municipality != "All") {
            query = query.whereEqualTo("Municipality", municipality)
        }

        query.get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    val diseaseName = doc.getString("DiseaseName") ?: "Unknown Disease"
                    val caseCount = doc.getString("CaseCount") ?: "0"
                    val muni = doc.getString("Municipality") ?: "Unknown"
                    val date = doc.getString("DateReported") ?: doc.getString("uploadedAt") ?: "N/A"

                    caseList.add(CaseItem(diseaseName, caseCount, muni, date))
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
