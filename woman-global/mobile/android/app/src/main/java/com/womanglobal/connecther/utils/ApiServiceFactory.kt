package com.womanglobal.connecther.utils

import com.womanglobal.connecther.BuildConfig
import com.womanglobal.connecther.services.ApiService
import com.womanglobal.connecther.utils.ServiceBuilder
import com.womanglobal.connecther.utils.mock.MockApiService

object ApiServiceFactory {
    fun createApiService(): ApiService {
        return if (BuildConfig.DEBUG) {
            MockApiService() // Use mock implementation in debug mode
        } else {
            ServiceBuilder.buildService(ApiService::class.java) // Use real implementation in production
        }
    }
}

