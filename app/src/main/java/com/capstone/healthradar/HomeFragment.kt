package com.capstone.healthradar

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
import androidx.core.content.ContextCompat
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
    private lateinit var diseaseListViewPager: ViewPager2
    private lateinit var diseasePageTitle: TextView
    private lateinit var diseaseLeftArrow: ImageView
    private lateinit var diseaseRightArrow: ImageView
    private lateinit var diseasePageIndicator: LinearLayout
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val TAG = "HomeFragment"
    private val pieColors = listOf(
        "#FF6B6B", "#4ECDC4", "#45B7D1", "#96CEB4", "#FECA57",
        "#FF9FF3", "#54A0FF", "#5F27CD", "#00D2D3", "#FF9F43",
        "#10AC84", "#EE5A24", "#0984E3", "#A29BFE", "#FD79A8"
    ).map { it.toColorInt() }
    private val municipalities = listOf("Mandaue", "Liloan", "Consolacion")
    private var currentMunicipalityIndex = 0
    private var currentWeekFilter = "Week 1"
    private var currentMonthYear = ""
    private val municipalityDiseaseData = mutableMapOf<Int, List<DiseaseItem>>()
    private val diseaseContainerMap = mutableMapOf<Int, LinearLayout>()
    private val diseaseScrollViewMap = mutableMapOf<Int, ScrollView>()
    private var barChartDiseases = mutableListOf<String>()
    private var barChartColors = mutableMapOf<String, Int>()
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
        updateThemeColors()
        userNameTv.textSize = 24f
    }

    private fun updateThemeColors() {
        // Check if fragment is attached before updating UI
        if (!isAdded) return

        // Update all text colors
        userNameTv.setTextColor(getPrimaryTextColor())
        monthYearTextView.setTextColor(getSecondaryTextColor())
        diseasePageTitle.setTextColor(getPrimaryTextColor())
        swipeHint.setTextColor(getSecondaryTextColor())
        updateArrowColors()
    }

    private fun updateArrowColors() {
        val arrowColor = getPrimaryColor()

        leftArrow.setColorFilter(arrowColor, android.graphics.PorterDuff.Mode.SRC_IN)
        rightArrow.setColorFilter(arrowColor, android.graphics.PorterDuff.Mode.SRC_IN)
        diseaseLeftArrow.setColorFilter(arrowColor, android.graphics.PorterDuff.Mode.SRC_IN)
        diseaseRightArrow.setColorFilter(arrowColor, android.graphics.PorterDuff.Mode.SRC_IN)
    }

    private fun getPrimaryTextColor(): Int {
        return if (isDarkMode()) {
            Color.WHITE
        } else {
            Color.BLACK
        }
    }

    private fun getSecondaryTextColor(): Int {
        return if (isDarkMode()) {
            Color.parseColor("#CCCCCC")
        } else {
            Color.parseColor("#666666")
        }
    }

    private fun getPrimaryColor(): Int {
        return Color.parseColor("#6366F1")
    }

    private fun getCardBackgroundColor(): Int {
        return if (isDarkMode()) {
            Color.parseColor("#1E1E1E")
        } else {
            Color.WHITE
        }
    }

    private fun getSurfaceColor(): Int {
        return if (isDarkMode()) {
            Color.parseColor("#2D2D2D")
        } else {
            Color.WHITE
        }
    }

    private fun getHighlightColor(): Int {
        return if (isDarkMode()) {
            Color.parseColor("#4A4A4A")
        } else {
            Color.parseColor("#F0F0F0")
        }
    }

    private fun isDarkMode(): Boolean {
        // Safe way to check if fragment is attached before accessing resources
        return if (isAdded && context != null) {
            val currentNightMode = requireContext().resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            currentNightMode == Configuration.UI_MODE_NIGHT_YES
        } else {
            // Default to light mode if fragment is not attached
            false
        }
    }

    private fun setupWeekSpinner() {
        // Check if fragment is attached before setting up spinner
        if (!isAdded || context == null) return

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

        diseaseListViewPager.layoutParams.height = dpToPx(250)

        diseaseListViewPager.getChildAt(0)?.let { recyclerView ->
            if (recyclerView is RecyclerView) {
                recyclerView.isNestedScrollingEnabled = false
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
        // Check if fragment is attached before updating UI
        if (!isAdded) return

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
        // Check if fragment is attached before updating UI
        if (!isAdded) return

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

        pieChartPager.isUserInputEnabled = true
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
        // Check if fragment is attached before updating UI
        if (!isAdded) return

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
        // Check if fragment is attached before updating UI
        if (!isAdded) return

        for (i in 0 until municipalityIndicator.childCount) {
            val dot = municipalityIndicator.getChildAt(i)
            dot.setBackgroundColor(if (i == currentMunicipalityIndex) getPrimaryColor() else Color.parseColor("#E0E0E0"))
        }
    }

    private fun updateMonthYearDisplay() {
        // Check if fragment is attached before updating UI
        if (!isAdded) return

        val calendar = Calendar.getInstance()
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        currentMonthYear = monthFormat.format(calendar.time)
        val currentWeek = getCurrentWeekOfMonth()
        monthYearTextView.text = "$currentMonthYear • Week $currentWeek"
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
                    // Check if fragment is still attached before updating UI
                    if (!isAdded) return@addOnSuccessListener

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
                    userNameTv.setTextColor(getPrimaryTextColor())
                    userNameTv.textSize = 24f
                    userNameTv.setTypeface(null, Typeface.BOLD)
                }
                .addOnFailureListener { e ->
                    // Check if fragment is still attached before updating UI
                    if (!isAdded) return@addOnFailureListener

                    userNameTv.text = "Hello, ${currentUser.email?.substringBefore('@') ?: "User"}"
                    userNameTv.setTextColor(getPrimaryTextColor())
                    userNameTv.textSize = 24f
                    userNameTv.setTypeface(null, Typeface.BOLD)
                    Log.e(TAG, "Error loading user name", e)
                }
        } else {
            // Check if fragment is attached before updating UI
            if (isAdded) {
                userNameTv.text = "Hello, User"
                userNameTv.setTextColor(getPrimaryTextColor())
                userNameTv.textSize = 24f
                userNameTv.setTypeface(null, Typeface.BOLD)
            }
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
                textColor = Color.TRANSPARENT
                textSize = 0f
                granularity = 1f
                setLabelCount(5, true)
                labelRotationAngle = 0f
                setCenterAxisLabels(false)
                setAvoidFirstLastClipping(true)
                isGranularityEnabled = true
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return ""
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

            setNoDataText("No Disease Data Available")
            setNoDataTextColor(getSecondaryTextColor())
            setNoDataTextTypeface(Typeface.DEFAULT_BOLD)

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
                // Check if fragment is still attached before processing data
                if (!isAdded) return@addOnSuccessListener

                try {
                    if (snapshot.isEmpty) {
                        showNoDataInBarChart()
                        showNoDiseaseData()
                        return@addOnSuccessListener
                    }

                    val diseaseMap = mutableMapOf<String, Float>()
                    val diseaseCaseMap = mutableMapOf<String, Int>() // Store case counts
                    val diseaseColorMap = mutableMapOf<String, Int>()

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
                                diseaseMap[diseaseName] = (diseaseMap[diseaseName] ?: 0f) + cases
                                diseaseCaseMap[diseaseName] = (diseaseCaseMap[diseaseName] ?: 0) + intCases
                                allDiseases.add(diseaseName)
                            }
                        }
                    }

                    if (diseaseMap.isEmpty()) {
                        showNoDataInBarChart()
                        showNoDiseaseData()
                        return@addOnSuccessListener
                    }

                    val sortedDiseases = diseaseMap.entries.sortedByDescending { it.value }
                    val entries = ArrayList<BarEntry>()
                    barChartDiseases.clear()
                    barChartColors.clear()

                    for ((index, entry) in sortedDiseases.withIndex()) {
                        entries.add(BarEntry(index.toFloat(), entry.value))
                        barChartDiseases.add(entry.key)

                        val color = generateColorForDisease(entry.key)
                        barChartColors[entry.key] = color

                        diseaseColorMap[entry.key] = color
                    }

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
                    updateDiseaseListForBarChartWithCases(diseaseColorMap, diseaseCaseMap)

                } catch (ex: Exception) {
                    Log.e(TAG, "Error loading bar chart data", ex)
                    showNoDataInBarChart()
                    showNoDiseaseData()
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error loading bar chart data", exception)
                // Check if fragment is still attached before showing error
                if (isAdded) {
                    showNoDataInBarChart()
                    showNoDiseaseData()
                }
            }
    }


    private fun showNoDataInBarChart() {
        // Check if fragment is attached before updating UI
        if (!isAdded) return

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

    private fun generateColorForDisease(diseaseName: String): Int {
        // Hash the disease name to get a consistent index
        val hash = abs(diseaseName.hashCode())
        val colorIndex = hash % pieColors.size
        return pieColors[colorIndex]
    }

    private fun updateDiseaseListForBarChartWithCases(
        diseaseColorMap: Map<String, Int>,
        diseaseCaseMap: Map<String, Int>
    ) {
        // Check if fragment is attached before updating UI
        if (!isAdded) return

        if (diseaseColorMap.isEmpty() || diseaseCaseMap.isEmpty()) {
            showNoDiseaseData()
            return
        }
        val sortedDiseases = diseaseCaseMap.entries.sortedByDescending { it.value }
        val diseaseItems = sortedDiseases.map { entry ->
            val diseaseName = entry.key
            val caseCount = entry.value
            val color = diseaseColorMap[diseaseName] ?: generateColorForDisease(diseaseName)
            DiseaseItemPage(diseaseName, caseCount, color)
        }
        paginatedDiseases = diseaseItems.chunked(diseasesPerPage).toMutableList()
        (diseaseListViewPager.adapter as? DiseaseListPagerAdapter)?.updateData(paginatedDiseases)
        updateDiseasePageUI(0)
        diseaseListViewPager.currentItem = 0
        selectedDisease = null
    }

    private fun showNoDiseaseData() {
        // Check if fragment is attached before updating UI
        if (!isAdded) return

        paginatedDiseases.clear()
        (diseaseListViewPager.adapter as? DiseaseListPagerAdapter)?.updateData(emptyList())
        diseasePageTitle.text = "No disease data available"
        diseasePageTitle.setTextColor(getSecondaryTextColor())
        diseaseLeftArrow.visibility = View.INVISIBLE
        diseaseRightArrow.visibility = View.INVISIBLE
        diseasePageIndicator.removeAllViews()
        selectedDisease = null
    }

    private fun highlightDiseaseInBarChartList(diseaseName: String) {
        // Check if fragment is attached before updating UI
        if (!isAdded) return

        removeHighlightFromBarChartList()
        selectedDisease = diseaseName
        for ((pageIndex, page) in paginatedDiseases.withIndex()) {
            val diseaseIndex = page.indexOfFirst { it.diseaseName == diseaseName }
            if (diseaseIndex != -1) {
                diseaseListViewPager.currentItem = pageIndex
                val barIndex = barChartDiseases.indexOf(diseaseName)
                if (barIndex >= 0) {
                    barChart.highlightValue(barIndex.toFloat(), 0, false)
                }
                (diseaseListViewPager.adapter as? DiseaseListPagerAdapter)?.updateSelectedDisease(diseaseName)
                break
            }
        }
    }

    private fun removeHighlightFromBarChartList() {
        // Check if fragment is attached before updating UI
        if (!isAdded) return

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
        // Check if fragment is attached before setting up chart
        if (!isAdded) return

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
        diseaseContainerMap[position] = diseaseContainer
        diseaseScrollViewMap[position] = scrollView
        Log.d(TAG, "Stored container and scroll view for position $position")
    }

    private fun highlightDiseaseInList(sliceIndex: Int, municipalityPosition: Int) {
        // Check if fragment is attached before updating UI
        if (!isAdded) return

        val diseaseList = municipalityDiseaseData[municipalityPosition] ?: return
        val diseaseContainer = diseaseContainerMap[municipalityPosition] ?: return
        val scrollView = diseaseScrollViewMap[municipalityPosition]

        if (sliceIndex < diseaseList.size) {
            removeHighlightFromDiseaseList(municipalityPosition)

            val selectedDisease = diseaseList[sliceIndex]
            Log.d(TAG, "Looking for disease: ${selectedDisease.disease}")

            // highlight the disease
            for (i in 0 until diseaseContainer.childCount) {
                val view = diseaseContainer.getChildAt(i)
                if (view is LinearLayout && view.tag?.toString()?.contains("disease_item_") == true) {
                    if (view.getChildAt(1) is TextView) {
                        val diseaseTextView = view.getChildAt(1) as TextView
                        if (diseaseTextView.text.toString() == selectedDisease.disease) {
                            Log.d(TAG, "Found disease item at position $i")
                            view.setBackgroundColor(getHighlightColor())
                            scrollView?.post {
                                val top = view.top
                                val scrollViewHeight = scrollView.height
                                val viewHeight = view.height
                                val targetScroll = top - (scrollViewHeight / 2) + (viewHeight / 2)
                                scrollView.smoothScrollTo(0, targetScroll.coerceAtLeast(0))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun removeHighlightFromDiseaseList(municipalityPosition: Int) {
        // Check if fragment is attached before updating UI
        if (!isAdded) return

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
                // Check if fragment is still attached before processing data
                if (!isAdded) return@addOnSuccessListener

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
                    for ((index, entry) in sorted.withIndex()) {
                        val disease = entry.key
                        val totalCases = entry.value
                        entries.add(PieEntry(totalCases, disease))
                        val percent = if (total > 0f) (totalCases / total * 100f) else 0f
                        val percentInt = percent.roundToInt().coerceAtLeast(0)

                        val color = pieColors[index % pieColors.size]

                        diseaseList.add(DiseaseItem(disease, totalCases.roundToInt(), percentInt, color))

                        Log.d(TAG, "Adding to list: $disease - $totalCases cases ($percentInt%)")
                    }

                    municipalityDiseaseData[position] = diseaseList
                    Log.d(TAG, "Stored ${diseaseList.size} diseases for position $position")

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
                // Check if fragment is still attached before showing error
                if (isAdded) {
                    pieChart.clear()
                    updateDiseaseList(emptyList(), diseaseContainer)
                    municipalityDiseaseData[position] = emptyList()
                }
            }
    }

    private fun updateDiseaseList(diseaseList: List<DiseaseItem>, container: LinearLayout) {
        // Check if fragment is attached before updating UI
        if (!isAdded) return

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

        for ((index, item) in diseaseList.withIndex()) {
            val diseaseItemView = createDiseaseItem(item, index)
            container.addView(diseaseItemView)
        }
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
        // Check if fragment is attached before creating view
        if (!isAdded || context == null) {
            return View(context) // Return empty view if not attached
        }

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
            tag = "disease_item_${index}"

            setOnClickListener {
                Log.d(TAG, "Disease item clicked: ${item.disease}")
                highlightPieSliceForDisease(item.disease, index)
            }
        }

        val colorDot = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(12), dpToPx(12)).apply {
                marginEnd = dpToPx(12)
            }

            val gradientDrawable = GradientDrawable()
            gradientDrawable.shape = GradientDrawable.OVAL
            gradientDrawable.setColor(item.color)
            gradientDrawable.cornerRadius = dpToPx(6).toFloat()
            background = gradientDrawable
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

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

        val casesCount = TextView(requireContext()).apply {
            text = "${item.cases} cases (${item.percent}%)"
            textSize = 12f
            setTextColor(getSecondaryTextColor())
            setTypeface(null, Typeface.BOLD)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        container.addView(colorDot)
        container.addView(diseaseName)
        container.addView(casesCount)
        Log.d(TAG, "Created disease item: ${item.disease} - ${item.cases} cases (${item.percent}%)")

        return container
    }

    private fun highlightPieSliceForDisease(diseaseName: String, diseaseIndex: Int) {
        // Check if fragment is attached before updating UI
        if (!isAdded) return

        Log.d(TAG, "Highlighting pie slice for disease: $diseaseName at index $diseaseIndex")

        val currentPosition = pieChartPager.currentItem
        val adapter = pieChartPager.adapter as? PieChartPagerAdapter
        val pieChart = adapter?.getPieChart(currentPosition)

        pieChart?.let {
            it.highlightValue(diseaseIndex.toFloat(), 0, true)
            highlightDiseaseInList(diseaseIndex, currentPosition)
        }
    }

    private fun dpToPx(dp: Int): Int {
        // Safe way to convert dp to px
        return if (isAdded && context != null) {
            val density = requireContext().resources.displayMetrics.density
            (dp * density).toInt()
        } else {
            // Default fallback
            (dp * 3).toInt() // Approximate conversion
        }
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
            view.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            return DiseasePageVH(view)
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
            for ((index, diseaseItem) in pageDiseases.withIndex()) {
                val diseaseView = createDiseaseItemView(diseaseItem, index)
                holder.container.addView(diseaseView)
            }
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
            // Check if fragment is attached before creating view
            if (!isAdded || context == null) {
                return LinearLayout(context)
            }

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
            val bullet = TextView(requireContext()).apply {
                text = "•"
                setTextColor(diseaseItem.color)
                textSize = 18f
                layoutParams = LinearLayout.LayoutParams(
                    dpToPx(24),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
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
            holder.municipalityTitle.setTextColor(getPrimaryTextColor())

            setupPieChart(holder.pieChart, holder.diseaseContainer, holder.diseaseScrollView, position)
            loadPieChartData(municipality, holder.pieChart, holder.diseaseContainer, position)
        }

        override fun getItemCount(): Int = municipalities.size

        fun refreshCurrentPage() {
            val currentPosition = pieChartPager.currentItem
            notifyItemChanged(currentPosition)
        }

        fun getPieChart(position: Int): PieChart? {
            return pieCharts[position]
        }
    }
}