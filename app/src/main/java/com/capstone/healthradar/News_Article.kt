package com.capstone.healthradar

data class NewsArticle(
    val source: NewsSource?,
    val author: String?,
    val title: String?,
    val description: String?,
    val url: String?,
    val urlToImage: String?,
    val publishedAt: String?,
    val content: String?
)

data class NewsSource(
    val id: String?,
    val name: String?
)