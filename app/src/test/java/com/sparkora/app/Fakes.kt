package com.sparkora.app

import com.sparkora.app.data.SessionManager
import com.sparkora.app.data.SessionState
import com.sparkora.app.data.SessionStore
import com.sparkora.app.location.GeoPoint
import com.sparkora.app.location.LocationProvider

/** In-memory session for JVM tests; starts signed in as emp_1 unless told otherwise. */
class FakeSessionStore(
    baseUrl: String,
    signedIn: Boolean = true,
) : SessionStore {

    var state = SessionState(
        token = if (signedIn) "test-token" else null,
        employeeId = if (signedIn) "emp_1" else null,
        employeeName = if (signedIn) "Emma Fields" else "",
        email = "emma@sparkleprocleaning.co.uk",
        companyId = "cmp_1",
        baseUrl = SessionManager.normalizeBaseUrl(baseUrl),
    )
        private set

    @Volatile override var cachedToken: String? = state.token
    @Volatile override var cachedBaseUrl: String = state.baseUrl

    var clearCount = 0
        private set
    var lastSavedLogin: SessionState? = null
        private set

    override suspend fun load(): SessionState = state

    override suspend fun saveLogin(
        token: String,
        employeeId: String,
        employeeName: String,
        email: String,
        companyId: String,
    ) {
        state = state.copy(
            token = token,
            employeeId = employeeId,
            employeeName = employeeName,
            email = email,
            companyId = companyId,
        )
        lastSavedLogin = state
        cachedToken = token
    }

    override suspend fun setBaseUrl(url: String) {
        val normalized = SessionManager.normalizeBaseUrl(url)
        state = state.copy(baseUrl = normalized)
        cachedBaseUrl = normalized
    }

    override suspend fun clear() {
        clearCount++
        state = state.copy(token = null, employeeId = null, employeeName = "")
        cachedToken = null
    }
}

class FakeLocationProvider(
    var point: GeoPoint? = GeoPoint(53.4808, -2.2426),
    var permitted: Boolean = true,
) : LocationProvider {
    override fun hasPermission(): Boolean = permitted
    override suspend fun currentLocation(): GeoPoint? = if (permitted) point else null
}
