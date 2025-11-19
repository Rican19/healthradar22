package com.capstone.healthradar

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import java.io.File
import java.text.Normalizer
import java.util.concurrent.Executors

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
    private var activeSheet: BottomSheetDialog? = null

    // Default map setup
    private val defaultCenter = GeoPoint(10.384, 123.957)
    private val defaultZoom = 13.5
    private val zoomOnBarangaySelect = 14.8 // Slight zoom for clarity

    data class Record(
        val diseaseNorm: String,
        val diseaseDisplay: String,
        val barangayNorm: String,
        val municipalityNorm: String,
        val caseCount: Int
    )

    data class GeoFeature(
        val barangay: String,
        var municipality: String,
        val polygons: List<List<GeoPoint>>,
        val normalizedBarangay: String = normalize(barangay),
        var normalizedMunicipality: String = normalize(municipality)
    )

    companion object {
        // Normalize but keep spaces between words — remove punctuation and diacritics
        fun normalize(name: String?): String {
            if (name == null) return ""
            var n = Normalizer.normalize(name, Normalizer.Form.NFD)
            n = n.replace("\\p{M}".toRegex(), "") // strip diacritics
            n = n.lowercase()
                .replace("city of", "")
                .replace("city", "")
                .replace("municipality of", "")
                .replace("municipality", "")
                .replace("mun.", "")
                .replace("brgy", "")
                .replace("barangay", "")
                .replace("ñ", "n")
                .replace("[^a-z0-9\\s]".toRegex(), " ") // keep letters, numbers and spaces
                .replace("\\s+".toRegex(), " ")
                .trim()
            return n
        }

        // Fuzzy match but tolerant to spacing
        fun fuzzyMatch(a: String?, b: String?): Boolean {
            if (a == null || b == null) return false
            val x = normalize(a)
            val y = normalize(b)
            if (x.isEmpty() || y.isEmpty()) return false
            if (x == y) return true
            val xr = x.replace(" ", "")
            val yr = y.replace(" ", "")
            return xr.contains(yr) || yr.contains(xr)
        }
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val ctx = requireContext()
        Configuration.getInstance().osmdroidBasePath = File(ctx.filesDir, "osmdroid")
        Configuration.getInstance().osmdroidTileCache = File(Configuration.getInstance().osmdroidBasePath, "tiles")
        Configuration.getInstance().load(ctx, ctx.getSharedPreferences("prefs", 0))

        val root = inflater.inflate(R.layout.fragment_map, container, false)
        mapView = root.findViewById(R.id.map_view)
        spinnerDisease = root.findViewById(R.id.spinner_disease)

        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(defaultZoom)
        mapView.controller.setCenter(defaultCenter)

        loadGeoJsonThenData()
        return root
    }

    private fun loadGeoJsonThenData() {
        exec.execute {
            try {
                val jsonText = requireContext().assets.open("geoshapes.json").bufferedReader().use { it.readText() }
                val root = JSONObject(jsonText)
                val features = root.getJSONArray("features")
                geoFeatures.clear()

                for (i in 0 until features.length()) {
                    val feature = features.getJSONObject(i)
                    val props = feature.optJSONObject("properties") ?: continue
                    val barangayRaw = props.optString("adm4_en", "Unknown").trim()
                    var municipalityRaw = props.optString("adm3_en", "").trim()

                    // fallback from PSGC codes when adm3_en missing
                    if (municipalityRaw.isBlank() || municipalityRaw.equals("Unknown", true)) {
                        val adm3Psgc = props.optLong("adm3_psgc", 0L)
                        municipalityRaw = when (adm3Psgc) {
                            702218000L -> "Liloan"
                            702217000L -> "Consolacion"
                            702214000L -> "Mandaue"
                            else -> inferMunicipality(barangayRaw)
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

    private fun inferMunicipality(barangay: String): String {
        val b = normalize(barangay)
        val liloanBrgys = listOf("cabadiangan", "calero", "catarman", "cotcot", "jubay",
            "lataban", "mangal", "poblacion", "puente", "san vicente", "sanvicente",
            "santa cruz", "tabla", "tayud", "tilhaong", "yati")
        val consolacionBrgys = listOf("cabancalan", "cansaga", "danglag", "garing", "jugan",
            "lampingan", "nangka", "panaosawon", "poblacion oriental", "poblacion occidental",
            "pulpogan", "pitogo", "tologon")
        val mandaueBrgys = listOf("alangalang", "bakilid", "banilad", "basak", "cabancalan",
            "canduman", "centro", "cubacub", "guizo", "labogon", "looc", "maguikay",
            "mantuyong", "opao", "paknaan", "subangdaku", "tabok", "tipolo", "tingub")

        return when {
            liloanBrgys.any { b.contains(it) } -> "Liloan"
            consolacionBrgys.any { b.contains(it) } -> "Consolacion"
            mandaueBrgys.any { b.contains(it) } -> "Mandaue"
            else -> "Unknown"
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
                }
                mapView.overlays.add(poly)
            }
        }
        mapView.invalidate()
    }

    private fun loadCasesFromFirestore() {
        records.clear()
        diseaseDisplayList.clear()

        db.collection("healthradarDB").document("centralizedData").collection("allCases").get()
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
                diseaseDisplayList.add(0, "All Diseases")

                val adapter = ArrayAdapter(
                    requireContext(),
                    R.layout.spinner_item,  // custom view for selected item
                    diseaseDisplayList
                )
                adapter.setDropDownViewResource(R.layout.spinner_dropdown_item) // custom view for dropdown list
                spinnerDisease.adapter = adapter

                spinnerDisease.setPopupBackgroundResource(android.R.color.transparent)
                spinnerDisease.setSelection(0)

                spinnerDisease.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                        (view as? TextView)?.setTextColor(Color.BLACK)
                        (view as? TextView)?.setTypeface(null, android.graphics.Typeface.BOLD)

                        val selected = parent.getItemAtPosition(position) as String
                        selectedDisease = selected
                        mapView.controller.setZoom(defaultZoom)
                        mapView.controller.setCenter(defaultCenter)
                        renderDiseasePolygons()
                    }

                    override fun onNothingSelected(parent: AdapterView<*>) {}
                }

            }
            .addOnFailureListener { ex ->
                Log.e(TAG, "Firestore load failed", ex)
            }
    }

    private fun renderDiseasePolygons() {
        mapView.overlays.removeAll(mapView.overlays.filterIsInstance<Polygon>())

        val selected = selectedDisease ?: "All Diseases"
        // barangayKey -> (diseaseDisplay -> count)
        val caseMap = mutableMapOf<String, MutableMap<String, Int>>()

        for (r in records) {
            if (selected == "All Diseases" || r.diseaseDisplay.equals(selected, true)) {
                val key = "${r.barangayNorm}_${r.municipalityNorm}"
                val m = caseMap.getOrPut(key) { mutableMapOf() }
                m[r.diseaseDisplay] = m.getOrDefault(r.diseaseDisplay, 0) + r.caseCount
            }
        }

        for (f in geoFeatures) {
            val brgyKey = "${f.normalizedBarangay}_${f.normalizedMunicipality}"
            val diseases = caseMap[brgyKey]
            val totalCases = diseases?.values?.sum() ?: 0

            // color polygons even when totalCases==0 (light fill), but clickable only if >0
            val fillColor = when {
                totalCases == 0 -> Color.argb(40, 255, 255, 255)
                totalCases in 1..2 -> Color.argb(120, 255, 245, 157)
                totalCases in 3..5 -> Color.argb(130, 255, 183, 77)
                totalCases in 6..10 -> Color.argb(140, 245, 124, 0)
                totalCases in 11..20 -> Color.argb(150, 229, 57, 53)
                else -> Color.argb(160, 183, 28, 28)
            }

            for (ring in f.polygons) {
                val poly = Polygon().apply {
                    points = ring
                    this.fillColor = fillColor
                    strokeColor = Color.BLACK
                    strokeWidth = 1.5f

                    // only attach click if there are diseases to show (i.e., in caseMap)
                    if (!diseases.isNullOrEmpty()) {
                        setOnClickListener { _, _, _ ->
                            // close previous sheet
                            activeSheet?.dismiss()
                            activeSheet = null

                            val muniName = if (f.municipality.isBlank() || f.municipality.equals("Unknown", true))
                                inferMunicipality(f.barangay) else f.municipality

                            val center = getPolygonCenter(ring)
                            // slight zoom and center
                            mapView.controller.animateTo(center)
                            mapView.controller.setZoom(zoomOnBarangaySelect)

                            // show bottomsheet with all disease entries for this barangay (respecting current spinner filter)
                            showBottomSheet(f.barangay, muniName, diseases)
                            true
                        }
                    }
                }
                mapView.overlays.add(poly)
            }
        }
        mapView.invalidate()
    }

    @SuppressLint("InflateParams", "SetTextI18n")
    private fun showBottomSheet(barangay: String, municipality: String, diseases: Map<String, Int>) {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.info_card, null)

        val title = view.findViewById<TextView>(R.id.textBarangay)
        val muni = view.findViewById<TextView>(R.id.textMunicipality)
        val total = view.findViewById<TextView>(R.id.textTotal)
        val listLayout = view.findViewById<LinearLayout>(R.id.listDiseases)

        title.text = barangay
        muni.text = "Municipality: $municipality"

        val totalCases = diseases.values.sum()
        total.text = "Total Cases: $totalCases"

        // ✅ Change background colour depending on total cases
        val color = when {
            totalCases in 1..2 -> Color.parseColor("#FFF59D")
            totalCases in 3..5 -> Color.parseColor("#FFB74D")
            totalCases in 6..10 -> Color.parseColor("#F57C00")
            totalCases in 11..20 -> Color.parseColor("#E53935")
            totalCases > 20 -> Color.parseColor("#B71C1C")
            else -> Color.parseColor("#E0E0E0")
        }
        total.setBackgroundColor(color)

        listLayout.removeAllViews()
        for ((disease, count) in diseases) {
            val tv = TextView(requireContext())
            tv.text = "$disease: $count"
            tv.textSize = 15f
            tv.setTextColor(Color.BLACK)
            listLayout.addView(tv)
        }

        dialog.setContentView(view)
        dialog.show()
    }


    private fun getPolygonCenter(points: List<GeoPoint>): GeoPoint {
        var lat = 0.0
        var lon = 0.0
        for (p in points) {
            lat += p.latitude
            lon += p.longitude
        }
        return GeoPoint(lat / points.size, lon / points.size)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        activeSheet?.dismiss()
        exec.shutdownNow()
    }
}
