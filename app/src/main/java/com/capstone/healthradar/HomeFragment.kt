package com.capstone.healthradar

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {

    private lateinit var lineChart: LineChart
    private lateinit var pieChart: PieChart
    private lateinit var spinner: Spinner
    private lateinit var pieChartTitle: TextView

    private val db = FirebaseFirestore.getInstance()
    private val municipalities = listOf("Liloan", "Consolacion", "Mandaue")
    private val TAG = "HomeFragment"
    private val isoFormat =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())

    @SuppressLint("MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // Initialize views
        lineChart = view.findViewById(R.id.lineChart)
        pieChart = view.findViewById(R.id.pieChart)
        spinner = view.findViewById(R.id.municipalitySpinner)
        pieChartTitle = view.findViewById(R.id.pieChartTitle)

        setupCharts()
        setupSpinner()

        return view
    }

    private fun setupCharts() {
        // ----- PIE CHART -----
        pieChart.apply {
            setBackgroundColor(Color.WHITE)
            setUsePercentValues(true)
            isDrawHoleEnabled = true
            holeRadius = 45f
            transparentCircleRadius = 50f
            setEntryLabelColor(Color.DKGRAY)
            setEntryLabelTextSize(12f)
            setCenterTextSize(16f)
            setCenterTextTypeface(Typeface.DEFAULT_BOLD)
            description.isEnabled = false
            legend.isEnabled = true
            setExtraOffsets(10f, 10f, 10f, 10f)
        }

        // ----- LINE CHART -----
        lineChart.apply {
            setBackgroundColor(Color.WHITE)
            axisRight.isEnabled = false
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            setDrawGridBackground(true)
            setGridBackgroundColor(Color.parseColor("#F5F5F5"))

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
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

            animateX(800)
        }
    }

    private fun setupSpinner() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            municipalities
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (!this@HomeFragment::pieChartTitle.isInitialized) return

                val selectedMunicipality = municipalities[position]
                pieChartTitle.text = "$selectedMunicipality Chart"

                loadPieChart(selectedMunicipality)
                loadLineChart(selectedMunicipality)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // ✅ Post to ensure selection happens after layout is ready
        spinner.post {
            spinner.setSelection(0)
        }
    }

    private fun loadPieChart(municipality: String) {
        db.collection("healthradarDB")
            .document("centralizedData")
            .collection("allCases")
            .get()
            .addOnSuccessListener { snapshot ->
                val filtered = snapshot.documents.filter {
                    val dbMunicipality =
                        it.getString("Municipality")?.replace("-", "")?.lowercase() ?: ""
                    dbMunicipality == municipality.replace("-", "").lowercase()
                }

                val diseaseSums = mutableMapOf<String, Float>()
                for (doc in filtered) {
                    val name = doc.getString("DiseaseName") ?: "Unknown"
                    val cases = doc.getString("CaseCount")?.toFloatOrNull() ?: 0f
                    if (cases > 0f) diseaseSums[name] = (diseaseSums[name] ?: 0f) + cases
                }

                if (diseaseSums.isNotEmpty()) {
                    val entries = diseaseSums.map { PieEntry(it.value, it.key) }
                    val ds = PieDataSet(ArrayList(entries), "")
                    ds.colors = listOf(
                        Color.parseColor("#FFB74D"),
                        Color.parseColor("#4DB6AC"),
                        Color.parseColor("#BA68C8"),
                        Color.parseColor("#81C784"),
                        Color.parseColor("#64B5F6")
                    )
                    ds.valueTextColor = Color.DKGRAY
                    ds.valueTextSize = 12f
                    ds.sliceSpace = 2f
                    ds.selectionShift = 5f

                    pieChart.data = PieData(ds)
                    pieChart.centerText = "$municipality\nDisease Cases"
                    pieChart.invalidate()
                    pieChart.animateY(800)
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

    private fun loadLineChart(municipality: String) {
        db.collection("healthradarDB")
            .document("centralizedData")
            .collection("allCases")
            .get()
            .addOnSuccessListener { snapshot ->
                val filtered = snapshot.documents.filter {
                    val dbMunicipality =
                        it.getString("Municipality")?.replace("-", "")?.lowercase() ?: ""
                    dbMunicipality == municipality.replace("-", "").lowercase()
                }

                val weekSums = FloatArray(4) { 0f }
                for (doc in filtered) {
                    val cases = doc.getString("CaseCount")?.toFloatOrNull() ?: 0f
                    val date = when (val dateField = doc.get("DateReported")) {
                        is com.google.firebase.Timestamp -> dateField.toDate()
                        is String -> try {
                            isoFormat.parse(dateField)
                        } catch (e: Exception) {
                            null
                        }
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
                val entries = ArrayList<Entry>()
                for (i in weekSums.indices) entries.add(Entry(i.toFloat(), weekSums[i]))

                if (weekSums.any { it > 0f }) {
                    val ds = LineDataSet(entries, "Weekly cases in $municipality")
                    ds.color = Color.parseColor("#FF8A65")
                    ds.valueTextColor = Color.DKGRAY
                    ds.valueTextSize = 12f
                    ds.setDrawCircles(true)
                    ds.circleRadius = 6f
                    ds.setCircleColor(Color.parseColor("#4DB6AC"))
                    ds.lineWidth = 3f
                    ds.mode = LineDataSet.Mode.CUBIC_BEZIER
                    ds.setDrawFilled(true)
                    ds.fillColor = Color.parseColor("#FFCCBC")
                    ds.fillAlpha = 80

                    lineChart.data = LineData(ds)
                    lineChart.xAxis.valueFormatter = IndexAxisValueFormatter(weeks)
                    lineChart.invalidate()
                    lineChart.animateX(800)
                } else {
                    lineChart.clear()
                    lineChart.invalidate()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to fetch line data", e)
                lineChart.clear()
                lineChart.invalidate()
            }
    }
}
