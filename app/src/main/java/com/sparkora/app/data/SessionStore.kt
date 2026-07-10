package com.sparkora.app.data

/**
 * Narrow session contract used by the API/repository layer and view models,
 * so tests can substitute an in-memory fake for the DataStore-backed
 * [SessionManager].
 */
interface SessionStore {
    val cachedToken: String?
    val cachedBaseUrl: String

    suspend fun load(): SessionState

    suspend fun saveLogin(
        token: String,
        employeeId: String,
        employeeName: String,
        email: String,
        companyId: String,
    )

    suspend fun setBaseUrl(url: String)

    suspend fun clear()
}
