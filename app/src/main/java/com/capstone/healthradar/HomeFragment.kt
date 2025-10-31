package com.capstone.healthradar

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {

    private lateinit var barChart: BarChart
    private lateinit var pieChart: PieChart
    private lateinit var pieChartTitle: TextView
    private lateinit var pieLegendLayout: LinearLayout

    private val db = FirebaseFirestore.getInstance()
    private val TAG = "HomeFragment"
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())

    private val pieColors = listOf(
        "#FFB74D".toColorInt(),
        "#4DB6AC".toColorInt(),
        "#BA68C8".toColorInt(),
        "#81C784".toColorInt(),
        "#64B5F6".toColorInt()
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        barChart = view.findViewById(R.id.barChart)
        pieChart = view.findViewById(R.id.pieChart)
        pieChartTitle = view.findViewById(R.id.pieChartTitle)
        pieLegendLayout = view.findViewById(R.id.pieLegendLayout)

        setupCharts()
        setupButtons(view)

        // Default selection
        selectMunicipality("Liloan")

        return view
    }

    private fun setupCharts() {
        // Pie chart setup
        pieChart.apply {
            setBackgroundColor(Color.WHITE)
            setUsePercentValues(true)
            isDrawHoleEnabled = true
            holeRadius = 45f
            transparentCircleRadius = 50f
            setEntryLabelColor(Color.TRANSPARENT)
            setCenterTextSize(16f)
            setCenterTextTypeface(Typeface.DEFAULT_BOLD)
            description.isEnabled = false
            legend.isEnabled = false
            isRotationEnabled = false
        }

        // Bar chart setup
        barChart.apply {
            setBackgroundColor(Color.WHITE)
            setDrawGridBackground(false)
            axisRight.isEnabled = false
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)

            xAxis.apply {
                position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = Color.DKGRAY
                textSize = 12f
                granularity = 1f
            }

            axisLeft.apply {
                textColor = Color.DKGRAY
                textSize = 12f
                setDrawGridLines(true)
                gridColor = Color.LTGRAY
            }

            animateY(1000)
        }
    }

    private fun setupButtons(view: View) {
        view.findViewById<Button>(R.id.btnLiloan).setOnClickListener { selectMunicipality("Liloan") }
        view.findViewById<Button>(R.id.btnConsolacion).setOnClickListener { selectMunicipality("Consolacion") }
        view.findViewById<Button>(R.id.btnMandaue).setOnClickListener { selectMunicipality("Mandaue") }
    }

    private fun selectMunicipality(name: String) {
        pieChartTitle.text = "$name Chart"
        loadPieChart(name)
        loadBarChart(name)
    }

    private fun DocumentSnapshot.getCaseCountAsFloat(): Float {
        return when (val raw = get("CaseCount")) {
            is Long -> raw.toFloat()
            is Int -> raw.toFloat()
            is Double -> raw.toFloat()
            is Float -> raw
            is String -> raw.toFloatOrNull() ?: 0f
            else -> 0f
        }
    }

    private fun loadPieChart(municipality: String) {
        db.collection("healthradarDB")
            .document("centralizedData")
            .collection("allCases")
            .get()
            .addOnSuccessListener { snapshot ->
                val ctx = context ?: return@addOnSuccessListener

                val filtered = snapshot.documents.filter {
                    it.getString("Municipality")?.replace("-", "")?.lowercase() ==
                            municipality.replace("-", "").lowercase()
                }

                val diseaseSums = mutableMapOf<String, Float>()
                for (doc in filtered) {
                    val name = doc.getString("DiseaseName") ?: "Unknown"
                    val cases = doc.getCaseCountAsFloat()
                    if (cases > 0f) diseaseSums[name] = (diseaseSums[name] ?: 0f) + cases
                }

                pieLegendLayout.removeAllViews()

                if (diseaseSums.isNotEmpty()) {
                    val entries = diseaseSums.map { PieEntry(it.value, "") } // hide label
                    val ds = PieDataSet(ArrayList(entries), "").apply {
                        colors = pieColors.take(diseaseSums.size).ifEmpty { pieColors }
                        valueTextColor = Color.DKGRAY
                        valueTextSize = 12f
                        sliceSpace = 2f
                        selectionShift = 5f
                    }

                    pieChart.data = PieData(ds)
                    pieChart.centerText = "$municipality\nDisease Cases"
                    pieChart.invalidate()

                    // Manual legend under pie chart
                    diseaseSums.keys.forEachIndexed { index, disease ->
                        val legendItem = LinearLayout(ctx).apply {
                            orientation = LinearLayout.HORIZONTAL
                            setPadding(0, 4, 0, 4)
                        }

                        val colorBox = View(ctx).apply {
                            setBackgroundColor(ds.colors[index % ds.colors.size])
                            layoutParams = LinearLayout.LayoutParams(40, 40)
                        }

                        val text = TextView(ctx).apply {
                            this.text = disease
                            setPadding(16, 0, 0, 0)
                            setTextColor(Color.DKGRAY)
                            textSize = 14f
                        }

                        legendItem.addView(colorBox)
                        legendItem.addView(text)
                        pieLegendLayout.addView(legendItem)
                    }
                } else {
                    pieChart.clear()
                    pieChart.invalidate()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to fetch pie data", e)
                pieChart.clear()
                pieChart.invalidate()
            }
    }

    private fun loadBarChart(municipality: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            db.collection("healthradarDB")
                .document("centralizedData")
                .collection("allCases")
                .get()
                .addOnSuccessListener { snapshot ->
                    val filtered = snapshot.documents.filter {
                        it.getString("Municipality")?.replace("-", "")?.lowercase() ==
                                municipality.replace("-", "").lowercase()
                    }

                    val weekSums = FloatArray(4)
                    for (doc in filtered) {
                        val cases = doc.getCaseCountAsFloat()
                        val date = when (val dateField = doc.get("DateReported")) {
                            is Timestamp -> dateField.toDate()
                            is String -> try { isoFormat.parse(dateField) } catch (_: Exception) { null }
                            else -> null
                        }

                        if (date != null) {
                            val cal = Calendar.getInstance()
                            cal.time = date
                            val day = cal.get(Calendar.DAY_OF_MONTH)
                            val weekIndex = ((day - 1) / 7).coerceIn(0, 3)
                            weekSums[weekIndex] += cases
                        }
                    }

                    val weeks = listOf("Week 1", "Week 2", "Week 3", "Week 4")
                    val entries = ArrayList<BarEntry>()
                    for (i in weekSums.indices) entries.add(BarEntry(i.toFloat(), weekSums[i]))

                    if (weekSums.any { it > 0f }) {
                        val ds = BarDataSet(entries, "Weekly Cases in $municipality").apply {
                            color = "#FF8A65".toColorInt()
                            valueTextColor = Color.DKGRAY
                            valueTextSize = 12f
                            barShadowColor = Color.LTGRAY
                            highLightAlpha = 50
                        }

                        val data = BarData(ds).apply { barWidth = 0.6f }

                        barChart.data = data
                        barChart.xAxis.valueFormatter = IndexAxisValueFormatter(weeks)
                        barChart.invalidate()
                        barChart.animateY(1000)
                    } else {
                        barChart.clear()
                        barChart.invalidate()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to fetch bar data", e)
                    barChart.clear()
                    barChart.invalidate()
                }
        }
    }
}
