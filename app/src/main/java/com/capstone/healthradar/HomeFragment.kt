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
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

class HomeFragment : Fragment() {

    private lateinit var barChart: BarChart
    private lateinit var pieChartPager: ViewPager2
    private lateinit var currentMonthTv: TextView
    private lateinit var municipalityIndicator: LinearLayout
    private lateinit var userNameTv: TextView
    private lateinit var weekSpinner: Spinner
    private lateinit var dayLabelsContainer: LinearLayout
    private lateinit var leftArrow: ImageView
    private lateinit var rightArrow: ImageView
    private lateinit var swipeHint: TextView

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val TAG = "HomeFragment"

    private val pieColors = listOf(
        "#FF6B6B", "#4DB6AC", "#64B5F6", "#FFB74D", "#BA68C8",
        "#81C784", "#7986CB", "#F06292", "#AED581", "#FFD54F",
        "#4FC3F7", "#9575CD", "#4DD0E1", "#F48FB1", "#A1887F"
    ).map { it.toColorInt() }

    private val municipalities = listOf("Mandaue", "Liloan", "Consolacion")
    private val allWeeks = listOf("Week 1", "Week 2", "Week 3", "Week 4")
    private var currentMunicipalityIndex = 0
    private var currentWeekFilter = "Week 1"

    // Day labels for the new design
    private val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
    private val fullDayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    // Store current disease list for each municipality
    private val currentDiseaseLists = mutableMapOf<Int, List<PagerDiseaseItem>>()
    private var currentBarChartData: Map<Int, List<DiseaseData>> = mutableMapOf()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        bindViews(view)
        initUi()
        setupPieChartPager()
        loadUserName()
        return view
    }

    private fun bindViews(root: View) {
        barChart = root.findViewById(R.id.barChart)
        pieChartPager = root.findViewById(R.id.pieChartPager)
        currentMonthTv = root.findViewById(R.id.currentMonth)
        municipalityIndicator = root.findViewById(R.id.municipalityIndicator)
        userNameTv = root.findViewById(R.id.userName)
        weekSpinner = root.findViewById(R.id.weekSpinner)
        dayLabelsContainer = root.findViewById(R.id.dayLabels)

        // New views for improved navigation
        leftArrow = root.findViewById(R.id.leftArrow)
        rightArrow = root.findViewById(R.id.rightArrow)
        swipeHint = root.findViewById(R.id.swipeHint)
    }

    private fun initUi() {
        currentMonthTv.text = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
        setupCharts()
        setupMunicipalityIndicator()
        setupWeekSpinner()
        setupDayLabels()
        loadBarChartData() // Load initial bar chart data
    }

    private fun setupDayLabels() {
        dayLabelsContainer.removeAllViews()

        for (day in dayLabels) {
            val textView = TextView(requireContext()).apply {
                text = day
                setTextColor(Color.parseColor("#888888"))
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    gravity = android.view.Gravity.CENTER
                }
            }
            dayLabelsContainer.addView(textView)
        }
    }

    private fun showDiseasePopup(dayIndex: Int, totalCases: Int) {
        val dayLabel = dayLabels[dayIndex]
        val fullDayName = fullDayNames[dayIndex]

        // Get disease information for this day
        val dayData = currentBarChartData[dayIndex]

        // Create custom dialog layout
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_disease_details, null)

        val titleTextView = dialogView.findViewById<TextView>(R.id.dialogTitle)
        val subtitleTextView = dialogView.findViewById<TextView>(R.id.dialogSubtitle)
        val totalCasesText = dialogView.findViewById<TextView>(R.id.totalCasesText)
        val diseaseListView = dialogView.findViewById<LinearLayout>(R.id.diseaseList)
        val closeButton = dialogView.findViewById<Button>(R.id.closeButton)

        titleTextView.text = fullDayName
        subtitleTextView.text = "Cases for $dayLabel"
        totalCasesText.text = "Total Cases: $totalCases"

        // Clear previous disease list
        diseaseListView.removeAllViews()

        if (dayData != null && dayData.isNotEmpty()) {
            for (disease in dayData) {
                val diseaseItemView = LayoutInflater.from(requireContext()).inflate(R.layout.item_disease_detail, null)

                val diseaseNameText = diseaseItemView.findViewById<TextView>(R.id.diseaseName)
                val diseaseCasesText = diseaseItemView.findViewById<TextView>(R.id.diseaseCases)

                diseaseNameText.text = disease.name
                diseaseCasesText.text = "${disease.cases} ${if (disease.cases == 1) "case" else "cases"}"

                diseaseListView.addView(diseaseItemView)
            }
        } else {
            val noDataText = TextView(requireContext()).apply {
                text = "No specific disease data available for this day."
                setTextColor(Color.GRAY)
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(32), 0, 0)
            }
            diseaseListView.addView(noDataText)
        }

        val dialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        closeButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()

        // Set dialog window size and position
        val window = dialog.window
        window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Set dialog dimensions
        val displayMetrics = resources.displayMetrics
        val width = (displayMetrics.widthPixels * 0.85).toInt()
        val height = (displayMetrics.heightPixels * 0.65).toInt()

        window?.setLayout(width, height)
        window?.setGravity(Gravity.CENTER)

        // Add animation
        window?.setWindowAnimations(R.style.DialogAnimation)
    }

    private fun setupWeekSpinner() {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, allWeeks).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        weekSpinner.adapter = adapter

        weekSpinner.setPopupBackgroundResource(android.R.color.transparent)

        weekSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentWeekFilter = allWeeks[position]
                refreshChartData()
                (view as? TextView)?.setTextColor(Color.WHITE)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
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
        loadBarChartData() // Reload bar chart with current municipality and week
        (pieChartPager.adapter as? PieChartPagerAdapter)?.refreshCurrentPage()
    }

    private fun setupPieChartPager() {
        pieChartPager.adapter = PieChartPagerAdapter()
        pieChartPager.orientation = ViewPager2.ORIENTATION_HORIZONTAL

        // Add page transform animation for better visual feedback
        pieChartPager.setPageTransformer { page, position ->
            when {
                position < -1 -> // [-Infinity,-1)
                    page.alpha = 0.1f
                position <= 1 -> { // [-1,1]
                    page.alpha = 1 - 0.3f * abs(position)
                    page.scaleX = 1 - 0.1f * abs(position)
                    page.scaleY = 1 - 0.1f * abs(position)
                }
                else -> // (1,+Infinity]
                    page.alpha = 0.1f
            }
        }

        pieChartPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentMunicipalityIndex = position
                updateMunicipalityIndicator()
                updateNavigationArrows()
                refreshChartData()
            }

            override fun onPageScrollStateChanged(state: Int) {
                // Show/hide swipe hint based on user interaction
                if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                    swipeHint.animate().alpha(0f).setDuration(200).start()
                } else if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    swipeHint.animate().alpha(1f).setDuration(500).start()
                }
            }
        })

        // Set up arrow click listeners
        leftArrow.setOnClickListener {
            if (currentMunicipalityIndex > 0) {
                pieChartPager.currentItem = currentMunicipalityIndex - 1
            }
        }

        rightArrow.setOnClickListener {
            if (currentMunicipalityIndex < municipalities.size - 1) {
                pieChartPager.currentItem = currentMunicipalityIndex + 1
            }
        }

        updateMunicipalityIndicator()
        updateNavigationArrows()

        // Auto-hide swipe hint after 3 seconds
        swipeHint.postDelayed({
            swipeHint.animate().alpha(0.5f).setDuration(500).start()
        }, 3000)
    }

    private fun updateNavigationArrows() {
        // Update left arrow visibility
        if (currentMunicipalityIndex == 0) {
            leftArrow.visibility = View.INVISIBLE
        } else {
            leftArrow.visibility = View.VISIBLE
            leftArrow.alpha = 1f
        }

        // Update right arrow visibility
        if (currentMunicipalityIndex == municipalities.size - 1) {
            rightArrow.visibility = View.INVISIBLE
        } else {
            rightArrow.visibility = View.VISIBLE
            rightArrow.alpha = 1f
        }

        // Add pulse animation to arrows when available
        if (currentMunicipalityIndex < municipalities.size - 1) {
            rightArrow.animate()
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(500)
                .withEndAction {
                    rightArrow.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(500)
                        .start()
                }
                .start()
        }
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

    private fun setupBarChart() {
        barChart.apply {
            setBackgroundColor(Color.TRANSPARENT)
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = false
            setScaleEnabled(false)
            setPinchZoom(false)
            setDrawBarShadow(false)
            setDrawValueAboveBar(true)
            setDrawGridBackground(false)

            // X-axis customization for days
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                setDrawAxisLine(false)
                textColor = Color.WHITE
                textSize = 11f
                granularity = 1f
                setLabelCount(7, false)
                axisMinimum = -0.5f
                axisMaximum = 6.5f
                labelRotationAngle = 0f
                valueFormatter = IndexAxisValueFormatter(dayLabels)
            }

            // Left Y-axis customization
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.parseColor("#333333")
                gridLineWidth = 0.8f
                setDrawAxisLine(false)
                setDrawLabels(false) // Hide Y-axis labels
                axisMinimum = 0f
                granularity = 10f
            }

            // Right Y-axis customization
            axisRight.apply {
                setDrawGridLines(false)
                setDrawAxisLine(false)
                setDrawLabels(false)
                axisMinimum = 0f
            }

            legend.isEnabled = false

            setNoDataText("No data available for selected week")
            setNoDataTextColor(Color.parseColor("#888888"))
            setNoDataTextTypeface(Typeface.DEFAULT_BOLD)

            // Remove extra offsets for cleaner look
            setExtraOffsets(0f, 0f, 0f, 0f)

            // Add click listener for bars
            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    if (e != null && h != null) {
                        val dayIndex = h.x.toInt()
                        val value = e.y.toInt()
                        showDiseasePopup(dayIndex, value)
                    }
                }

                override fun onNothingSelected() {
                    // Do nothing
                }
            })
        }
    }

    private fun loadBarChartData() {
        val currentMunicipality = municipalities[currentMunicipalityIndex]
        val weekNum = currentWeekFilter.replace("Week ", "").toIntOrNull() ?: 1

        db.collection("healthradarDB").document("centralizedData").collection("allCases")
            .get()
            .addOnSuccessListener { snapshot ->
                try {
                    val docs = snapshot.documents.filter {
                        val docMunicipality = it.getString("Municipality") ?: ""
                        docMunicipality.replace("-", "")?.lowercase() ==
                                currentMunicipality.replace("-", "").lowercase()
                    }

                    // Initialize data structures
                    val dayMap = mutableMapOf<Int, Float>()
                    val diseaseDayMap = mutableMapOf<Int, MutableList<DiseaseData>>()

                    // Initialize all days with 0 values
                    for (day in 0..6) {
                        dayMap[day] = 0f
                        diseaseDayMap[day] = mutableListOf()
                    }

                    // Process documents for the selected week
                    for (doc in docs) {
                        val docWeekNum = when (val w = doc.get("Week")) {
                            is Number -> w.toInt()
                            is String -> w.toIntOrNull() ?: -1
                            else -> -1
                        }

                        // Only process documents for the selected week
                        if (docWeekNum == weekNum) {
                            val cases = when (val raw = doc.get("CaseCount")) {
                                is Number -> raw.toFloat()
                                is String -> raw.toFloatOrNull() ?: 0f
                                else -> 0f
                            }

                            val diseaseName = doc.getString("DiseaseName") ?: "Unknown"

                            if (cases > 0f && diseaseName != "Unknown") {
                                // Get day of week from date
                                val dateStr = doc.getString("DateReported") ?: ""
                                val dayOfWeek = getDayOfWeekFromDate(dateStr) ?: 0 // Default to Monday if no date

                                dayMap[dayOfWeek] = (dayMap[dayOfWeek] ?: 0f) + cases

                                // Add disease data for this day
                                val existingDisease = diseaseDayMap[dayOfWeek]?.find { it.name == diseaseName }
                                if (existingDisease != null) {
                                    existingDisease.cases += cases.toInt()
                                } else {
                                    diseaseDayMap[dayOfWeek]?.add(DiseaseData(diseaseName, cases.toInt()))
                                }
                            }
                        }
                    }

                    // Store the current bar chart data for click events
                    currentBarChartData = diseaseDayMap.mapValues { it.value }

                    // Create entries for all 7 days
                    val entries = ArrayList<BarEntry>()
                    for (day in 0..6) {
                        entries.add(BarEntry(day.toFloat(), dayMap[day] ?: 0f))
                    }

                    val dataSet = BarDataSet(entries, "Daily Cases - $currentMunicipality ($currentWeekFilter)").apply {
                        color = Color.parseColor("#5A4CE1") // Single color for all bars
                        valueTextColor = Color.WHITE
                        valueTextSize = 10f
                        setDrawValues(true)
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                return if (value > 0) value.toInt().toString() else ""
                            }
                        }
                    }

                    val data = BarData(dataSet).apply {
                        barWidth = 0.6f
                        setValueTextSize(10f)
                    }

                    // Set the data and refresh
                    barChart.data = data
                    barChart.invalidate()

                    // Animations
                    barChart.animateY(1000, Easing.EaseInOutCubic)

                    // Show message if no data
                    val totalCases = dayMap.values.sum()
                    if (totalCases == 0f) {
                        barChart.clear()
                        barChart.setNoDataText("No cases found for $currentMunicipality in $currentWeekFilter")
                    }

                    Log.d(TAG, "Loaded bar chart data for $currentMunicipality - $currentWeekFilter: $dayMap")

                } catch (ex: Exception) {
                    Log.e(TAG, "Error loading bar chart data", ex)
                    barChart.clear()
                    barChart.setNoDataText("Error loading data for $currentMunicipality")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "loadBarChartData failed", e)
                barChart.clear()
                barChart.setNoDataText("Failed to load data")
            }
    }

    private fun getDayOfWeekFromDate(dateStr: String): Int? {
        return try {
            if (dateStr.isNotEmpty()) {
                val formats = listOf("yyyy-MM-dd", "MM/dd/yyyy", "dd/MM/yyyy", "yyyy/MM/dd")
                for (format in formats) {
                    try {
                        val sdf = SimpleDateFormat(format, Locale.getDefault())
                        val date = sdf.parse(dateStr)
                        if (date != null) {
                            val calendar = Calendar.getInstance()
                            calendar.time = date
                            // Convert to 0-6 where 0=Monday, 6=Sunday
                            val day = calendar.get(Calendar.DAY_OF_WEEK) - 2
                            return if (day < 0) 6 else day
                        }
                    } catch (e: Exception) {
                        continue
                    }
                }
            }
            0 // Default to Monday if no date or parsing fails
        } catch (e: Exception) {
            0 // Default to Monday if any error
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
                text = getString(R.string.no_disease_data)
                textSize = 14f
                setTextColor(Color.GRAY)
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(20), 0, 0)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                contentDescription = getString(R.string.no_disease_data)
            }
            container.addView(emptyText)
            return
        }

        for (item in diseaseList) {
            container.addView(createDiseaseItem(item))
        }

        // Force scrollable behavior - find the parent ScrollView safely
        container.post {
            val scrollView = container.parent as? ScrollView
            scrollView?.isVerticalScrollBarEnabled = true
            scrollView?.scrollTo(0, 0)
            // Set accessibility for the scroll view
            scrollView?.contentDescription = "Scrollable list of diseases for ${municipalities.getOrNull(currentMunicipalityIndex) ?: "current municipality"}"
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
            background = null // Ensure no background initially
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = getString(R.string.disease_item_content_description, item.disease, item.combinedCases)
        }

        val colorView = View(requireContext()).apply {
            setBackgroundColor(item.colorInt)
            layoutParams = LinearLayout.LayoutParams(dpToPx(10), dpToPx(10)).apply {
                marginEnd = dpToPx(10)
            }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        val nameTv = TextView(requireContext()).apply {
            text = item.disease
            textSize = 12f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        val casesTv = TextView(requireContext()).apply {
            text = "${item.combinedCases} cases"
            textSize = 11f
            setTextColor(Color.parseColor("#5A4CE1"))
            setTypeface(null, Typeface.BOLD)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        container.addView(colorView)
        container.addView(nameTv)
        container.addView(casesTv)

        return container
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

    private data class DiseaseData(
        val name: String,
        var cases: Int
    )

    private inner class PieChartPagerAdapter : RecyclerView.Adapter<PieChartPagerAdapter.PieChartVH>() {

        inner class PieChartVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val pieChart: PieChart = itemView.findViewById(R.id.pieChartItem)
            val diseaseContainer: LinearLayout = itemView.findViewById(R.id.diseaseContainer)
            val municipalityTitle: TextView = itemView.findViewById(R.id.municipalityTitle)
            val cardView: CardView = itemView.findViewById(R.id.cardView)
            // Removed problematic scrollView reference
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PieChartVH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pie_chart_card, parent, false)
            return PieChartVH(view)
        }

        override fun onBindViewHolder(holder: PieChartVH, position: Int) {
            val municipality = municipalities[position]

            // Set municipality title for this specific card
            holder.municipalityTitle.text = "$municipality Municipality"
            holder.municipalityTitle.contentDescription = "Health data for $municipality municipality"

            // Set accessibility content for pie chart
            holder.pieChart.contentDescription = "Pie chart showing disease distribution for $municipality"

            // Different background colors for different municipalities
            when (position) {
                0 -> { // Mandaue
                    holder.municipalityTitle.setTextColor(Color.parseColor("#5A4CE1"))
                    holder.cardView.setCardBackgroundColor(Color.parseColor("#2A2A3A"))
                }
                1 -> { // Liloan
                    holder.municipalityTitle.setTextColor(Color.parseColor("#4CAF50"))
                    holder.cardView.setCardBackgroundColor(Color.parseColor("#2A3A2A"))
                }
                2 -> { // Consolacion
                    holder.municipalityTitle.setTextColor(Color.parseColor("#FF9800"))
                    holder.cardView.setCardBackgroundColor(Color.parseColor("#3A2A2A"))
                }
            }

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