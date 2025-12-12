package com.capstone.healthradar

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Typeface
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
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class MapFragment : Fragment() {

    private lateinit var mapView: MapView
    private lateinit var spinnerDisease: Spinner
    private lateinit var weekSpinner: Spinner
    private lateinit var monthDisplay: TextView
    private lateinit var monthContainer: LinearLayout
    private lateinit var monthIcon: ImageView

    private val db = FirebaseFirestore.getInstance()
    private val TAG = "MapFragment"

    private val records = mutableListOf<Record>()
    private val geoFeatures = mutableListOf<GeoFeature>()
    private val exec = Executors.newSingleThreadExecutor()

    private var selectedDisease: String? = null
    private var currentWeekFilter = ""
    private var selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH)
    private var selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    private var selectedDay: Int = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
    private var activeSheet: BottomSheetDialog? = null

    private val monthNames = arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    private val defaultCenter = GeoPoint(10.384, 123.957)
    private val defaultZoom = 13.5
    private val zoomOnBarangaySelect = 14.8

    data class Record(
        val diseaseNorm: String,
        val diseaseDisplay: String,
        val barangayNorm: String,
        val municipalityNorm: String,
        val caseCount: Int,
        val week: Int,
        val dateReported: String?
    )

    data class GeoFeature(
        val barangay: String,
        var municipality: String,
        val polygons: List<List<GeoPoint>>,
        val normalizedBarangay: String = normalize(barangay),
        var normalizedMunicipality: String = normalize(municipality)
    )

    companion object {
        fun normalize(name: String?): String {
            if (name == null) return ""
            var n = Normalizer.normalize(name, Normalizer.Form.NFD)
            n = n.replace("\\p{M}".toRegex(), "")
            n = n.lowercase()
                .replace("city of", "")
                .replace("city", "")
                .replace("municipality of", "")
                .replace("municipality", "")
                .replace("mun.", "")
                .replace("brgy", "")
                .replace("barangay", "")
                .replace("ñ", "n")
                .replace("[^a-z0-9\\s]".toRegex(), " ")
                .replace("\\s+".toRegex(), " ")
                .trim()
            return n
        }

        fun getWeekFromDate(year: Int, month: Int, day: Int): Int {
            val calendar = Calendar.getInstance()
            calendar.set(year, month, day)
            val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
            val week = (dayOfMonth - 1) / 7 + 1
            return week.coerceAtLeast(1).coerceAtMost(4)
        }

        fun isDateFromSelectedMonth(dateString: String?, selectedMonth: Int, selectedYear: Int): Boolean {
            if (dateString.isNullOrEmpty()) return false
            return try {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                dateFormat.timeZone = TimeZone.getTimeZone("UTC")
                val date = dateFormat.parse(dateString) ?: return false

                val docCalendar = Calendar.getInstance()
                docCalendar.time = date

                val docMonth = docCalendar.get(Calendar.MONTH)
                val docYear = docCalendar.get(Calendar.YEAR)

                docMonth == selectedMonth && docYear == selectedYear
            } catch (e: Exception) {
                false
            }
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
        weekSpinner = root.findViewById(R.id.weekSpinner)
        monthDisplay = root.findViewById(R.id.monthDisplay)
        monthContainer = root.findViewById(R.id.monthContainer)
        monthIcon = root.findViewById(R.id.monthIcon)

        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(defaultZoom)
        mapView.controller.setCenter(defaultCenter)

        setupMonthClickListener()
        updateMonthDisplay()
        loadGeoJsonThenData()

        return root
    }

    private fun setupMonthClickListener() {
        monthIcon.setOnClickListener { showDatePickerDialog() }
        monthContainer.setOnClickListener { showDatePickerDialog() }
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        calendar.set(selectedYear, selectedMonth, selectedDay)

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                selectedYear = year
                selectedMonth = month
                selectedDay = day
                updateMonthDisplay()
                val calculatedWeek = getWeekFromDate(year, month, day)
                currentWeekFilter = "Week $calculatedWeek"
                refreshWeekSpinner()
                refreshDiseaseSpinner()
                renderDiseasePolygons()
            },
            selectedYear,
            selectedMonth,
            selectedDay
        )
        datePickerDialog.datePicker.maxDate = System.currentTimeMillis()
        datePickerDialog.show()
    }

    private fun updateMonthDisplay() {
        val monthName = monthNames[selectedMonth]
        monthDisplay.text = "$monthName $selectedYear"
    }

    private fun setupWeekSpinner() {
        val availableWeeks = getAvailableWeeksForSelectedDate()
        val weekOptions = (1..4).filter { availableWeeks.contains(it) }.map { "Week $it" }.toMutableList()
        if (weekOptions.isEmpty()) weekOptions.add("Week 1")

        // Auto-select current week
        val currentWeek = getWeekFromDate(selectedYear, selectedMonth, selectedDay)
        val targetWeek = "Week $currentWeek"
        val targetIndex = weekOptions.indexOf(targetWeek)
        currentWeekFilter = if (targetIndex >= 0) targetWeek else weekOptions[0]

        val adapter = ArrayAdapter(requireContext(), R.layout.custom_spinner_item, weekOptions)
        adapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item)
        weekSpinner.adapter = adapter

        weekSpinner.setSelection(weekOptions.indexOf(currentWeekFilter))
        weekSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentWeekFilter = weekOptions[position]
                refreshDiseaseSpinner()
                renderDiseasePolygons()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun refreshWeekSpinner() = setupWeekSpinner()

    private fun refreshDiseaseSpinner() {
        val weekNum = currentWeekFilter.replace("Week ", "").toIntOrNull() ?: 1
        val diseasesWithRecords = getDiseasesWithRecords(selectedMonth, selectedYear, weekNum)

        val diseaseOptions = mutableListOf("All Diseases")
        diseaseOptions.addAll(diseasesWithRecords)

        val adapter = ArrayAdapter(requireContext(), R.layout.custom_spinner_item, diseaseOptions)
        adapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item)
        spinnerDisease.adapter = adapter

        val prevSelection = selectedDisease
        spinnerDisease.setSelection(prevSelection?.let { diseaseOptions.indexOf(it) } ?: 0)
        selectedDisease = spinnerDisease.selectedItem as String

        spinnerDisease.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedDisease = parent.getItemAtPosition(position) as String
                renderDiseasePolygons()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun getDiseasesWithRecords(month: Int, year: Int, week: Int): List<String> {
        return records.filter { isDateFromSelectedMonth(it.dateReported, month, year) && it.week == week }
            .map { it.diseaseDisplay }.distinct().sorted()
    }

    private fun getAvailableWeeksForSelectedDate(): Set<Int> {
        return records.filter { isDateFromSelectedMonth(it.dateReported, selectedMonth, selectedYear) }
            .map { it.week }.toSet()
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
                    if (municipalityRaw.isBlank() || municipalityRaw.equals("Unknown", true)) {
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
                        "Polygon" -> polygons.add(coordsToGeoPoints(geometry.getJSONArray("coordinates").getJSONArray(0)))
                        "MultiPolygon" -> {
                            val arr = geometry.getJSONArray("coordinates")
                            for (m in 0 until arr.length()) {
                                polygons.add(coordsToGeoPoints(arr.getJSONArray(m).getJSONArray(0)))
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
                }
                mapView.overlays.add(poly)
            }
        }
        mapView.invalidate()
    }

    private fun loadCasesFromFirestore() {
        records.clear()
        db.collection("healthradarDB").document("centralizedData").collection("allCases")
            .get()
            .addOnSuccessListener { docs ->
                for (doc in docs) {
                    try {
                        val disease = doc.getString("DiseaseName") ?: continue
                        val barangay = doc.getString("Barangay") ?: ""
                        val municipality = doc.getString("Municipality") ?: ""
                        val count = (doc.get("CaseCount") as? Number)?.toInt() ?: 0
                        val week = (doc.get("Week") as? Number)?.toInt() ?: 1
                        val dateReported = doc.getString("DateReported") ?: doc.getString("uploadedAt") ?: ""

                        records.add(
                            Record(
                                normalize(disease),
                                disease.trim(),
                                normalize(barangay),
                                normalize(municipality),
                                count,
                                week,
                                dateReported
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing document: ${doc.id}", e)
                    }
                }

                setupWeekSpinner()
                refreshDiseaseSpinner()
                renderDiseasePolygons()
            }
            .addOnFailureListener { ex -> Log.e(TAG, "Firestore load failed", ex) }
    }

    private fun renderDiseasePolygons() {
        mapView.overlays.removeAll(mapView.overlays.filterIsInstance<Polygon>())

        val selected = selectedDisease ?: "All Diseases"
        val weekNum = currentWeekFilter.replace("Week ", "").toIntOrNull() ?: 1

        val caseMap = mutableMapOf<String, MutableMap<String, Int>>()
        for (r in records) {
            if (!isDateFromSelectedMonth(r.dateReported, selectedMonth, selectedYear)) continue
            if (r.week != weekNum) continue
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
                    if (!diseases.isNullOrEmpty()) {
                        setOnClickListener { _, _, _ ->
                            activeSheet?.dismiss()
                            activeSheet = null

                            val muniName = if (f.municipality.isBlank() || f.municipality.equals("Unknown", true))
                                "Unknown" else f.municipality

                            val center = getPolygonCenter(ring)
                            mapView.controller.animateTo(center)
                            mapView.controller.setZoom(zoomOnBarangaySelect)

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

    private fun getPolygonCenter(points: List<GeoPoint>): GeoPoint {
        val lat = points.sumOf { it.latitude } / points.size
        val lon = points.sumOf { it.longitude } / points.size
        return GeoPoint(lat, lon)
    }

    @SuppressLint("InflateParams")
    private fun showBottomSheet(barangay: String, municipality: String, diseases: Map<String, Int>) {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.info_card, null)

        val title = view.findViewById<TextView>(R.id.textBarangay)
        val muni = view.findViewById<TextView>(R.id.textMunicipality)
        val total = view.findViewById<TextView>(R.id.textTotal)
        val listLayout = view.findViewById<LinearLayout>(R.id.listDiseases)
        val weekInfo = view.findViewById<TextView>(R.id.textWeekInfo)

        title.text = barangay
        muni.text = "Municipality: $municipality"
        weekInfo.text = "Period: ${monthDisplay.text} • $currentWeekFilter"

        val totalCases = diseases.values.sum()
        total.text = "Total Cases: $totalCases"
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
            tv.textSize = 14f
            tv.setTextColor(Color.BLACK)
            tv.setTypeface(Typeface.DEFAULT_BOLD)
            tv.setPadding(8, 4, 8, 4)
            listLayout.addView(tv)
        }

        dialog.setContentView(view)
        dialog.show()
        activeSheet = dialog
    }
}
