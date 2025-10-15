package com.capstone.healthradar

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

class CaseListAdapter(
    private val context: Context,
    private val caseList: List<CaseItem>
) : BaseAdapter() {

    override fun getCount(): Int = caseList.size
    override fun getItem(position: Int): Any = caseList[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_case_row, parent, false)

        val tvDiseaseName = view.findViewById<TextView>(R.id.tvDiseaseName)
        val tvCases = view.findViewById<TextView>(R.id.tvCases)
        val tvMunicipality = view.findViewById<TextView>(R.id.tvMunicipality)
        val tvDate = view.findViewById<TextView>(R.id.tvDate)

        val item = caseList[position]

        tvDiseaseName.text = item.diseaseName
        tvCases.text = item.caseCount
        tvMunicipality.text = item.municipality
        tvDate.text = if (item.dateReported.length >= 10) item.dateReported.take(10) else item.dateReported

        return view
    }
}
