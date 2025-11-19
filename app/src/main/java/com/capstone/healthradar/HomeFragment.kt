package com.capstone.healthradar

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.utils.MPPointF
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class HomeFragment : Fragment() {

    private lateinit var barChart: BarChart
    private lateinit var pieChartPager: ViewPager2
    private lateinit var currentMonthTv: TextView
    private lateinit var latestRecordsContainer: LinearLayout
    private lateinit var btnShowAll: TextView
    private lateinit var lineGraphDescription: TextView
    private lateinit var currentMunicipalityTv: TextView
    private lateinit var municipalityIndicator: LinearLayout
    private lateinit var userNameTv: TextView
    private lateinit var weekSpinner: Spinner

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val TAG = "HomeFragment"

    private val pieColors = listOf(
        "#FF6B6B", "#4DB6AC", "#64B5F6", "#FFB74D", "#BA68C8",
        "#81C784", "#7986CB", "#F06292", "#AED581", "#FFD54F",
        "#4FC3F7", "#9575CD", "#4DD0E1", "#F48FB1", "#A1887F"
    ).map { it.toColorInt() }

    private val municipalities = listOf("Mandaue", "Liloan", "Consolacion")
    private val allWeeks = listOf("All Weeks", "Week 1", "Week 2", "Week 3", "Week 4")
    private var showAllRecords = false
    private var currentMunicipalityIndex = 0
    private var currentWeekFilter = "All Weeks"

    // Store current disease list for each municipality
    private val currentDiseaseLists = mutableMapOf<Int, List<PagerDiseaseItem>>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        bindViews(view)
        initUi()
        setupPieChartPager()
        loadUserName()
        loadLatestRecords()
        return view
    }

    private fun bindViews(root: View) {
        barChart = root.findViewById(R.id.barChart)
        pieChartPager = root.findViewById(R.id.pieChartPager)
        currentMonthTv = root.findViewById(R.id.currentMonth)
        latestRecordsContainer = root.findViewById(R.id.latestRecordsContainer)
        btnShowAll = root.findViewById(R.id.btnShowAll)
        lineGraphDescription = root.findViewById(R.id.lineGraphDescription)
        currentMunicipalityTv = root.findViewById(R.id.currentMunicipality)
        municipalityIndicator = root.findViewById(R.id.municipalityIndicator)
        userNameTv = root.findViewById(R.id.userName)
        weekSpinner = root.findViewById(R.id.weekSpinner)
    }

    private fun initUi() {
        currentMonthTv.text = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
        setupCharts()
        setupActions()
        setupMunicipalityIndicator()
        setupWeekSpinner()
    }

    private fun setupWeekSpinner() {
        // Use default Android layout for now to avoid crashes
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, allWeeks).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        weekSpinner.adapter = adapter

        // Style the spinner programmatically
        weekSpinner.setPopupBackgroundResource(android.R.color.transparent)

        // Set text color to white for selected item
        weekSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentWeekFilter = allWeeks[position]
                refreshChartData()

                // Set white text color for selected item
                (view as? TextView)?.setTextColor(Color.WHITE)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Set initial text color
        (weekSpinner.selectedView as? TextView)?.setTextColor(Color.WHITE)
    }

    private fun loadUserName() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val displayName = currentUser.displayName
            if (!displayName.isNullOrEmpty()) {
                userNameTv.text = "Hello, $displayName"
            } else {
                val email = currentUser.email
                val name = email?.substringBefore('@') ?: "User"
                userNameTv.text = "Hello, $name"
            }
        } else {
            userNameTv.text = "Hello, User"
        }
    }

    private fun refreshChartData() {
        val currentMunicipality = municipalities[currentMunicipalityIndex]
        if (currentWeekFilter == "All Weeks") {
            loadBarChart(currentMunicipality)
        } else {
            loadFilteredBarChart(currentMunicipality, currentWeekFilter)
        }
        (pieChartPager.adapter as? PieChartPagerAdapter)?.refreshCurrentPage()
    }

    private fun setupPieChartPager() {
        pieChartPager.adapter = PieChartPagerAdapter()
        pieChartPager.orientation = ViewPager2.ORIENTATION_HORIZONTAL

        pieChartPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentMunicipalityIndex = position
                updateMunicipalityIndicator()
                currentMunicipalityTv.text = municipalities[position]
                refreshChartData()
                loadLatestRecords()
            }
        })

        currentMunicipalityTv.text = municipalities[currentMunicipalityIndex]
        updateMunicipalityIndicator()
    }

    private fun setupMunicipalityIndicator() {
        municipalityIndicator.removeAllViews()
        for (i in municipalities.indices) {
            val dot = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(10), dpToPx(10)).apply {
                    marginEnd = dpToPx(6)
                }
                setBackgroundColor(if (i == currentMunicipalityIndex) Color.parseColor("#5A4CE1") else Color.parseColor("#666666"))
            }
            municipalityIndicator.addView(dot)
        }
    }

    private fun updateMunicipalityIndicator() {
        for (i in 0 until municipalityIndicator.childCount) {
            val dot = municipalityIndicator.getChildAt(i)
            dot.setBackgroundColor(if (i == currentMunicipalityIndex) Color.parseColor("#5A4CE1") else Color.parseColor("#666666"))
        }
    }

    private fun setupCharts() {
        setupBarChart()
    }

    private fun setupActions() {
        btnShowAll.setOnClickListener {
            showAllRecords = !showAllRecords
            btnShowAll.text = if (showAllRecords) "Show less" else "Show all"
            loadLatestRecords()
        }
    }

    private fun setupPieChart(pieChart: PieChart, diseaseContainer: LinearLayout, position: Int) {
        pieChart.apply {
            setBackgroundColor(Color.TRANSPARENT)
            setUsePercentValues(false)
            isDrawHoleEnabled = false
            setEntryLabelColor(Color.TRANSPARENT)
            description.isEnabled = false
            legend.isEnabled = false
            isRotationEnabled = false
            setDrawEntryLabels(false)
            setDrawCenterText(false)
            setDrawRoundedSlices(false)
            setEntryLabelTextSize(0f)
            setExtraOffsets(20f, 20f, 20f, 20f)
            minAngleForSlices = 15f
            setTouchEnabled(true)
            setNoDataText("Loading data...")
            setNoDataTextColor(Color.WHITE)
            setDrawSliceText(false)

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    if (e != null && h != null) {
                        val sliceIndex = h.x.toInt()
                        highlightDiseaseInList(sliceIndex, diseaseContainer, position)
                    }
                }

                override fun onNothingSelected() {
                    removeHighlightFromDiseaseList(diseaseContainer)
                }
            })
        }
    }

    private fun highlightDiseaseInList(sliceIndex: Int, diseaseContainer: LinearLayout, municipalityPosition: Int) {
        val diseaseList = currentDiseaseLists[municipalityPosition] ?: return

        if (sliceIndex < diseaseList.size) {
            removeHighlightFromDiseaseList(diseaseContainer)

            val selectedView = diseaseContainer.getChildAt(sliceIndex)
            selectedView?.setBackgroundColor(Color.parseColor("#333366"))

            diseaseContainer.post {
                selectedView?.let {
                    val scrollView = diseaseContainer.parent as? ScrollView
                    scrollView?.smoothScrollTo(0, it.top)
                }
            }
        }
    }

    private fun removeHighlightFromDiseaseList(diseaseContainer: LinearLayout) {
        for (i in 0 until diseaseContainer.childCount) {
            val view = diseaseContainer.getChildAt(i)
            view.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    private fun setupBarChart() {
        barChart.apply {
            setBackgroundColor(Color.TRANSPARENT)
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(false)
            setDrawBarShadow(false)
            setDrawValueAboveBar(false) // Values inside bars now

            // X-axis customization
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = Color.WHITE
                textSize = 11f
                granularity = 1f
                setLabelCount(4, true)
                setCenterAxisLabels(false)
                axisMinimum = -0.5f
                axisMaximum = 3.5f
                labelRotationAngle = 0f
            }

            // Left Y-axis customization
            axisLeft.apply {
                textColor = Color.WHITE
                setDrawGridLines(true)
                gridColor = Color.parseColor("#333333")
                gridLineWidth = 0.8f
                axisLineColor = Color.parseColor("#666666")
                axisLineWidth = 1f
                axisMinimum = 0f
                granularity = 1f
                setDrawAxisLine(true)
            }

            axisRight.isEnabled = false
            legend.isEnabled = false

            setNoDataText("No weekly data available\n\nCases will appear here when they are reported by health centers")
            setNoDataTextColor(Color.parseColor("#888888"))
            setNoDataTextTypeface(Typeface.DEFAULT_BOLD)

            // Better spacing
            setExtraOffsets(20f, 20f, 20f, 25f)

            // Add click listener for interactive bars - FIXED VERSION
            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    if (e != null && h != null) {
                        val weekIndex = h.x.toInt()
                        val weekNumber = weekIndex + 1
                        val cases = e.y.toInt()

                        // Show detailed info in description
                        lineGraphDescription.text = "Week $weekNumber: $cases ${if (cases == 1) "case" else "cases"}"

                        // REMOVED the recursive highlightValue() call that was causing the crash
                        // The bar is already highlighted automatically by the chart library
                    }
                }

                override fun onNothingSelected() {
                    // Reset description when nothing is selected
                    lineGraphDescription.text = "Tap on any bar to see weekly details"
                }
            })
        }
    }

    private fun loadBarChart(municipality: String) {
        lineGraphDescription.text = "Loading weekly data for $municipality..."

        db.collection("healthradarDB").document("centralizedData").collection("allCases")
            .get()
            .addOnSuccessListener { snapshot ->
                try {
                    val docs = snapshot.documents.filter {
                        it.getString("Municipality")?.replace("-", "")?.lowercase() ==
                                municipality.replace("-", "").lowercase()
                    }

                    // Create a map for all 4 weeks with default 0 values
                    val weekMap = mutableMapOf<Int, Float>()

                    // Initialize ALL 4 weeks with 0 values
                    for (week in 1..4) {
                        weekMap[week] = 0f
                    }

                    // Fill with actual data from Firebase
                    for (doc in docs) {
                        val cases = when (val raw = doc.get("CaseCount")) {
                            is Number -> raw.toFloat()
                            is String -> raw.toFloatOrNull() ?: 0f
                            else -> 0f
                        }
                        val weekNum = when (val w = doc.get("Week")) {
                            is Number -> w.toInt()
                            is String -> w.toIntOrNull() ?: -1
                            else -> -1
                        }
                        if (weekNum in 1..4 && cases > 0f) {
                            weekMap[weekNum] = (weekMap[weekNum] ?: 0f) + cases
                        }
                    }

                    // Create entries for all 4 weeks
                    val entries = ArrayList<BarEntry>()
                    entries.add(BarEntry(0f, weekMap[1] ?: 0f)) // Week 1
                    entries.add(BarEntry(1f, weekMap[2] ?: 0f)) // Week 2
                    entries.add(BarEntry(2f, weekMap[3] ?: 0f)) // Week 3
                    entries.add(BarEntry(3f, weekMap[4] ?: 0f)) // Week 4

                    val barDataSet = BarDataSet(entries, "Weekly Cases - $municipality").apply {
                        // Different shades for each week
                        val colors = listOf(
                            Color.parseColor("#7B68EE"), // Week 1 - Light purple
                            Color.parseColor("#6A5ACD"), // Week 2 - Medium purple
                            Color.parseColor("#5A4CE1"), // Week 3 - Brand purple
                            Color.parseColor("#483D8B")  // Week 4 - Dark purple
                        )
                        this.colors = colors
                        valueTextColor = Color.WHITE
                        valueTextSize = 10f
                        setDrawValues(true)
                        // Only show values for bars with data
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                return if (value > 0) value.toInt().toString() else ""
                            }
                        }
                    }

                    val barData = BarData(barDataSet).apply {
                        barWidth = 0.6f
                        setValueTextSize(10f)
                    }

                    // Always show labels for all 4 weeks
                    val labels = listOf("Week 1", "Week 2", "Week 3", "Week 4")
                    barChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
                    barChart.xAxis.setLabelCount(4, true)
                    barChart.xAxis.axisMinimum = -0.5f
                    barChart.xAxis.axisMaximum = 3.5f

                    barChart.data = barData
                    barChart.setVisibleXRange(-0.5f, 3.5f)
                    barChart.invalidate()

                    // Enhanced animations
                    barChart.animateY(800, Easing.EaseInOutCubic)
                    barChart.animateX(600, Easing.EaseInOutQuad)

                    val totalShown = weekMap.values.sum().roundToInt()
                    val weeksWithData = weekMap.count { it.value > 0 }

                    if (totalShown == 0) {
                        lineGraphDescription.text = "No case data found for $municipality. Data will appear here when cases are reported."
                    } else {
                        lineGraphDescription.text = "Tap on any bar to see weekly details"
                        // Add average line if we have data
                        addAverageLine(totalShown.toFloat())
                    }

                    // Debug log to check what data we have
                    Log.d(TAG, "Week data for $municipality: Week1=${weekMap[1]}, Week2=${weekMap[2]}, Week3=${weekMap[3]}, Week4=${weekMap[4]}")

                } catch (ex: Exception) {
                    Log.e(TAG, "Error building bar chart", ex)
                    barChart.clear()
                    lineGraphDescription.text = "Error loading data. Please try again."
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "loadBarChart failed", e)
                barChart.clear()
                lineGraphDescription.text = "Failed to load data. Check your connection."
            }
    }

    private fun addAverageLine(totalCases: Float) {
        val average = totalCases / 4f // Average across 4 weeks

        val limitLine = LimitLine(average, "Weekly Average")
        limitLine.lineColor = Color.parseColor("#FF6B6B")
        limitLine.lineWidth = 1.5f
        limitLine.textColor = Color.WHITE
        limitLine.textSize = 10f
        limitLine.enableDashedLine(10f, 10f, 0f) // Dashed line

        barChart.axisLeft.removeAllLimitLines() // Clear previous lines
        barChart.axisLeft.addLimitLine(limitLine)
    }

    private fun loadFilteredBarChart(municipality: String, selectedWeek: String) {
        val weekNum = selectedWeek.replace("Week ", "").toIntOrNull() ?: return

        db.collection("healthradarDB").document("centralizedData").collection("allCases")
            .get()
            .addOnSuccessListener { snapshot ->
                try {
                    val docs = snapshot.documents.filter {
                        it.getString("Municipality")?.replace("-", "")?.lowercase() ==
                                municipality.replace("-", "").lowercase()
                    }

                    if (docs.isEmpty()) {
                        barChart.clear()
                        lineGraphDescription.text = "No data available for $selectedWeek in $municipality"
                        return@addOnSuccessListener
                    }

                    // Filter by specific week
                    val weekDocs = docs.filter { doc ->
                        val docWeekNum = when (val w = doc.get("Week")) {
                            is Number -> w.toInt()
                            is String -> w.toIntOrNull() ?: -1
                            else -> -1
                        }
                        docWeekNum == weekNum
                    }

                    if (weekDocs.isEmpty()) {
                        barChart.clear()
                        lineGraphDescription.text = "No data available for $selectedWeek in $municipality"
                        return@addOnSuccessListener
                    }

                    // Group by disease for the selected week
                    val diseaseMap = mutableMapOf<String, Float>()
                    for (doc in weekDocs) {
                        val disease = doc.getString("DiseaseName") ?: "Unknown"
                        val cases = when (val raw = doc.get("CaseCount")) {
                            is Number -> raw.toFloat()
                            is String -> raw.toFloatOrNull() ?: 0f
                            else -> 0f
                        }
                        if (disease.isNotBlank() && disease != "Unknown" && cases > 0f) {
                            diseaseMap[disease] = (diseaseMap[disease] ?: 0f) + cases
                        }
                    }

                    if (diseaseMap.isEmpty()) {
                        barChart.clear()
                        lineGraphDescription.text = "No case data for $selectedWeek in $municipality"
                        return@addOnSuccessListener
                    }

                    // Create bar chart entries for diseases
                    val sortedDiseases = diseaseMap.entries.sortedByDescending { it.value }
                    val entries = ArrayList<BarEntry>()
                    sortedDiseases.forEachIndexed { idx, entry ->
                        entries.add(BarEntry(idx.toFloat(), entry.value))
                    }

                    val barDataSet = BarDataSet(entries, "$selectedWeek Cases - $municipality").apply {
                        color = Color.parseColor("#5A4CE1")
                        valueTextColor = Color.WHITE
                        valueTextSize = 10f
                        setDrawValues(true)
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                return if (value > 0) value.toInt().toString() else ""
                            }
                        }
                    }

                    val labels = sortedDiseases.map { it.key }
                    barChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)

                    val barData = BarData(barDataSet).apply {
                        barWidth = 0.6f
                        setValueTextSize(10f)
                    }

                    barChart.data = barData
                    barChart.animateY(800, Easing.EaseInOutCubic)

                    val totalCases = diseaseMap.values.sum().roundToInt()
                    lineGraphDescription.text = "$selectedWeek cases in $municipality — $totalCases total cases across ${diseaseMap.size} diseases"

                } catch (ex: Exception) {
                    Log.e(TAG, "Error filtering bar chart by week", ex)
                    barChart.clear()
                    lineGraphDescription.text = "Error filtering data for $selectedWeek"
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "loadFilteredBarChart failed", e)
                barChart.clear()
                lineGraphDescription.text = "Failed to filter data for $selectedWeek"
            }
    }

    private fun loadPieChartData(municipality: String, pieChart: PieChart, diseaseContainer: LinearLayout, position: Int) {
        db.collection("healthradarDB").document("centralizedData").collection("allCases")
            .get()
            .addOnSuccessListener { snapshot ->
                try {
                    val diseaseTotals = mutableMapOf<String, Float>()

                    val municipalDocs = snapshot.documents.filter { doc ->
                        val docMunicipality = doc.getString("Municipality") ?: ""
                        docMunicipality.replace("-", "")?.lowercase() ==
                                municipality.replace("-", "").lowercase()
                    }

                    for (doc in municipalDocs) {
                        val disease = doc.getString("DiseaseName") ?: "Unknown"
                        if (disease.isNotBlank() && disease != "Unknown") {
                            val cases = when (val raw = doc.get("CaseCount")) {
                                is Number -> raw.toFloat()
                                is String -> raw.toFloatOrNull() ?: 0f
                                else -> 0f
                            }
                            if (cases > 0f) {
                                diseaseTotals[disease] = (diseaseTotals[disease] ?: 0f) + cases
                            }
                        }
                    }

                    if (diseaseTotals.isEmpty()) {
                        pieChart.clear()
                        updateDiseaseList(emptyList(), diseaseContainer)
                        return@addOnSuccessListener
                    }

                    val sorted = diseaseTotals.entries.sortedByDescending { it.value }
                    val total = sorted.sumOf { it.value.toDouble() }.toFloat()

                    val entries = ArrayList<PieEntry>()
                    val diseaseList = mutableListOf<PagerDiseaseItem>()

                    for ((disease, totalCases) in sorted) {
                        entries.add(PieEntry(totalCases, disease))
                        val percent = if (total > 0f) (totalCases / total * 100f) else 0f
                        val percentInt = percent.roundToInt().coerceAtLeast(0)

                        diseaseList.add(PagerDiseaseItem(
                            disease,
                            totalCases.roundToInt(),
                            percentInt,
                            pieColors[diseaseList.size % pieColors.size]
                        ))
                    }

                    currentDiseaseLists[position] = diseaseList

                    val ds = PieDataSet(entries, "").apply {
                        colors = pieColors.take(entries.size)
                        valueTextColor = Color.TRANSPARENT
                        valueTextSize = 0f
                        sliceSpace = 2f
                        selectionShift = 8f
                        setDrawValues(false)
                        yValuePosition = PieDataSet.ValuePosition.INSIDE_SLICE
                    }

                    val pieData = PieData(ds)
                    pieChart.data = pieData
                    pieChart.animateY(1000)

                    updateDiseaseList(diseaseList, diseaseContainer)

                } catch (ex: Exception) {
                    Log.e(TAG, "Error building pie chart", ex)
                    pieChart.clear()
                    updateDiseaseList(emptyList(), diseaseContainer)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "loadPieChart failed", e)
                pieChart.clear()
                updateDiseaseList(emptyList(), diseaseContainer)
            }
    }

    private fun updateDiseaseList(diseaseList: List<PagerDiseaseItem>, container: LinearLayout) {
        container.removeAllViews()

        if (diseaseList.isEmpty()) {
            val emptyText = TextView(requireContext()).apply {
                text = "No disease data available"
                textSize = 14f
                setTextColor(Color.GRAY)
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(20), 0, 0)
            }
            container.addView(emptyText)
            return
        }

        for (item in diseaseList) {
            container.addView(createDiseaseItem(item))
        }
    }

    private fun createDiseaseItem(item: PagerDiseaseItem): View {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(6)
                bottomMargin = dpToPx(6)
            }
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
        }

        val colorView = View(requireContext()).apply {
            setBackgroundColor(item.colorInt)
            layoutParams = LinearLayout.LayoutParams(dpToPx(10), dpToPx(10)).apply {
                marginEnd = dpToPx(10)
            }
        }

        val nameTv = TextView(requireContext()).apply {
            text = item.disease
            textSize = 12f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }

        val casesTv = TextView(requireContext()).apply {
            text = "${item.combinedCases} cases"
            textSize = 11f
            setTextColor(Color.parseColor("#5A4CE1"))
            setTypeface(null, Typeface.BOLD)
        }

        container.addView(colorView)
        container.addView(nameTv)
        container.addView(casesTv)

        return container
    }

    private fun loadLatestRecords() {
        db.collection("healthradarDB").document("centralizedData").collection("allCases")
            .orderBy("uploadedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                try {
                    val docs = snapshot.documents
                    latestRecordsContainer.removeAllViews()

                    val toShow = if (!showAllRecords) docs.take(5) else docs

                    if (toShow.isEmpty()) {
                        val emptyText = TextView(requireContext()).apply {
                            text = "No disease records found"
                            textSize = 14f
                            setTextColor(Color.GRAY)
                            gravity = Gravity.CENTER
                        }
                        latestRecordsContainer.addView(emptyText)
                        return@addOnSuccessListener
                    }

                    for (doc in toShow) {
                        val disease = doc.getString("DiseaseName") ?: "Unknown"
                        val cases = when (val raw = doc.get("CaseCount")) {
                            is Number -> raw.toInt()
                            is String -> raw.toIntOrNull() ?: 0
                            else -> 0
                        }
                        val muni = doc.getString("Municipality") ?: ""
                        val dateStr = doc.getString("DateReported") ?: doc.getString("uploadedAt") ?: ""

                        latestRecordsContainer.addView(createRecordCard(disease, cases, muni, dateStr))
                    }
                } catch (ex: Exception) {
                    Log.e(TAG, "Error building latest records", ex)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "loadLatestRecords failed", e)
            }
    }

    private fun createRecordCard(disease: String, cases: Int, muni: String, dateStr: String): View {
        val card = CardView(requireContext()).apply {
            radius = dpToPx(10).toFloat()
            cardElevation = dpToPx(2).toFloat()
            useCompatPadding = true
            setCardBackgroundColor(Color.parseColor("#1E1E1E"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(8)
                bottomMargin = dpToPx(8)
            }
        }

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
        }

        val topRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val nameTv = TextView(requireContext()).apply {
            text = disease
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val casesTv = TextView(requireContext()).apply {
            text = "$cases cases"
            textSize = 14f
            setTextColor(Color.parseColor("#5A4CE1"))
        }

        topRow.addView(nameTv)
        topRow.addView(casesTv)

        val subTv = TextView(requireContext()).apply {
            text = "$muni • $dateStr"
            textSize = 13f
            setTextColor(Color.GRAY)
            setPadding(0, dpToPx(6), 0, 0)
        }

        container.addView(topRow)
        container.addView(subTv)
        card.addView(container)
        return card
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private data class PagerDiseaseItem(
        val disease: String,
        val combinedCases: Int,
        val percent: Int,
        val colorInt: Int
    )

    private inner class PieChartPagerAdapter : RecyclerView.Adapter<PieChartPagerAdapter.PieChartVH>() {

        inner class PieChartVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val pieChart: PieChart = itemView.findViewById(R.id.pieChartItem)
            val diseaseContainer: LinearLayout = itemView.findViewById(R.id.diseaseContainer)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PieChartVH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pie_chart, parent, false)
            return PieChartVH(view)
        }

        override fun onBindViewHolder(holder: PieChartVH, position: Int) {
            val municipality = municipalities[position]
            setupPieChart(holder.pieChart, holder.diseaseContainer, position)
            loadPieChartData(municipality, holder.pieChart, holder.diseaseContainer, position)
        }

        override fun getItemCount(): Int = municipalities.size

        fun refreshCurrentPage() {
            val currentPosition = pieChartPager.currentItem
            notifyItemChanged(currentPosition)
        }
    }
}