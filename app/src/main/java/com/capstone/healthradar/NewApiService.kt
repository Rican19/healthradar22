package com.capstone.healthradar

import retrofit2.http.GET

interface NewsApiService {
    @GET("health-news")
    suspend fun getHealthNews(): NewsResponse
}