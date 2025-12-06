package com.capstone.healthradar

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
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
    private lateinit var monthYearTextView: TextView
    private lateinit var mainNestedScrollView: NestedScrollView

    // New pagination views
    private lateinit var diseaseListViewPager: ViewPager2
    private lateinit var diseasePageTitle: TextView
    private lateinit var diseaseLeftArrow: ImageView
    private lateinit var diseaseRightArrow: ImageView
    private lateinit var diseasePageIndicator: LinearLayout

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val TAG = "HomeFragment"

    // Color palette for pie chart and disease breakdown
    private val pieColors = listOf(
        "#FF6B6B", "#4ECDC4", "#45B7D1", "#96CEB4", "#FECA57",
        "#FF9FF3", "#54A0FF", "#5F27CD", "#00D2D3", "#FF9F43",
        "#10AC84", "#EE5A24", "#0984E3", "#A29BFE", "#FD79A8"
    ).map { it.toColorInt() }

    private val municipalities = listOf("Mandaue", "Liloan", "Consolacion")
    private var currentMunicipalityIndex = 0
    private var currentWeekFilter = "Week 1"
    private var currentMonthYear = ""

    // Store disease data with colors for each municipality
    private val municipalityDiseaseData = mutableMapOf<Int, List<DiseaseItem>>()
    // Store disease container views for each municipality
    private val diseaseContainerMap = mutableMapOf<Int, LinearLayout>()
    // Store scroll views for each municipality
    private val diseaseScrollViewMap = mutableMapOf<Int, ScrollView>()

    // Store disease data for bar chart
    private var barChartDiseases = mutableListOf<String>()
    private var barChartColors = mutableMapOf<String, Int>()

    // Pagination variables
    private var paginatedDiseases = mutableListOf<List<DiseaseItemPage>>()
    private var diseasesPerPage = 5
    private var currentDiseasePage = 0
    private var selectedDisease: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        bindViews(view)
        initUi()
        setupViewPagers()
        loadUserName()
        return view
    }

    private fun bindViews(root: View) {
        barChart = root.findViewById(R.id.barChart)
        pieChartPager = root.findViewById(R.id.pieChartPager)
        municipalityIndicator = root.findViewById(R.id.municipalityIndicator)
        userNameTv = root.findViewById(R.id.userName)
        weekSpinner = root.findViewById(R.id.weekSpinner)
        leftArrow = root.findViewById(R.id.leftArrow)
        rightArrow = root.findViewById(R.id.rightArrow)
        swipeHint = root.findViewById(R.id.swipeHint)
        monthYearTextView = root.findViewById(R.id.monthYearTextView)
        mainNestedScrollView = root.findViewById(R.id.mainNestedScrollView)

        // New pagination views
        diseaseListViewPager = root.findViewById(R.id.diseaseListViewPager)
        diseasePageTitle = root.findViewById(R.id.diseasePageTitle)
        diseaseLeftArrow = root.findViewById(R.id.diseaseLeftArrow)
        diseaseRightArrow = root.findViewById(R.id.diseaseRightArrow)
        diseasePageIndicator = root.findViewById(R.id.diseasePageIndicator)
    }

    private fun initUi() {
        setupCharts()
        setupMunicipalityIndicator()
        setupWeekSpinner()
        updateMonthYearDisplay()
        loadBarChartData()

        // Update all text and icon colors for theme compatibility
        updateThemeColors()

        // Set greeting text size to match XML
        userNameTv.textSize = 24f
    }

    private fun updateThemeColors() {
        // Update all text colors
        userNameTv.setTextColor(getPrimaryTextColor())
        monthYearTextView.setTextColor(getSecondaryTextColor())
        diseasePageTitle.setTextColor(getPrimaryTextColor())
        swipeHint.setTextColor(getSecondaryTextColor())

        // Update arrow icon colors
        updateArrowColors()
    }

    private fun updateArrowColors() {
        val arrowColor = getPrimaryColor()

        // Programmatically set tint for all arrow icons
        leftArrow.setColorFilter(arrowColor, android.graphics.PorterDuff.Mode.SRC_IN)
        rightArrow.setColorFilter(arrowColor, android.graphics.PorterDuff.Mode.SRC_IN)
        diseaseLeftArrow.setColorFilter(arrowColor, android.graphics.PorterDuff.Mode.SRC_IN)
        diseaseRightArrow.setColorFilter(arrowColor, android.graphics.PorterDuff.Mode.SRC_IN)
    }

    // Helper function to get colors dynamically
    private fun getPrimaryTextColor(): Int {
        return if (isDarkMode()) {
            Color.WHITE  // White text in dark mode
        } else {
            Color.BLACK  // Black text in light mode
        }
    }

    private fun getSecondaryTextColor(): Int {
        return if (isDarkMode()) {
            Color.parseColor("#CCCCCC")  // Light gray in dark mode
        } else {
            Color.parseColor("#666666")  // Dark gray in light mode
        }
    }

    private fun getPrimaryColor(): Int {
        // Your blue color (#6366F1)
        return Color.parseColor("#6366F1")
    }

    private fun getCardBackgroundColor(): Int {
        return if (isDarkMode()) {
            Color.parseColor("#1E1E1E") // Dark gray for dark mode
        } else {
            Color.WHITE // White for light mode
        }
    }

    private fun getSurfaceColor(): Int {
        return if (isDarkMode()) {
            Color.parseColor("#2D2D2D") // Dark gray for dark mode
        } else {
            Color.WHITE // White for light mode
        }
    }

    private fun getHighlightColor(): Int {
        return if (isDarkMode()) {
            Color.parseColor("#4A4A4A") // Darker gray for dark mode highlight
        } else {
            Color.parseColor("#F0F0F0") // Light gray for light mode highlight
        }
    }

    private fun isDarkMode(): Boolean {
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun setupWeekSpinner() {
        val currentWeek = getCurrentWeekOfMonth()
        val weekOptions = mutableListOf<String>()
        for (week in 1..4) {
            if (week <= currentWeek) {
                weekOptions.add("Week $week")
            }
        }

        val latestWeek = if (currentWeek > 4) 4 else currentWeek
        currentWeekFilter = "Week $latestWeek"

        val adapter = object : ArrayAdapter<String>(
            requireContext(),
            android.R.layout.simple_spinner_item,
            weekOptions
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                // Use theme-aware color
                textView.setTextColor(getPrimaryTextColor())
                textView.textSize = 14f
                textView.setTypeface(null, Typeface.BOLD)
                textView.setBackgroundColor(Color.TRANSPARENT)

                val itemText = getItem(position) ?: ""
                val weekNum = itemText.replace("Week ", "").toIntOrNull()
                val isCurrentWeek = weekNum == currentWeek

                textView.text = if (isCurrentWeek) {
                    "$itemText (Current) ▼"
                } else {
                    "$itemText ▼"
                }

                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                // Use theme-aware color
                textView.setTextColor(getPrimaryTextColor())
                textView.textSize = 14f
                textView.setTypeface(null, Typeface.NORMAL)
                textView.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
                textView.setBackgroundColor(getSurfaceColor())

                val itemText = getItem(position) ?: ""
                val weekNum = itemText.replace("Week ", "").toIntOrNull()
                val isCurrentWeek = weekNum == currentWeek

                textView.text = if (isCurrentWeek) {
                    "$itemText (Current)"
                } else {
                    itemText
                }

                return view
            }
        }

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        weekSpinner.adapter = adapter
        weekSpinner.setBackgroundColor(Color.TRANSPARENT)

        weekSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val items = weekOptions
                if (position < items.size) {
                    val selectedText = items[position]
                    currentWeekFilter = selectedText.replace(" (Current)", "")
                    refreshChartData()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        weekSpinner.post {
            val latestWeekIndex = weekOptions.indexOfFirst {
                it.replace("Week ", "").toIntOrNull() == latestWeek
            }
            if (latestWeekIndex >= 0) {
                weekSpinner.setSelection(latestWeekIndex)
            }
        }
    }

    private fun refreshChartData() {
        loadBarChartData()
        (pieChartPager.adapter as? PieChartPagerAdapter)?.refreshCurrentPage()
    }

    private fun setupViewPagers() {
        setupPieChartPager()
        setupDiseaseListPager()
    }

    private fun setupDiseaseListPager() {
        diseaseListViewPager.adapter = DiseaseListPagerAdapter()
        diseaseListViewPager.orientation = ViewPager2.ORIENTATION_HORIZONTAL

        // Set fixed height for the ViewPager2
        diseaseListViewPager.layoutParams.height = dpToPx(250)

        // Disable nested scrolling
        diseaseListViewPager.getChildAt(0)?.let { recyclerView ->
            if (recyclerView is RecyclerView) {
                recyclerView.isNestedScrollingEnabled = false
                // Set layout params for RecyclerView inside ViewPager2
                recyclerView.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        }

        diseaseListViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentDiseasePage = position
                updateDiseasePageUI(position)
            }
        })

        diseaseLeftArrow.setOnClickListener {
            if (diseaseListViewPager.currentItem > 0) {
                diseaseListViewPager.currentItem = diseaseListViewPager.currentItem - 1
            }
        }

        diseaseRightArrow.setOnClickListener {
            if (diseaseListViewPager.currentItem < paginatedDiseases.size - 1) {
                diseaseListViewPager.currentItem = diseaseListViewPager.currentItem + 1
            }
        }
    }

    private fun updateDiseasePageUI(position: Int) {
        val totalPages = paginatedDiseases.size
        if (totalPages > 0) {
            diseasePageTitle.text = "Disease List - Page ${position + 1} of $totalPages"
            // Update text color for theme
            diseasePageTitle.setTextColor(getPrimaryTextColor())
        } else {
            diseasePageTitle.text = "No disease data available"
            diseasePageTitle.setTextColor(getSecondaryTextColor())
        }

        diseaseLeftArrow.visibility = if (position == 0) View.INVISIBLE else View.VISIBLE
        diseaseRightArrow.visibility = if (position == totalPages - 1) View.INVISIBLE else View.VISIBLE

        updateDiseasePageIndicator(position)
    }

    private fun updateDiseasePageIndicator(position: Int) {
        diseasePageIndicator.removeAllViews()
        val totalPages = paginatedDiseases.size

        for (i in 0 until totalPages) {
            val dot = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(8), dpToPx(8)).apply {
                    marginEnd = dpToPx(4)
                }
                setBackgroundColor(if (i == position) getPrimaryColor() else Color.parseColor("#E0E0E0"))
            }
            diseasePageIndicator.addView(dot)
        }
    }

    private fun setupPieChartPager() {
        pieChartPager.adapter = PieChartPagerAdapter()
        pieChartPager.orientation = ViewPager2.ORIENTATION_HORIZONTAL

        // Prevent ViewPager2 from intercepting vertical scrolls
        pieChartPager.isUserInputEnabled = true

        // Disable nested scrolling in the ViewPager2's RecyclerView to prevent conflicts
        pieChartPager.getChildAt(0)?.let { recyclerView ->
            if (recyclerView is RecyclerView) {
                recyclerView.isNestedScrollingEnabled = false
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
                super.onPageScrollStateChanged(state)
                // Enable/disable main scroll when ViewPager is being scrolled
                mainNestedScrollView.isNestedScrollingEnabled = state == ViewPager2.SCROLL_STATE_IDLE
            }
        })

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
    }

    private fun updateNavigationArrows() {
        leftArrow.visibility = if (currentMunicipalityIndex == 0) View.INVISIBLE else View.VISIBLE
        rightArrow.visibility = if (currentMunicipalityIndex == municipalities.size - 1) View.INVISIBLE else View.VISIBLE
    }

    private fun setupMunicipalityIndicator() {
        municipalityIndicator.removeAllViews()
        for (i in municipalities.indices) {
            val dot = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(10), dpToPx(10)).apply {
                    marginEnd = dpToPx(6)
                }
                setBackgroundColor(if (i == currentMunicipalityIndex) getPrimaryColor() else Color.parseColor("#E0E0E0"))
            }
            municipalityIndicator.addView(dot)
        }
    }

    private fun updateMunicipalityIndicator() {
        for (i in 0 until municipalityIndicator.childCount) {
            val dot = municipalityIndicator.getChildAt(i)
            dot.setBackgroundColor(if (i == currentMunicipalityIndex) getPrimaryColor() else Color.parseColor("#E0E0E0"))
        }
    }

    private fun updateMonthYearDisplay() {
        val calendar = Calendar.getInstance()
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        currentMonthYear = monthFormat.format(calendar.time)
        val currentWeek = getCurrentWeekOfMonth()
        monthYearTextView.text = "$currentMonthYear • Week $currentWeek"
        // Use theme-aware color
        monthYearTextView.setTextColor(getSecondaryTextColor())
        monthYearTextView.textSize = 16f
        monthYearTextView.setTypeface(null, Typeface.BOLD)
    }

    private fun loadUserName() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userId = currentUser.uid
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
                        userNameTv.text = if (firstName.isNotEmpty() && lastName.isNotEmpty()) {
                            "Hello, $firstName $lastName"
                        } else if (firstName.isNotEmpty()) {
                            "Hello, $firstName"
                        } else {
                            "Hello, ${currentUser.email?.substringBefore('@') ?: "User"}"
                        }
                    } else {
                        userNameTv.text = "Hello, ${currentUser.email?.substringBefore('@') ?: "User"}"
                    }
                    // FIXED: Use theme-aware color
                    userNameTv.setTextColor(getPrimaryTextColor())
                    userNameTv.textSize = 24f
                    userNameTv.setTypeface(null, Typeface.BOLD)
                }
        } else {
            userNameTv.text = "Hello, User"
            // FIXED: Use theme-aware color
            userNameTv.setTextColor(getPrimaryTextColor())
            userNameTv.textSize = 24f
            userNameTv.setTypeface(null, Typeface.BOLD)
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
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(false)
            setDrawBarShadow(false)
            setDrawValueAboveBar(true)
            setDrawGridBackground(false)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                setDrawAxisLine(true)
                axisLineColor = getPrimaryTextColor()
                axisLineWidth = 1f
                textColor = Color.TRANSPARENT // HIDE TEXT on X-axis
                textSize = 0f // Make text invisible
                granularity = 1f
                setLabelCount(5, true)
                labelRotationAngle = 0f
                setCenterAxisLabels(false)
                setAvoidFirstLastClipping(true)
                isGranularityEnabled = true
                // Remove value formatter to hide labels
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return "" // Empty string for all values
                    }
                }
            }

            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = if (isDarkMode()) Color.parseColor("#333333") else Color.parseColor("#E0E0E0")
                gridLineWidth = 0.8f
                setDrawAxisLine(true)
                axisLineColor = getPrimaryTextColor()
                axisLineWidth = 1f
                setDrawLabels(true)
                textColor = getPrimaryTextColor()
                textSize = 10f
                axisMinimum = 0f
                granularity = 1f
                isGranularityEnabled = true
            }

            axisRight.apply {
                setDrawGridLines(false)
                setDrawAxisLine(false)
                setDrawLabels(false)
                axisMinimum = 0f
            }

            legend.isEnabled = false

            // NEW: Set custom no data text
            setNoDataText("No Disease Data Available")
            setNoDataTextColor(getSecondaryTextColor())
            setNoDataTextTypeface(Typeface.DEFAULT_BOLD)

            // Enable click listener for bar selection
            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    if (e != null && h != null) {
                        val index = h.x.toInt()
                        if (index < barChartDiseases.size) {
                            val diseaseName = barChartDiseases[index]
                            highlightDiseaseInBarChartList(diseaseName)
                        }
                    }
                }

                override fun onNothingSelected() {
                    removeHighlightFromBarChartList()
                }
            })

            // Remove extra offset since no labels
            extraBottomOffset = 10f
        }
    }

    private fun loadBarChartData() {
        val currentMunicipality = municipalities[currentMunicipalityIndex]
        val weekNum = currentWeekFilter.replace("Week ", "").toIntOrNull() ?: 1

        db.collection("healthradarDB").document("centralizedData").collection("allCases")
            .whereEqualTo("Municipality", currentMunicipality)
            .whereEqualTo("Week", weekNum)
            .get()
            .addOnSuccessListener { snapshot ->
                try {
                    // NEW: Check if there are ANY documents at all
                    if (snapshot.isEmpty) {
                        showNoDataInBarChart()
                        showNoDiseaseData()
                        return@addOnSuccessListener
                    }

                    val diseaseMap = mutableMapOf<String, Float>()
                    val diseaseCaseMap = mutableMapOf<String, Int>() // Store case counts
                    val diseaseColorMap = mutableMapOf<String, Int>()

                    // Track all diseases found
                    val allDiseases = mutableSetOf<String>()

                    for (doc in snapshot.documents) {
                        val dateStr = doc.getString("DateReported") ?: doc.getString("uploadedAt")
                        if (dateStr != null && isDateFromCurrentMonth(dateStr)) {
                            val cases = when (val raw = doc.get("CaseCount")) {
                                is Number -> raw.toFloat()
                                is String -> raw.toFloatOrNull() ?: 0f
                                else -> 0f
                            }
                            val intCases = cases.toInt()
                            val diseaseName = doc.getString("DiseaseName")?.trim() ?: "Unknown"

                            if (cases > 0f && diseaseName.isNotBlank() && diseaseName != "Unknown") {
                                // Sum up cases for each disease
                                diseaseMap[diseaseName] = (diseaseMap[diseaseName] ?: 0f) + cases
                                diseaseCaseMap[diseaseName] = (diseaseCaseMap[diseaseName] ?: 0) + intCases
                                allDiseases.add(diseaseName)
                            }
                        }
                    }

                    // NEW: Check if diseaseMap is empty AFTER processing
                    if (diseaseMap.isEmpty()) {
                        showNoDataInBarChart()
                        showNoDiseaseData()
                        return@addOnSuccessListener
                    }

                    // Sort diseases by case count (highest first)
                    val sortedDiseases = diseaseMap.entries.sortedByDescending { it.value }
                    val entries = ArrayList<BarEntry>()
                    barChartDiseases.clear()
                    barChartColors.clear()

                    // Create entries and assign colors (NO disease names for X-axis)
                    for ((index, entry) in sortedDiseases.withIndex()) {
                        entries.add(BarEntry(index.toFloat(), entry.value))
                        barChartDiseases.add(entry.key)

                        // Generate a consistent color for each disease based on its name
                        val color = generateColorForDisease(entry.key)
                        barChartColors[entry.key] = color

                        // Store the color mapping for reference
                        diseaseColorMap[entry.key] = color
                    }

                    // Adjust rotation not needed since no labels
                    barChart.xAxis.labelRotationAngle = 0f
                    barChart.extraBottomOffset = 10f

                    val dataSet = BarDataSet(entries, "").apply {
                        colors = barChartDiseases.map { barChartColors[it] ?: getPrimaryColor() }
                        valueTextColor = getPrimaryTextColor()
                        valueTextSize = 10f
                        setDrawValues(true)
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                return if (value > 0) value.toInt().toString() else ""
                            }
                        }

                        // Add some visual styling
                        barBorderColor = if (isDarkMode()) Color.parseColor("#444444") else Color.parseColor("#DDDDDD")
                        barBorderWidth = 0.5f
                    }

                    val data = BarData(dataSet).apply {
                        barWidth = 0.6f
                        setValueTextSize(10f)
                    }

                    barChart.data = data
                    barChart.invalidate()
                    barChart.animateY(1000, Easing.EaseInOutCubic)

                    // Update disease list with pagination
                    updateDiseaseListForBarChartWithCases(diseaseColorMap, diseaseCaseMap)

                } catch (ex: Exception) {
                    Log.e(TAG, "Error loading bar chart data", ex)
                    showNoDataInBarChart()
                    showNoDiseaseData()
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error loading bar chart data", exception)
                showNoDataInBarChart()
                showNoDiseaseData()
            }
    }

    /**
     * Show "No Disease Data Available" message in the bar chart
     */
    private fun showNoDataInBarChart() {
        // Clear any existing data
        barChart.clear()

        // Set custom no data text
        barChart.setNoDataText("No Disease Data Available")
        barChart.setNoDataTextColor(getSecondaryTextColor())
        barChart.setNoDataTextTypeface(Typeface.DEFAULT_BOLD)

        // Clear the disease lists
        barChartDiseases.clear()
        barChartColors.clear()

        // Refresh the chart to show the "No Data Available" message
        barChart.invalidate()
    }

    /**
     * Generate a consistent color for a disease based on its name
     * This ensures the same disease always gets the same color
     */
    private fun generateColorForDisease(diseaseName: String): Int {
        // Hash the disease name to get a consistent index
        val hash = abs(diseaseName.hashCode())
        val colorIndex = hash % pieColors.size
        return pieColors[colorIndex]
    }

    /**
     * Update the disease list below the bar chart WITH case counts - Paginated
     */
    private fun updateDiseaseListForBarChartWithCases(
        diseaseColorMap: Map<String, Int>,
        diseaseCaseMap: Map<String, Int>
    ) {
        if (diseaseColorMap.isEmpty() || diseaseCaseMap.isEmpty()) {
            showNoDiseaseData()
            return
        }

        // Sort diseases by case count (highest first)
        val sortedDiseases = diseaseCaseMap.entries.sortedByDescending { it.value }

        // Convert to DiseaseItemPage objects
        val diseaseItems = sortedDiseases.map { entry ->
            val diseaseName = entry.key
            val caseCount = entry.value
            val color = diseaseColorMap[diseaseName] ?: generateColorForDisease(diseaseName)
            DiseaseItemPage(diseaseName, caseCount, color)
        }

        // Paginate the diseases
        paginatedDiseases = diseaseItems.chunked(diseasesPerPage).toMutableList()

        // Update the ViewPager2 adapter
        (diseaseListViewPager.adapter as? DiseaseListPagerAdapter)?.updateData(paginatedDiseases)

        // Update UI
        updateDiseasePageUI(0)

        // Reset to first page
        diseaseListViewPager.currentItem = 0

        // Clear selection when new data loads
        selectedDisease = null
    }

    private fun showNoDiseaseData() {
        paginatedDiseases.clear()
        (diseaseListViewPager.adapter as? DiseaseListPagerAdapter)?.updateData(emptyList())
        diseasePageTitle.text = "No disease data available"
        diseasePageTitle.setTextColor(getSecondaryTextColor())
        diseaseLeftArrow.visibility = View.INVISIBLE
        diseaseRightArrow.visibility = View.INVISIBLE
        diseasePageIndicator.removeAllViews()
        selectedDisease = null
    }

    /**
     * Highlight a disease in the bar chart list
     */
    private fun highlightDiseaseInBarChartList(diseaseName: String) {
        removeHighlightFromBarChartList()
        selectedDisease = diseaseName

        // Find which page contains this disease
        for ((pageIndex, page) in paginatedDiseases.withIndex()) {
            val diseaseIndex = page.indexOfFirst { it.diseaseName == diseaseName }
            if (diseaseIndex != -1) {
                // Navigate to the correct page
                diseaseListViewPager.currentItem = pageIndex

                // Also select the corresponding bar in the chart
                val barIndex = barChartDiseases.indexOf(diseaseName)
                if (barIndex >= 0) {
                    barChart.highlightValue(barIndex.toFloat(), 0, false)
                }

                // Update the adapter to show highlight
                (diseaseListViewPager.adapter as? DiseaseListPagerAdapter)?.updateSelectedDisease(diseaseName)
                break
            }
        }
    }

    /**
     * Remove highlight from all items in bar chart list
     */
    private fun removeHighlightFromBarChartList() {
        selectedDisease = null
        (diseaseListViewPager.adapter as? DiseaseListPagerAdapter)?.updateSelectedDisease(null)
    }

    private fun isDateFromCurrentMonth(dateString: String?): Boolean {
        if (dateString.isNullOrEmpty()) return false
        return try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            dateFormat.timeZone = TimeZone.getTimeZone("UTC")
            val date = dateFormat.parse(dateString) ?: return false

            val calendar = Calendar.getInstance()
            val currentMonth = calendar.get(Calendar.MONTH)
            val currentYear = calendar.get(Calendar.YEAR)

            calendar.time = date
            val docMonth = calendar.get(Calendar.MONTH)
            val docYear = calendar.get(Calendar.YEAR)

            docMonth == currentMonth && docYear == currentYear
        } catch (e: Exception) {
            false
        }
    }

    private fun getCurrentWeekOfMonth(): Int {
        val calendar = Calendar.getInstance()
        val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
        return (dayOfMonth - 1) / 7 + 1
    }

    private fun setupPieChart(pieChart: PieChart, diseaseContainer: LinearLayout, scrollView: ScrollView, position: Int) {
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
            // FIXED: Changed from "Loading disease data..." to "No disease data available"
            setNoDataText("No disease data available")
            setNoDataTextColor(getSecondaryTextColor())
            setDrawSliceText(false)

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    if (e != null && h != null) {
                        val sliceIndex = h.x.toInt()
                        Log.d(TAG, "Pie slice $sliceIndex selected")
                        highlightDiseaseInList(sliceIndex, position)
                    }
                }

                override fun onNothingSelected() {
                    removeHighlightFromDiseaseList(position)
                }
            })
        }

        // Store the disease container and scroll view for this municipality
        diseaseContainerMap[position] = diseaseContainer
        diseaseScrollViewMap[position] = scrollView
        Log.d(TAG, "Stored container and scroll view for position $position")
    }

    private fun highlightDiseaseInList(sliceIndex: Int, municipalityPosition: Int) {
        val diseaseList = municipalityDiseaseData[municipalityPosition] ?: return
        val diseaseContainer = diseaseContainerMap[municipalityPosition] ?: return
        val scrollView = diseaseScrollViewMap[municipalityPosition]

        if (sliceIndex < diseaseList.size) {
            removeHighlightFromDiseaseList(municipalityPosition)

            val selectedDisease = diseaseList[sliceIndex]
            Log.d(TAG, "Looking for disease: ${selectedDisease.disease}")

            // Find and highlight the disease item
            for (i in 0 until diseaseContainer.childCount) {
                val view = diseaseContainer.getChildAt(i)
                if (view is LinearLayout && view.tag?.toString()?.contains("disease_item_") == true) {
                    // Check if this view corresponds to the selected disease
                    if (view.getChildAt(1) is TextView) {
                        val diseaseTextView = view.getChildAt(1) as TextView
                        if (diseaseTextView.text.toString() == selectedDisease.disease) {
                            Log.d(TAG, "Found disease item at position $i")
                            // Highlight the item
                            view.setBackgroundColor(getHighlightColor())

                            // Scroll to show the disease item
                            scrollView?.post {
                                // Calculate position to scroll to
                                val top = view.top
                                val scrollViewHeight = scrollView.height
                                val viewHeight = view.height

                                // Calculate target scroll position to center the item
                                val targetScroll = top - (scrollViewHeight / 2) + (viewHeight / 2)

                                // Scroll to position
                                scrollView.smoothScrollTo(0, targetScroll.coerceAtLeast(0))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun removeHighlightFromDiseaseList(municipalityPosition: Int) {
        val diseaseContainer = diseaseContainerMap[municipalityPosition] ?: return

        for (i in 0 until diseaseContainer.childCount) {
            val view = diseaseContainer.getChildAt(i)
            if (view is LinearLayout && view.tag?.toString()?.contains("disease_item_") == true) {
                view.setBackgroundColor(Color.TRANSPARENT)
            }
        }
    }

    private fun loadPieChartData(municipality: String, pieChart: PieChart, diseaseContainer: LinearLayout, position: Int) {
        val weekNum = currentWeekFilter.replace("Week ", "").toIntOrNull() ?: 1

        Log.d(TAG, "Loading pie chart data for $municipality, Week $weekNum")

        db.collection("healthradarDB").document("centralizedData").collection("allCases")
            .whereEqualTo("Municipality", municipality)
            .whereEqualTo("Week", weekNum)
            .get()
            .addOnSuccessListener { snapshot ->
                try {
                    Log.d(TAG, "Got ${snapshot.documents.size} documents for $municipality")

                    val diseaseTotals = mutableMapOf<String, Float>()
                    var hasData = false

                    for (doc in snapshot.documents) {
                        val dateStr = doc.getString("DateReported") ?: doc.getString("uploadedAt")
                        if (dateStr != null && isDateFromCurrentMonth(dateStr)) {
                            val disease = doc.getString("DiseaseName") ?: "Unknown"
                            if (disease.isNotBlank() && disease != "Unknown") {
                                val cases = when (val raw = doc.get("CaseCount")) {
                                    is Number -> raw.toFloat()
                                    is String -> raw.toFloatOrNull() ?: 0f
                                    else -> 0f
                                }
                                if (cases > 0f) {
                                    diseaseTotals[disease] = (diseaseTotals[disease] ?: 0f) + cases
                                    hasData = true
                                    Log.d(TAG, "Found disease: $disease with $cases cases")
                                }
                            }
                        }
                    }

                    // Check if we have any data
                    if (!hasData) {
                        Log.d(TAG, "No data found for $municipality, Week $weekNum")
                        pieChart.clear()
                        updateDiseaseList(emptyList(), diseaseContainer)
                        municipalityDiseaseData[position] = emptyList()
                        return@addOnSuccessListener
                    }

                    Log.d(TAG, "Total diseases found: ${diseaseTotals.size}")

                    val sorted = diseaseTotals.entries.sortedByDescending { it.value }
                    val total = sorted.sumOf { it.value.toDouble() }.toFloat()

                    Log.d(TAG, "Total cases: $total, Sorted diseases: ${sorted.size}")

                    val entries = ArrayList<PieEntry>()
                    val diseaseList = mutableListOf<DiseaseItem>()

                    // Create pie chart entries and disease list items with matching colors
                    for ((index, entry) in sorted.withIndex()) {
                        val disease = entry.key
                        val totalCases = entry.value
                        entries.add(PieEntry(totalCases, disease))

                        // Calculate percentage
                        val percent = if (total > 0f) (totalCases / total * 100f) else 0f
                        val percentInt = percent.roundToInt().coerceAtLeast(0)

                        // Assign color from palette - same color for pie slice and disease item
                        val color = pieColors[index % pieColors.size]

                        diseaseList.add(DiseaseItem(disease, totalCases.roundToInt(), percentInt, color))

                        Log.d(TAG, "Adding to list: $disease - $totalCases cases ($percentInt%)")
                    }

                    // Store disease data for this municipality
                    municipalityDiseaseData[position] = diseaseList
                    Log.d(TAG, "Stored ${diseaseList.size} diseases for position $position")

                    // Create pie chart dataset with colors
                    val ds = PieDataSet(entries, "").apply {
                        colors = diseaseList.map { it.color }
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
                    Log.d(TAG, "Disease list updated for $municipality")

                } catch (ex: Exception) {
                    Log.e(TAG, "Error building pie chart", ex)
                    pieChart.clear()
                    updateDiseaseList(emptyList(), diseaseContainer)
                    municipalityDiseaseData[position] = emptyList()
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error loading pie chart data", exception)
                pieChart.clear()
                updateDiseaseList(emptyList(), diseaseContainer)
                municipalityDiseaseData[position] = emptyList()
            }
    }

    private fun updateDiseaseList(diseaseList: List<DiseaseItem>, container: LinearLayout) {
        container.removeAllViews()

        if (diseaseList.isEmpty()) {
            val emptyText = TextView(requireContext()).apply {
                text = "No disease data available"
                textSize = 14f
                setTextColor(getSecondaryTextColor())
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(20), 0, 0)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                contentDescription = "No disease data available"
            }
            container.addView(emptyText)
            return
        }

        Log.d(TAG, "Updating disease list with ${diseaseList.size} items")

        // Add disease items
        for ((index, item) in diseaseList.withIndex()) {
            val diseaseItemView = createDiseaseItem(item, index)
            container.addView(diseaseItemView)
        }

        // Add some bottom padding to ensure scrolling works well
        val paddingView = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(20)
            )
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        container.addView(paddingView)

        Log.d(TAG, "Added ${diseaseList.size} disease items to container")
    }

    private fun createDiseaseItem(item: DiseaseItem, index: Int): View {
        // Create the main container
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
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = "${item.disease}, ${item.cases} cases, ${item.percent} percent of total"

            // Add tag to identify this as a disease item
            tag = "disease_item_${index}"

            // ADD CLICK LISTENER HERE
            setOnClickListener {
                Log.d(TAG, "Disease item clicked: ${item.disease}")
                // Highlight the corresponding pie slice when disease name is clicked
                highlightPieSliceForDisease(item.disease, index)
            }
        }

        // Create the colored dot - THIS WILL MATCH THE PIE CHART SLICE COLOR
        val colorDot = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(12), dpToPx(12)).apply {
                marginEnd = dpToPx(12)
            }

            // Create a circular shape with the exact color from the pie chart
            val gradientDrawable = GradientDrawable()
            gradientDrawable.shape = GradientDrawable.OVAL
            gradientDrawable.setColor(item.color)
            gradientDrawable.cornerRadius = dpToPx(6).toFloat()
            background = gradientDrawable
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        // Create disease name TextView - MAKE SURE TEXT IS SET
        val diseaseName = TextView(requireContext()).apply {
            text = item.disease
            textSize = 14f
            setTextColor(getPrimaryTextColor())
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        // Create cases count TextView
        val casesCount = TextView(requireContext()).apply {
            text = "${item.cases} cases (${item.percent}%)"
            textSize = 12f
            setTextColor(getSecondaryTextColor())
            setTypeface(null, Typeface.BOLD)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        // Add views to container
        container.addView(colorDot)
        container.addView(diseaseName)
        container.addView(casesCount)

        // DEBUG: Log the created item
        Log.d(TAG, "Created disease item: ${item.disease} - ${item.cases} cases (${item.percent}%)")

        return container
    }

    // NEW FUNCTION: Highlight pie slice when disease name is clicked
    private fun highlightPieSliceForDisease(diseaseName: String, diseaseIndex: Int) {
        Log.d(TAG, "Highlighting pie slice for disease: $diseaseName at index $diseaseIndex")

        // Get the current pie chart from the adapter
        val currentPosition = pieChartPager.currentItem
        val adapter = pieChartPager.adapter as? PieChartPagerAdapter
        val pieChart = adapter?.getPieChart(currentPosition)

        pieChart?.let {
            // Highlight the pie slice
            it.highlightValue(diseaseIndex.toFloat(), 0, true)

            // Also highlight the disease item in the list
            highlightDiseaseInList(diseaseIndex, currentPosition)
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private data class DiseaseItem(
        val disease: String,
        val cases: Int,
        val percent: Int,
        val color: Int
    )

    private data class DiseaseItemPage(
        val diseaseName: String,
        val caseCount: Int,
        val color: Int
    )

    private inner class DiseaseListPagerAdapter : RecyclerView.Adapter<DiseaseListPagerAdapter.DiseasePageVH>() {

        private var pages: List<List<DiseaseItemPage>> = emptyList()

        inner class DiseasePageVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val container: LinearLayout = itemView.findViewById(R.id.diseaseItemsContainer)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DiseasePageVH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_disease_page, parent, false)
            // Ensure the view takes full width and height
            view.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            return DiseasePageVH(view)  // FIXED: Changed 'itemView' to 'view'
        }

        override fun onBindViewHolder(holder: DiseasePageVH, position: Int) {
            val pageDiseases = pages.getOrNull(position) ?: emptyList()
            holder.container.removeAllViews()

            if (pageDiseases.isEmpty()) {
                val noDataText = TextView(requireContext()).apply {
                    text = "No disease data available"
                    setTextColor(getSecondaryTextColor())
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setPadding(0, dpToPx(20), 0, 0)
                }
                holder.container.addView(noDataText)
                return
            }

            // Add disease items for this page
            for ((index, diseaseItem) in pageDiseases.withIndex()) {
                val diseaseView = createDiseaseItemView(diseaseItem, index)
                holder.container.addView(diseaseView)
            }

            // Add some bottom padding
            val paddingView = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(20)
                )
            }
            holder.container.addView(paddingView)
        }

        override fun getItemCount(): Int = pages.size

        fun updateData(newPages: List<List<DiseaseItemPage>>) {
            pages = newPages
            notifyDataSetChanged()
        }

        fun updateSelectedDisease(diseaseName: String?) {
            selectedDisease = diseaseName
            notifyDataSetChanged()
        }

        private fun createDiseaseItemView(diseaseItem: DiseaseItemPage, index: Int): LinearLayout {
            val container = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dpToPx(8)
                }
                setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))

                if (diseaseItem.diseaseName == selectedDisease) {
                    setBackgroundColor(getHighlightColor())
                } else {
                    setBackgroundColor(Color.TRANSPARENT)
                }

                isClickable = true
                tag = diseaseItem.diseaseName

                setOnClickListener {
                    highlightDiseaseInBarChartList(diseaseItem.diseaseName)
                }
            }

            // Bullet point
            val bullet = TextView(requireContext()).apply {
                text = "•"
                setTextColor(diseaseItem.color)
                textSize = 18f
                layoutParams = LinearLayout.LayoutParams(
                    dpToPx(24),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            // Disease name
            val diseaseText = TextView(requireContext()).apply {
                text = diseaseItem.diseaseName
                setTextColor(getPrimaryTextColor())
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
                setSingleLine(true)
                ellipsize = TextUtils.TruncateAt.END
            }

            // Case count
            val caseCountText = TextView(requireContext()).apply {
                text = "${diseaseItem.caseCount} cases"
                setTextColor(getSecondaryTextColor())
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            container.addView(bullet)
            container.addView(diseaseText)
            container.addView(caseCountText)

            return container
        }
    }

    private inner class PieChartPagerAdapter : RecyclerView.Adapter<PieChartPagerAdapter.PieChartVH>() {

        private val pieCharts = mutableMapOf<Int, PieChart>()

        inner class PieChartVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val pieChart: PieChart = itemView.findViewById(R.id.pieChartItem)
            val diseaseContainer: LinearLayout = itemView.findViewById(R.id.diseaseContainer)
            val municipalityTitle: TextView = itemView.findViewById(R.id.municipalityTitle)
            val cardView: CardView = itemView.findViewById(R.id.cardView)
            val diseaseScrollView: ScrollView = itemView.findViewById(R.id.diseaseScrollView)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PieChartVH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_pie_chart_card, parent, false)
            return PieChartVH(view)
        }

        override fun onBindViewHolder(holder: PieChartVH, position: Int) {
            // Store the pie chart reference
            pieCharts[position] = holder.pieChart

            val municipality = municipalities[position]
            val calendar = Calendar.getInstance()
            val currentMonth = SimpleDateFormat("MMMM", Locale.getDefault()).format(calendar.time)
            val currentYear = calendar.get(Calendar.YEAR)
            val currentWeek = getCurrentWeekOfMonth()
            val weekNum = currentWeekFilter.replace("Week ", "").toIntOrNull() ?: 1
            val weekLabel = if (weekNum == currentWeek) "$currentWeekFilter (Current)" else currentWeekFilter

            holder.municipalityTitle.text = "$municipality Municipality\n$currentMonth $currentYear • $weekLabel"
            holder.municipalityTitle.contentDescription = "Disease list for $municipality municipality for $weekLabel of $currentMonth $currentYear"

            // Use theme-aware color
            holder.municipalityTitle.setTextColor(getPrimaryTextColor())

            setupPieChart(holder.pieChart, holder.diseaseContainer, holder.diseaseScrollView, position)
            loadPieChartData(municipality, holder.pieChart, holder.diseaseContainer, position)
        }

        override fun getItemCount(): Int = municipalities.size

        fun refreshCurrentPage() {
            val currentPosition = pieChartPager.currentItem
            notifyItemChanged(currentPosition)
        }

        // Get pie chart by position
        fun getPieChart(position: Int): PieChart? {
            return pieCharts[position]
        }
    }
}