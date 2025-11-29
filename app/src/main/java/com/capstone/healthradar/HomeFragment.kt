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
    private lateinit var municipalityIndicator: LinearLayout
    private lateinit var userNameTv: TextView
    private lateinit var weekSpinner: Spinner
    private lateinit var leftArrow: ImageView
    private lateinit var rightArrow: ImageView
    private lateinit var swipeHint: TextView

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val TAG = "HomeFragment"

    // Improved color palette for white background
    private val pieColors = listOf(
        "#FF6B6B", "#4ECDC4", "#45B7D1", "#96CEB4", "#FECA57",
        "#FF9FF3", "#54A0FF", "#5F27CD", "#00D2D3", "#FF9F43",
        "#10AC84", "#EE5A24", "#0984E3", "#A29BFE", "#FD79A8"
    ).map { it.toColorInt() }

    private val municipalities = listOf("Mandaue", "Liloan", "Consolacion")
    private val allWeeks = listOf("Week 1", "Week 2", "Week 3", "Week 4")
    private var currentMunicipalityIndex = 0
    private var currentWeekFilter = "Week 1"

    // Store current disease list for each municipality
    private val currentDiseaseLists = mutableMapOf<Int, List<PagerDiseaseItem>>()
    private var currentDiseaseLabels: List<String> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // Set white background for the entire fragment
        view.setBackgroundColor(Color.WHITE)

        bindViews(view)
        initUi()
        setupPieChartPager()
        loadUserName()
        return view
    }

    private fun bindViews(root: View) {
        barChart = root.findViewById(R.id.barChart)
        pieChartPager = root.findViewById(R.id.pieChartPager)
        municipalityIndicator = root.findViewById(R.id.municipalityIndicator)
        userNameTv = root.findViewById(R.id.userName)
        weekSpinner = root.findViewById(R.id.weekSpinner)

        // New views for improved navigation
        leftArrow = root.findViewById(R.id.leftArrow)
        rightArrow = root.findViewById(R.id.rightArrow)
        swipeHint = root.findViewById(R.id.swipeHint)
    }

    private fun initUi() {
        setupCharts()
        setupMunicipalityIndicator()
        setupWeekSpinner()
        loadBarChartData() // Load initial bar chart data
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
                (view as? TextView)?.setTextColor(Color.parseColor("#333333")) // Dark text
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        (weekSpinner.selectedView as? TextView)?.setTextColor(Color.parseColor("#333333"))
    }

    private fun loadUserName() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userId = currentUser.uid

            // Fetch user data from Firestore to get first and last name
            db.collection("healthradarDB")
                .document("users")
                .collection("user")
                .whereEqualTo("userAuthId", userId)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    if (!querySnapshot.isEmpty) {
                        val document = querySnapshot.documents[0]
                        val firstName = document.getString("firstName") ?: ""
                        val lastName = document.getString("lastName") ?: ""

                        if (firstName.isNotEmpty() && lastName.isNotEmpty()) {
                            userNameTv.text = "Hello, $firstName $lastName"
                        } else if (firstName.isNotEmpty()) {
                            userNameTv.text = "Hello, $firstName"
                        } else if (lastName.isNotEmpty()) {
                            userNameTv.text = "Hello, $lastName"
                        } else {
                            // Fallback to email if no name found
                            val email = currentUser.email
                            val name = email?.substringBefore('@') ?: "User"
                            userNameTv.text = "Hello, $name"
                        }
                    } else {
                        // Fallback if no user document found
                        val email = currentUser.email
                        val name = email?.substringBefore('@') ?: "User"
                        userNameTv.text = "Hello, $name"
                    }

                    // Style the greeting text
                    userNameTv.setTextColor(Color.parseColor("#FFFFFF")) // Dark blue-gray
                    userNameTv.textSize = 18f
                    userNameTv.setTypeface(null, Typeface.BOLD)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error loading user data", e)
                    // Fallback on error
                    val email = currentUser.email
                    val name = email?.substringBefore('@') ?: "User"
                    userNameTv.text = "Hello, $name"
                    userNameTv.setTextColor(Color.parseColor("#FFFFFF"))
                    userNameTv.textSize = 18f
                    userNameTv.setTypeface(null, Typeface.BOLD)
                }
        } else {
            userNameTv.text = "Hello, User"
            userNameTv.setTextColor(Color.parseColor("#FFFFFF"))
            userNameTv.textSize = 18f
            userNameTv.setTypeface(null, Typeface.BOLD)
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
            leftArrow.setColorFilter(Color.parseColor("#5A4CE1")) // Purple color
        }

        // Update right arrow visibility
        if (currentMunicipalityIndex == municipalities.size - 1) {
            rightArrow.visibility = View.INVISIBLE
        } else {
            rightArrow.visibility = View.VISIBLE
            rightArrow.alpha = 1f
            rightArrow.setColorFilter(Color.parseColor("#5A4CE1")) // Purple color
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
                layoutParams = LinearLayout.LayoutParams(dpToPx(12), dpToPx(12)).apply {
                    marginEnd = dpToPx(8)
                }
                setBackgroundColor(if (i == currentMunicipalityIndex) Color.parseColor("#5A4CE1") else Color.parseColor("#E0E0E0"))
                // Add rounded corners
                background = resources.getDrawable(R.drawable.indicator_dot, null)
            }
            municipalityIndicator.addView(dot)
        }
    }

    private fun updateMunicipalityIndicator() {
        for (i in 0 until municipalityIndicator.childCount) {
            val dot = municipalityIndicator.getChildAt(i)
            dot.setBackgroundColor(if (i == currentMunicipalityIndex) Color.parseColor("#5A4CE1") else Color.parseColor("#E0E0E0"))
        }
    }

    private fun setupCharts() {
        setupBarChart()
    }

    private fun setupBarChart() {
        barChart.apply {
            setBackgroundColor(Color.TRANSPARENT)
            description.isEnabled = false
            setTouchEnabled(false) // Disable touch interactions including click
            isDragEnabled = true // Enable dragging for horizontal scrolling
            setScaleEnabled(true)
            setPinchZoom(false)
            setDrawBarShadow(false)
            setDrawValueAboveBar(true)
            setDrawGridBackground(false)

            // X-axis customization for disease names
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                setDrawAxisLine(true)
                axisLineColor = Color.WHITE
                axisLineWidth = 1f
                textColor = Color.WHITE
                textSize = 11f
                granularity = 1f
                setLabelCount(10, false) // Show more labels
                labelRotationAngle = -45f // Rotate labels for better readability
                setCenterAxisLabels(false)
            }

            // Left Y-axis customization
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.parseColor("#333333")
                gridLineWidth = 0.8f
                setDrawAxisLine(true)
                axisLineColor = Color.WHITE
                axisLineWidth = 1f
                setDrawLabels(true)
                textColor = Color.WHITE
                textSize = 10f
                axisMinimum = 0f
                granularity = 1f
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
            setNoDataTextColor(Color.WHITE)
            setNoDataTextTypeface(Typeface.DEFAULT_BOLD)

            // Add extra right offset to accommodate scrolling
            setExtraOffsets(0f, 0f, 20f, 0f)

            // Remove click listener for bars
            setOnChartValueSelectedListener(null)
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

                    // Group by disease name
                    val diseaseMap = mutableMapOf<String, Float>()

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
                                // Add to disease totals (no day breakdown needed)
                                diseaseMap[diseaseName] = (diseaseMap[diseaseName] ?: 0f) + cases
                            }
                        }
                    }

                    // Sort diseases by count (descending)
                    val sortedDiseases = diseaseMap.entries.sortedByDescending { it.value }

                    // Create entries and labels
                    val entries = ArrayList<BarEntry>()
                    val diseaseLabels = ArrayList<String>()

                    for ((index, entry) in sortedDiseases.withIndex()) {
                        entries.add(BarEntry(index.toFloat(), entry.value))
                        diseaseLabels.add(entry.key)
                    }

                    // Store current disease labels
                    currentDiseaseLabels = diseaseLabels

                    // Update X-axis with disease names
                    barChart.xAxis.valueFormatter = IndexAxisValueFormatter(diseaseLabels)
                    barChart.xAxis.labelCount = diseaseLabels.size

                    val dataSet = BarDataSet(entries, "Disease Cases - $currentMunicipality ($currentWeekFilter)").apply {
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

                    val data = BarData(dataSet).apply {
                        barWidth = 0.4f
                        setValueTextSize(10f)
                    }

                    // Set the data and refresh
                    barChart.data = data

                    // Adjust X-axis range based on number of diseases
                    barChart.xAxis.axisMinimum = -0.5f
                    barChart.xAxis.axisMaximum = (diseaseLabels.size - 0.5f).coerceAtLeast(0.5f)

                    barChart.invalidate()

                    // Animations
                    barChart.animateY(1000, Easing.EaseInOutCubic)

                    // Show message if no data
                    val totalCases = diseaseMap.values.sum()
                    if (totalCases == 0f) {
                        barChart.clear()
                        barChart.setNoDataText("No cases found for $currentMunicipality in $currentWeekFilter")
                    }

                    Log.d(TAG, "Loaded bar chart data for $currentMunicipality - $currentWeekFilter: ${diseaseMap.size} diseases")

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
            setNoDataTextColor(Color.BLACK) // Changed to black
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
            selectedView?.setBackgroundColor(Color.parseColor("#F0F4FF")) // Light blue highlight

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
                setTextColor(Color.parseColor("#888888"))
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
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            background = null // Ensure no background initially
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = getString(R.string.disease_item_content_description, item.disease, item.combinedCases)
        }

        val colorView = View(requireContext()).apply {
            setBackgroundColor(item.colorInt)
            layoutParams = LinearLayout.LayoutParams(dpToPx(12), dpToPx(12)).apply {
                marginEnd = dpToPx(12)
            }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            // Add rounded corners
            background = resources.getDrawable(R.drawable.indicator_dot, null)
        }

        val nameTv = TextView(requireContext()).apply {
            text = item.disease
            textSize = 13f
            setTextColor(Color.BLACK) // Changed to black
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        val casesTv = TextView(requireContext()).apply {
            text = "${item.combinedCases} cases"
            textSize = 12f
            setTextColor(Color.parseColor("#5A4CE1")) // Primary purple
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

            // Set municipality title for this specific card - RESTORE ORIGINAL COLORS
            holder.municipalityTitle.text = "$municipality Municipality"
            holder.municipalityTitle.contentDescription = "Health data for $municipality municipality"

            // Restore original municipality title colors
            when (position) {
                0 -> { // Mandaue - Blue theme
                    holder.municipalityTitle.setTextColor(Color.parseColor("#2C5AA0"))
                    holder.cardView.setCardBackgroundColor(Color.parseColor("#F0F7FF"))
                }
                1 -> { // Liloan - Green theme
                    holder.municipalityTitle.setTextColor(Color.parseColor("#2E7D32"))
                    holder.cardView.setCardBackgroundColor(Color.parseColor("#F1F8E9"))
                }
                2 -> { // Consolacion - Orange theme
                    holder.municipalityTitle.setTextColor(Color.parseColor("#EF6C00"))
                    holder.cardView.setCardBackgroundColor(Color.parseColor("#FFF3E0"))
                }
            }

            // Set card elevation and radius
            holder.cardView.cardElevation = dpToPx(2).toFloat()
            holder.cardView.radius = dpToPx(12).toFloat()

            // Set accessibility content for pie chart
            holder.pieChart.contentDescription = "Pie chart showing disease distribution for $municipality"

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