package com.capstone.healthradar

data class NewsArticle(
    val title: String,
    val description: String,
    val url: String,
    val urlToImage: String // not used, but still included for API compatibility
)