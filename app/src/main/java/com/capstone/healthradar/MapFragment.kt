package com.capstone.healthradar

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import java.io.BufferedReader
import java.io.InputStreamReader

class MapFragment : Fragment() {

    private lateinit var mapView: MapView
    private lateinit var spinnerMandaue: Spinner
    private lateinit var spinnerConsolacion: Spinner
    private lateinit var spinnerLiloan: Spinner
    private lateinit var legendLayout: LinearLayout

    private val db = FirebaseFirestore.getInstance()
    private val TAG = "MapFragment"

    // Municipality -> Disease -> CaseCount
    private val caseDataByDisease = mutableMapOf<String, MutableMap<String, Int>>()
    // For dropdowns
    private val diseaseData = mutableMapOf<String, MutableSet<String>>()

    // Track selected disease per municipality
    private val selectedDiseaseByMunicipality = mutableMapOf<String, String?>()

    @SuppressLint("MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val context = requireContext()
        Configuration.getInstance().load(context, context.getSharedPreferences("prefs", 0))

        val rootView = inflater.inflate(R.layout.fragment_map, container, false)

        // Setup map
        mapView = rootView.findViewById(R.id.map_view)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(11.5)
        mapView.controller.setCenter(GeoPoint(10.37, 123.965))

        spinnerMandaue = rootView.findViewById(R.id.spinner_mandaue)
        spinnerConsolacion = rootView.findViewById(R.id.spinner_consolacion)
        spinnerLiloan = rootView.findViewById(R.id.spinner_liloan)
        legendLayout = rootView.findViewById(R.id.legendLayout)

        // Load Firestore data
        loadCasesFromFirestore {
            populateDiseaseDropdowns()
            updatePolygonColors()
            setupLegend()
        }

        return rootView
    }

    // 🔹 Normalises municipality names from both Firestore + GeoJSON
    private fun normalizeName(name: String?): String {
        if (name == null) return ""

        val clean = name.lowercase()
            .replace("-", "")
            .replace(" ", "")
            .trim()

        // Handle aliases / variations
        return when {
            clean.contains("mandaue") -> "mandaue"
            clean.contains("consolacion") -> "consolacion"
            clean.contains("liloan") -> "liloan"
            else -> clean
        }
    }

    private fun loadCasesFromFirestore(onComplete: () -> Unit) {
        db.collection("healthradarDB")
            .document("centralizedData")
            .collection("allCases")
            .get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    var municipality = doc.getString("Municipality") ?: continue
                    municipality = normalizeName(municipality)

                    val countStr = doc.getString("CaseCount") ?: "0"
                    val count = countStr.toIntOrNull() ?: 0
                    val disease = doc.getString("DiseaseName") ?: "Unknown"

                    // Build nested map
                    val muniMap = caseDataByDisease.getOrPut(municipality) { mutableMapOf() }
                    val current = muniMap.getOrDefault(disease, 0)
                    muniMap[disease] = current + count

                    val diseases = diseaseData.getOrPut(municipality) { mutableSetOf() }
                    diseases.add(disease)
                }
                onComplete()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error fetching Firestore data", e)
                onComplete()
            }
    }

    private fun populateDiseaseDropdowns() {
        setSpinner(spinnerMandaue, "mandaue")
        setSpinner(spinnerConsolacion, "consolacion")
        setSpinner(spinnerLiloan, "liloan")
    }

    private fun setSpinner(spinner: Spinner, municipalityKey: String) {
        val diseases = diseaseData[municipalityKey]?.toList() ?: listOf()

        val adapter = if (diseases.isEmpty()) {
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, listOf("No Data Available"))
        } else {
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, diseases)
        }

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.isEnabled = diseases.isNotEmpty()

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val disease = parent.getItemAtPosition(position).toString()
                if (disease != "No Data Available") {
                    selectedDiseaseByMunicipality[municipalityKey] = disease
                    Log.d(TAG, "Selected disease for $municipalityKey = $disease")
                    updatePolygonColors()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun updatePolygonColors() {
        mapView.overlays.clear()

        try {
            val inputStream = requireContext().assets.open("geoshapes.json")
            val json = BufferedReader(InputStreamReader(inputStream)).use { it.readText() }

            val jsonObj = JSONObject(json)
            val features = jsonObj.getJSONArray("features")

            for (i in 0 until features.length()) {
                val feature = features.getJSONObject(i)
                val properties = feature.getJSONObject("properties")

                // ✅ Use actual keys from your GeoJSON
                val municipalityNameRaw = properties.optString("adm3_en",
                    properties.optString("name", "Unknown"))
                val municipalityName = normalizeName(municipalityNameRaw)

                val selectedDisease = selectedDiseaseByMunicipality[municipalityName]
                val count = if (selectedDisease != null) {
                    caseDataByDisease[municipalityName]?.get(selectedDisease) ?: 0
                } else {
                    caseDataByDisease[municipalityName]?.values?.sum() ?: 0
                }

                Log.d(TAG, "Polygon for $municipalityNameRaw → Disease=$selectedDisease Cases=$count")

                val geometry = feature.getJSONObject("geometry")
                if (geometry.getString("type") == "Polygon") {
                    val coords = geometry.getJSONArray("coordinates").getJSONArray(0)
                    val points = mutableListOf<GeoPoint>()
                    for (j in 0 until coords.length()) {
                        val coord = coords.getJSONArray(j)
                        val lon = coord.getDouble(0)
                        val lat = coord.getDouble(1)
                        points.add(GeoPoint(lat, lon))
                    }

                    val polygon = Polygon().apply {
                        this.points = points
                        this.fillColor = getRiskColor(count)
                        this.strokeColor = Color.BLACK
                        this.strokeWidth = 2f
                        this.title = if (selectedDisease != null) {
                            "$municipalityNameRaw\n$selectedDisease: $count cases"
                        } else {
                            "$municipalityNameRaw\nTotal Cases: $count"
                        }
                    }

                    mapView.overlays.add(polygon)
                }
            }

            mapView.invalidate()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getRiskColor(count: Int): Int {
        return when {
            count == 0 -> 0x5500FF00 // green
            count in 1..50 -> 0x55FFFF00 // yellow
            count in 51..150 -> 0x55FFA500 // orange
            else -> 0x55FF0000 // red
        }
    }

    private fun setupLegend() {
        legendLayout.removeAllViews()

        val riskLevels = listOf(
            Pair("No Cases", 0x5500FF00),
            Pair("1–50 Cases", 0x55FFFF00),
            Pair("51–150 Cases", 0x55FFA500),
            Pair("151+ Cases", 0x55FF0000)
        )

        for ((label, color) in riskLevels) {
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(10, 5, 10, 5)
            }

            val colorBox = View(requireContext()).apply {
                setBackgroundColor(color)
                layoutParams = LinearLayout.LayoutParams(60, 60)
            }

            val text = TextView(requireContext()).apply {
                text = label
                textSize = 14f
                setTextColor(Color.BLACK) // ✅ Set text color to black
                setPadding(20, 0, 0, 0)
            }

            row.addView(colorBox)
            row.addView(text)
            legendLayout.addView(row)
        }
    }

}
