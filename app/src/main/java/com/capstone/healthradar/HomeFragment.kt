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
import androidx.core.content.ContextCompat
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
    private lateinit var monthYearTextView: TextView

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
        municipalityIndicator = root.findViewById(R.id.municipalityIndicator)
        userNameTv = root.findViewById(R.id.userName)
        weekSpinner = root.findViewById(R.id.weekSpinner)
        leftArrow = root.findViewById(R.id.leftArrow)
        rightArrow = root.findViewById(R.id.rightArrow)
        swipeHint = root.findViewById(R.id.swipeHint)
        monthYearTextView = root.findViewById(R.id.monthYearTextView)
    }

    private fun initUi() {
        setupCharts()
        setupMunicipalityIndicator()
        setupWeekSpinner()
        updateMonthYearDisplay()
        loadBarChartData()
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

    private fun setupPieChartPager() {
        pieChartPager.adapter = PieChartPagerAdapter()
        pieChartPager.orientation = ViewPager2.ORIENTATION_HORIZONTAL

        pieChartPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentMunicipalityIndex = position
                updateMunicipalityIndicator()
                updateNavigationArrows()
                refreshChartData()
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
                layoutParams = LinearLayout.LayoutParams(dpToPx(12), dpToPx(12)).apply {
                    marginEnd = dpToPx(8)
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
        monthYearTextView.setTextColor(getPrimaryTextColor())
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
                    userNameTv.setTextColor(getPrimaryTextColor())
                    userNameTv.textSize = 18f
                    userNameTv.setTypeface(null, Typeface.BOLD)
                }
        } else {
            userNameTv.text = "Hello, User"
            userNameTv.setTextColor(getPrimaryTextColor())
            userNameTv.textSize = 18f
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
                textColor = getPrimaryTextColor()
                textSize = 11f
                granularity = 1f
                setLabelCount(5, true)
                labelRotationAngle = 0f
                setCenterAxisLabels(false)
                setAvoidFirstLastClipping(false)
                isGranularityEnabled = true
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
            }

            axisRight.apply {
                setDrawGridLines(false)
                setDrawAxisLine(false)
                setDrawLabels(false)
                axisMinimum = 0f
            }

            legend.isEnabled = false
            setNoDataText("No data available for selected week")
            setNoDataTextColor(getPrimaryTextColor())
            setNoDataTextTypeface(Typeface.DEFAULT_BOLD)
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
                    val diseaseMap = mutableMapOf<String, Float>()
                    for (doc in snapshot.documents) {
                        val dateStr = doc.getString("DateReported") ?: doc.getString("uploadedAt")
                        if (dateStr != null && isDateFromCurrentMonth(dateStr)) {
                            val cases = when (val raw = doc.get("CaseCount")) {
                                is Number -> raw.toFloat()
                                is String -> raw.toFloatOrNull() ?: 0f
                                else -> 0f
                            }
                            val diseaseName = doc.getString("DiseaseName") ?: "Unknown"
                            if (cases > 0f && diseaseName != "Unknown") {
                                diseaseMap[diseaseName] = (diseaseMap[diseaseName] ?: 0f) + cases
                            }
                        }
                    }

                    val sortedDiseases = diseaseMap.entries.sortedByDescending { it.value }
                    val entries = ArrayList<BarEntry>()
                    val diseaseLabels = ArrayList<String>()

                    for ((index, entry) in sortedDiseases.withIndex()) {
                        entries.add(BarEntry(index.toFloat(), entry.value))
                        diseaseLabels.add(entry.key)
                    }

                    barChart.xAxis.valueFormatter = IndexAxisValueFormatter(diseaseLabels)
                    barChart.xAxis.labelCount = diseaseLabels.size

                    val dataSet = BarDataSet(entries, "").apply {
                        color = getPrimaryColor()
                        valueTextColor = getPrimaryTextColor()
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

                    barChart.data = data
                    barChart.invalidate()
                    barChart.animateY(1000, Easing.EaseInOutCubic)

                } catch (ex: Exception) {
                    Log.e(TAG, "Error loading bar chart data", ex)
                }
            }
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
            setNoDataTextColor(getPrimaryTextColor())
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
        val diseaseList = municipalityDiseaseData[municipalityPosition] ?: return

        if (sliceIndex < diseaseList.size) {
            removeHighlightFromDiseaseList(diseaseContainer)
            val selectedView = diseaseContainer.getChildAt(sliceIndex)
            selectedView?.setBackgroundColor(Color.parseColor("#3A3A3A"))
        }
    }

    private fun removeHighlightFromDiseaseList(diseaseContainer: LinearLayout) {
        for (i in 0 until diseaseContainer.childCount) {
            val view = diseaseContainer.getChildAt(i)
            view.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    private fun loadPieChartData(municipality: String, pieChart: PieChart, diseaseContainer: LinearLayout, position: Int) {
        val weekNum = currentWeekFilter.replace("Week ", "").toIntOrNull() ?: 1

        db.collection("healthradarDB").document("centralizedData").collection("allCases")
            .whereEqualTo("Municipality", municipality)
            .whereEqualTo("Week", weekNum)
            .get()
            .addOnSuccessListener { snapshot ->
                try {
                    val diseaseTotals = mutableMapOf<String, Float>()
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
                                }
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
                    }

                    // Store disease data for this municipality
                    municipalityDiseaseData[position] = diseaseList

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

                } catch (ex: Exception) {
                    Log.e(TAG, "Error building pie chart", ex)
                    pieChart.clear()
                    updateDiseaseList(emptyList(), diseaseContainer)
                }
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

        // Create scroll hint text for accessibility
        if (diseaseList.size > 5) {
            val scrollHint = TextView(requireContext()).apply {
                text = "Scroll down to see more diseases"
                textSize = 11f
                setTextColor(getSecondaryTextColor())
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dpToPx(8))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                contentDescription = "Scroll down to see more diseases"
            }
            container.addView(scrollHint)
        }

        // Add disease items
        for ((index, item) in diseaseList.withIndex()) {
            container.addView(createDiseaseItem(item, index, diseaseList.size))
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
    }

    private fun createDiseaseItem(item: DiseaseItem, index: Int, totalItems: Int): View {
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
            background = null
            isClickable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = "${item.disease}, ${item.cases} cases, ${item.percent} percent of total"
        }

        // Create the colored dot - THIS WILL MATCH THE PIE CHART SLICE COLOR
        val colorDot = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(12), dpToPx(12)).apply {
                marginEnd = dpToPx(12)
            }

            // Create a circular shape with the exact color from the pie chart
            val gradientDrawable = GradientDrawable()
            gradientDrawable.shape = GradientDrawable.OVAL
            gradientDrawable.setColor(item.color) // Use the color from DiseaseItem
            gradientDrawable.cornerRadius = dpToPx(6).toFloat()
            background = gradientDrawable
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        // Create disease name TextView
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

        return container
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private data class DiseaseItem(
        val disease: String,
        val cases: Int,
        val percent: Int,
        val color: Int  // This color matches the pie chart slice
    )

    private inner class PieChartPagerAdapter : RecyclerView.Adapter<PieChartPagerAdapter.PieChartVH>() {

        inner class PieChartVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val pieChart: PieChart = itemView.findViewById(R.id.pieChartItem)
            val diseaseContainer: LinearLayout = itemView.findViewById(R.id.diseaseContainer)
            val municipalityTitle: TextView = itemView.findViewById(R.id.municipalityTitle)
            val cardView: CardView = itemView.findViewById(R.id.cardView)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PieChartVH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_pie_chart_card, parent, false)
            return PieChartVH(view)
        }

        override fun onBindViewHolder(holder: PieChartVH, position: Int) {
            val municipality = municipalities[position]
            val calendar = Calendar.getInstance()
            val currentMonth = SimpleDateFormat("MMMM", Locale.getDefault()).format(calendar.time)
            val currentYear = calendar.get(Calendar.YEAR)
            val currentWeek = getCurrentWeekOfMonth()
            val weekNum = currentWeekFilter.replace("Week ", "").toIntOrNull() ?: 1
            val weekLabel = if (weekNum == currentWeek) "$currentWeekFilter (Current)" else currentWeekFilter

            holder.municipalityTitle.text = "$municipality Municipality\n$currentMonth $currentYear • $weekLabel"
            holder.municipalityTitle.contentDescription = "Disease data for $municipality municipality for $weekLabel of $currentMonth $currentYear"

            // Set the text color to ensure visibility
            holder.municipalityTitle.setTextColor(getPrimaryTextColor())

            // Set card background if needed (optional)
            // holder.cardView.setCardBackgroundColor(getCardBackgroundColor())

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