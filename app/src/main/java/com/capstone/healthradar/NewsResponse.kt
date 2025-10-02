package com.capstone.healthradar

data class NewsResponse(
    val status: String,
    val totalResults: Int,
    val articles: List<Article>
)
