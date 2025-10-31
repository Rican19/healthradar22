package com.capstone.healthradar

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.min

class MapFragment : Fragment() {

    private lateinit var mapView: MapView
    private lateinit var spinnerDisease: Spinner

    private val db = FirebaseFirestore.getInstance()
    private val TAG = "MapFragment"

    private val records = mutableListOf<Record>()
    private val diseaseDisplayList = mutableListOf<String>()
    private val geoFeatures = mutableListOf<GeoFeature>()
    private val exec = Executors.newSingleThreadExecutor()

    private var selectedDisease: String? = null

    data class Record(
        val diseaseNorm: String,
        val diseaseDisplay: String,
        val barangayNorm: String,
        val municipalityNorm: String,
        val caseCount: Int
    )

    data class GeoFeature(
        val barangay: String,
        val municipality: String,
        val polygons: List<List<GeoPoint>>,
        val normalizedBarangay: String = normalize(barangay),
        val normalizedMunicipality: String = normalize(municipality)
    )

    companion object {
        fun normalize(name: String?): String {
            if (name == null) return ""
            return name.lowercase()
                .replace("brgy", "")
                .replace("barangay", "")
                .replace("city", "")
                .replace("municipality", "")
                .replace("liloan", "")
                .replace("mandaue", "")
                .replace("consolacion", "")
                .replace("-", "")
                .replace(".", "")
                .replace("ñ", "n")
                .replace("\\s+".toRegex(), "")
                .trim()
        }

        fun fuzzyMatch(a: String?, b: String?): Boolean {
            if (a == null || b == null) return false
            val x = normalize(a)
            val y = normalize(b)
            return x == y || x.contains(y) || y.contains(x)
        }
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val ctx = requireContext()
        Configuration.getInstance().osmdroidBasePath = File(ctx.filesDir, "osmdroid")
        Configuration.getInstance().osmdroidTileCache =
            File(Configuration.getInstance().osmdroidBasePath, "tiles")
        Configuration.getInstance().load(ctx, ctx.getSharedPreferences("prefs", 0))

        val root = inflater.inflate(R.layout.fragment_map, container, false)
        mapView = root.findViewById(R.id.map_view)
        spinnerDisease = root.findViewById(R.id.spinner_disease)

        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(13.5)
        mapView.controller.setCenter(GeoPoint(10.384, 123.957))

        loadGeoJsonThenData()
        return root
    }

    // ---------------- Load GeoJSON ----------------
    private fun loadGeoJsonThenData() {
        exec.execute {
            try {
                val jsonText = requireContext().assets.open("geoshapes.json")
                    .bufferedReader().use { it.readText() }

                val root = JSONObject(jsonText)
                val features = root.getJSONArray("features")
                geoFeatures.clear()

                for (i in 0 until features.length()) {
                    val feature = features.getJSONObject(i)
                    val props = feature.optJSONObject("properties") ?: continue

                    val barangayRaw = props.optString("adm4_en", "Unknown")
                    var municipalityRaw = props.optString("adm3_en", "Unknown")

                    if (municipalityRaw == "Unknown") {
                        val adm3Psgc = props.optLong("adm3_psgc", 0L)
                        municipalityRaw = when (adm3Psgc) {
                            702218000L -> "Liloan"
                            702217000L -> "Consolacion"
                            702214000L -> "Mandaue"
                            else -> "Unknown"
                        }
                    }

                    val geometry = feature.optJSONObject("geometry") ?: continue
                    val polygons = mutableListOf<List<GeoPoint>>()

                    when (geometry.optString("type")) {
                        "Polygon" -> {
                            val coords = geometry.getJSONArray("coordinates").getJSONArray(0)
                            polygons.add(coordsToGeoPoints(coords))
                        }
                        "MultiPolygon" -> {
                            val arr = geometry.getJSONArray("coordinates")
                            for (m in 0 until arr.length()) {
                                val coords = arr.getJSONArray(m).getJSONArray(0)
                                polygons.add(coordsToGeoPoints(coords))
                            }
                        }
                    }

                    geoFeatures.add(GeoFeature(barangayRaw, municipalityRaw, polygons))
                }

                requireActivity().runOnUiThread {
                    drawBaseMap()
                    loadCasesFromFirestore()
                }

            } catch (ex: Exception) {
                Log.e(TAG, "GeoJSON load failed", ex)
            }
        }
    }

    private fun coordsToGeoPoints(coords: org.json.JSONArray): List<GeoPoint> {
        val pts = mutableListOf<GeoPoint>()
        for (i in 0 until coords.length()) {
            val c = coords.getJSONArray(i)
            pts.add(GeoPoint(c.getDouble(1), c.getDouble(0)))
        }
        return pts
    }

    private fun drawBaseMap() {
        mapView.overlays.clear()
        for (f in geoFeatures) {
            for (ring in f.polygons) {
                val poly = Polygon().apply {
                    points = ring
                    fillColor = Color.argb(60, 255, 255, 255)
                    strokeColor = Color.argb(150, 0, 0, 0)
                    strokeWidth = 1.5f
                    title = "${f.barangay}, ${f.municipality}"
                }
                mapView.overlays.add(poly)
            }
        }
        mapView.invalidate()
    }

    // ---------------- Firestore ----------------
    private fun loadCasesFromFirestore() {
        records.clear()
        diseaseDisplayList.clear()

        db.collection("healthradarDB")
            .document("centralizedData")
            .collection("allCases")
            .get()
            .addOnSuccessListener { docs ->
                for (doc in docs) {
                    val disease = doc.getString("DiseaseName") ?: continue
                    val barangay = doc.getString("Barangay") ?: ""
                    val municipality = doc.getString("Municipality") ?: ""
                    val count = (doc.get("CaseCount") as? Number)?.toInt() ?: 0

                    records.add(
                        Record(
                            normalize(disease),
                            disease.trim(),
                            normalize(barangay),
                            normalize(municipality),
                            count
                        )
                    )

                    if (!diseaseDisplayList.contains(disease.trim()))
                        diseaseDisplayList.add(disease.trim())
                }

                diseaseDisplayList.sortBy { it.lowercase() }
                diseaseDisplayList.add(0, "Select Disease")

                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, diseaseDisplayList)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerDisease.adapter = adapter

                spinnerDisease.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                        val selected = parent.getItemAtPosition(position) as String
                        selectedDisease = if (selected == "Select Disease") null else normalize(selected)
                        renderDiseasePolygons()
                    }

                    override fun onNothingSelected(parent: AdapterView<*>) {}
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "Firestore load failed", it)
            }
    }

    // ---------------- Render Polygons ----------------
    private fun renderDiseasePolygons() {
        mapView.overlays.clear()
        drawBaseMap()

        val selected = selectedDisease ?: return

        val caseMap = mutableMapOf<String, Int>()
        for (r in records) {
            if (r.diseaseNorm == selected) {
                val key = "${r.barangayNorm}_${r.municipalityNorm}"
                caseMap[key] = caseMap.getOrDefault(key, 0) + r.caseCount
            }
        }

        for (f in geoFeatures) {
            val brgy = f.normalizedBarangay
            val muni = f.normalizedMunicipality
            var count = 0

            for ((key, value) in caseMap) {
                val parts = key.split("_")
                if (parts.size == 2 &&
                    (fuzzyMatch(parts[0], brgy) || brgy.contains(parts[0])) &&
                    (fuzzyMatch(parts[1], muni) || muni.contains(parts[1]))
                ) {
                    count = value
                    break
                }
            }

            val fillColor = when {
                count == 0 -> Color.argb(60, 255, 255, 255)
                count in 1..2 -> Color.parseColor("#FFF59D")
                count in 3..5 -> Color.parseColor("#FFB74D")
                count in 6..10 -> Color.parseColor("#F57C00")
                count in 11..20 -> Color.parseColor("#E53935")
                else -> Color.parseColor("#B71C1C")
            }

            for (ring in f.polygons) {
                val poly = Polygon().apply {
                    points = ring
                    this.fillColor = fillColor
                    strokeColor = Color.BLACK
                    strokeWidth = 1.5f
                    title = "${f.barangay}, ${f.municipality}\nCases: $count"
                }
                mapView.overlays.add(poly)
            }
        }

        mapView.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        exec.shutdownNow()
    }
}
