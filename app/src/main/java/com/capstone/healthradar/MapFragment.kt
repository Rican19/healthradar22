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
import java.text.SimpleDateFormat
import java.text.Normalizer
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
    private val diseaseDisplayList = mutableListOf<String>()
    private val geoFeatures = mutableListOf<GeoFeature>()
    private val exec = Executors.newSingleThreadExecutor()

    private var selectedDisease: String? = null
    private var currentWeekFilter = "Week 1"
    private var selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH)
    private var selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    private var selectedDay: Int = 1 // Default to 1st day
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
        monthIcon.setOnClickListener {
            showDatePickerDialog()
        }

        monthContainer.setOnClickListener {
            showDatePickerDialog()
        }
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
        val weekOptions = mutableListOf<String>()

        for (week in 1..4) {
            if (week in availableWeeks) {
                weekOptions.add("Week $week")
            }
        }

        if (weekOptions.isEmpty()) {
            weekOptions.add("Week 1")
        }

        val calculatedWeek = getWeekFromDate(selectedYear, selectedMonth, selectedDay)
        val targetWeek = "Week $calculatedWeek"
        val targetIndex = weekOptions.indexOf(targetWeek)

        if (targetIndex >= 0) {
            currentWeekFilter = targetWeek
        } else if (weekOptions.isNotEmpty()) {
            currentWeekFilter = weekOptions[0]
        }

        val adapter = object : ArrayAdapter<String>(
            requireContext(),
            R.layout.custom_spinner_item,
            weekOptions
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.setTextColor(Color.BLACK)
                textView.textSize = 14f
                textView.setTypeface(null, Typeface.BOLD)

                val weekText = getItem(position) ?: ""
                val weekNum = weekText.replace("Week ", "").toIntOrNull()
                val isCurrentDateWeek = weekNum == getWeekFromDate(selectedYear, selectedMonth, selectedDay)

                textView.text = if (isCurrentDateWeek) {
                    "$weekText (Current)"
                } else {
                    weekText
                }

                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.setTextColor(Color.BLACK)
                textView.textSize = 14f
                textView.setTypeface(null, Typeface.NORMAL)

                val weekText = getItem(position) ?: ""
                val weekNum = weekText.replace("Week ", "").toIntOrNull()
                val isCurrentDateWeek = weekNum == getWeekFromDate(selectedYear, selectedMonth, selectedDay)

                textView.text = if (isCurrentDateWeek) {
                    "$weekText (Current)"
                } else {
                    weekText
                }

                return view
            }
        }

        adapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item)
        weekSpinner.adapter = adapter

        weekSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position < weekOptions.size) {
                    currentWeekFilter = weekOptions[position].replace(" (Current)", "")
                    refreshDiseaseSpinner()
                    renderDiseasePolygons()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        weekSpinner.post {
            if (weekOptions.isNotEmpty()) {
                val targetWeek = "Week ${getWeekFromDate(selectedYear, selectedMonth, selectedDay)}"
                val targetIndex = weekOptions.indexOfFirst { it.startsWith(targetWeek) }
                if (targetIndex >= 0) {
                    weekSpinner.setSelection(targetIndex)
                } else {
                    weekSpinner.setSelection(0)
                }
            }
        }
    }

    private fun refreshWeekSpinner() {
        setupWeekSpinner()
    }

    private fun refreshDiseaseSpinner() {
        val weekNum = currentWeekFilter.replace("Week ", "").toIntOrNull() ?: 1
        val diseasesWithRecords = getDiseasesWithRecords(selectedMonth, selectedYear, weekNum)

        val diseaseOptions = mutableListOf("All Diseases")
        diseaseOptions.addAll(diseasesWithRecords)

        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.custom_spinner_item,
            diseaseOptions
        )
        adapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item)
        spinnerDisease.adapter = adapter
        val previousSelection = selectedDisease
        if (previousSelection != null && diseaseOptions.contains(previousSelection)) {
            val index = diseaseOptions.indexOf(previousSelection)
            spinnerDisease.setSelection(index)
        } else {
            spinnerDisease.setSelection(0)
            selectedDisease = "All Diseases"
        }

        spinnerDisease.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selected = parent.getItemAtPosition(position) as String
                selectedDisease = selected
                renderDiseasePolygons()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun getDiseasesWithRecords(month: Int, year: Int, week: Int): List<String> {
        val diseases = mutableSetOf<String>()

        for (record in records) {
            if (isDateFromSelectedMonth(record.dateReported, month, year) && record.week == week) {
                diseases.add(record.diseaseDisplay)
            }
        }

        return diseases.sorted()
    }

    private fun getAvailableWeeksForSelectedDate(): Set<Int> {
        val availableWeeks = mutableSetOf<Int>()
        for (record in records) {
            if (isDateFromSelectedMonth(record.dateReported, selectedMonth, selectedYear)) {
                availableWeeks.add(record.week)
            }
        }
        return availableWeeks
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
            .addOnFailureListener { ex ->
                Log.e(TAG, "Firestore load failed", ex)
            }
    }

    private fun renderDiseasePolygons() {
        mapView.overlays.removeAll(mapView.overlays.filterIsInstance<Polygon>())

        val selected = selectedDisease ?: "All Diseases"
        val weekNum = currentWeekFilter.replace("Week ", "").toIntOrNull() ?: 1

        val caseMap = mutableMapOf<String, MutableMap<String, Int>>()

        for (r in records) {
            // Filter by selected month
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
                                inferMunicipality(f.barangay) else f.municipality

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

    @SuppressLint("InflateParams", "SetTextI18n")
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

        // Show selected date in info card
        val monthName = monthNames[selectedMonth]
        val dateStr = String.format("%02d/%02d/%04d", selectedMonth + 1, selectedDay, selectedYear)
        weekInfo.text = "Period: $monthName $selectedYear • $currentWeekFilter • $dateStr"
        weekInfo.setTextColor(Color.GRAY)

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
            tv.textSize = 15f
            tv.setTextColor(Color.BLACK)
            listLayout.addView(tv)
        }

        dialog.setContentView(view)
        dialog.show()
        activeSheet = dialog
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

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        activeSheet?.dismiss()
        exec.shutdownNow()
    }

    // ADD THIS METHOD
    fun refreshData() {
        Log.d(TAG, "Refreshing map data...")

        // Reload cases from Firestore
        loadCasesFromFirestore()

        // Update month display
        updateMonthDisplay()

        // Refresh the spinners
        refreshWeekSpinner()
        refreshDiseaseSpinner()

        // Re-render polygons
        renderDiseasePolygons()

        Toast.makeText(requireContext(), "Map data refreshed", Toast.LENGTH_SHORT).show()
    }
}