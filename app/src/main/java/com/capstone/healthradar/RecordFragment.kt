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

        adapter = CaseListAdapter(requireContext(), caseList)
        listViewDiseases.adapter = adapter

        val spinnerAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            municipalities
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMunicipality.adapter = spinnerAdapter

        loadCases("All")

        spinnerMunicipality.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedMunicipality = municipalities[position]
                loadCases(selectedMunicipality)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadCases("All")
    }

    private fun loadCases(municipality: String) {
        caseList.clear()
        adapter.notifyDataSetChanged()

        val query: Query = firestore.collection("healthradarDB")
            .document("centralizedData")
            .collection("allCases")

        query.get()
            .addOnSuccessListener { documents ->
                if (!isAdded) return@addOnSuccessListener

                for (doc in documents) {
                    val diseaseName = doc.getString("DiseaseName") ?: "Unknown Disease"
                    val caseCount = doc.get("CaseCount")?.toString() ?: "0"
                    val muni = doc.getString("Municipality") ?: "Unknown"
                    val date = doc.getString("DateReported")
                        ?: doc.getString("uploadedAt")
                        ?: "N/A"

                    if (municipality == "All" ||
                        muni.replace("-", "", true).equals(municipality.replace("-", "", true), true)
                    ) {
                        caseList.add(CaseItem(diseaseName, caseCount, muni, date))
                    }
                }

                caseList.sortBy { it.diseaseName.lowercase() }
                adapter.notifyDataSetChanged()

                if (caseList.isEmpty()) {
                    showEmptyMessage()
                } else {
                    hideEmptyMessage()
                }
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                Toast.makeText(requireContext(), "Error loading cases: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showEmptyMessage() {
        // Check if empty view already exists
        if (listViewDiseases.emptyView == null) {
            val emptyView = TextView(requireContext()).apply {
                text = "No disease cases found for selected filter"
                gravity = android.view.Gravity.CENTER
                setTextColor(resources.getColor(android.R.color.darker_gray, requireContext().theme))
                setPadding(0, 50, 0, 50)
            }

            (listViewDiseases.parent as? ViewGroup)?.addView(emptyView)
            listViewDiseases.emptyView = emptyView
        }
    }

    private fun hideEmptyMessage() {
        listViewDiseases.emptyView = null
    }

    // ADD THIS METHOD
    fun refreshData() {
        val selectedPosition = spinnerMunicipality.selectedItemPosition
        val selectedMunicipality = if (selectedPosition >= 0 && selectedPosition < municipalities.size) {
            municipalities[selectedPosition]
        } else {
            "All"
        }

        loadCases(selectedMunicipality)
        Toast.makeText(requireContext(), "Records refreshed", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up
        caseList.clear()
        adapter.notifyDataSetChanged()
    }
}