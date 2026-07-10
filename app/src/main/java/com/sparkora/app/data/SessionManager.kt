package com.sparkora.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "sparkora_session")

data class SessionState(
    val token: String? = null,
    val employeeId: String? = null,
    val employeeName: String = "",
    val email: String = "",
    val companyId: String = "",
    val baseUrl: String = SessionManager.DEFAULT_BASE_URL,
) {
    val isLoggedIn: Boolean get() = !token.isNullOrBlank() && !employeeId.isNullOrBlank()
}

class SessionManager(private val context: Context) {

    private object Keys {
        val TOKEN = stringPreferencesKey("token")
        val EMPLOYEE_ID = stringPreferencesKey("employee_id")
        val EMPLOYEE_NAME = stringPreferencesKey("employee_name")
        val EMAIL = stringPreferencesKey("email")
        val COMPANY_ID = stringPreferencesKey("company_id")
        val BASE_URL = stringPreferencesKey("base_url")
    }

    /** Hot copies for synchronous consumers (OkHttp interceptor, Retrofit builder). */
    @Volatile var cachedToken: String? = null
        private set
    @Volatile var cachedBaseUrl: String = DEFAULT_BASE_URL
        private set

    val state: Flow<SessionState> = context.sessionDataStore.data.map { p ->
        SessionState(
            token = p[Keys.TOKEN],
            employeeId = p[Keys.EMPLOYEE_ID],
            employeeName = p[Keys.EMPLOYEE_NAME] ?: "",
            email = p[Keys.EMAIL] ?: "",
            companyId = p[Keys.COMPANY_ID] ?: "",
            baseUrl = normalizeBaseUrl(p[Keys.BASE_URL]),
        )
    }

    /** Populate the volatile caches once at startup (and return the state). */
    suspend fun load(): SessionState {
        val s = state.first()
        cachedToken = s.token
        cachedBaseUrl = s.baseUrl
        return s
    }

    suspend fun saveLogin(
        token: String,
        employeeId: String,
        employeeName: String,
        email: String,
        companyId: String,
    ) {
        context.sessionDataStore.edit { p ->
            p[Keys.TOKEN] = token
            p[Keys.EMPLOYEE_ID] = employeeId
            p[Keys.EMPLOYEE_NAME] = employeeName
            p[Keys.EMAIL] = email
            p[Keys.COMPANY_ID] = companyId
        }
        cachedToken = token
    }

    suspend fun setBaseUrl(url: String) {
        val normalized = normalizeBaseUrl(url)
        context.sessionDataStore.edit { p -> p[Keys.BASE_URL] = normalized }
        cachedBaseUrl = normalized
    }

    /** Clears credentials but keeps email/companyId/server so the next login is prefilled. */
    suspend fun clear() {
        context.sessionDataStore.edit { p ->
            p.remove(Keys.TOKEN)
            p.remove(Keys.EMPLOYEE_ID)
            p.remove(Keys.EMPLOYEE_NAME)
        }
        cachedToken = null
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.sparkora.co.uk/"

        fun normalizeBaseUrl(raw: String?): String {
            var url = raw?.trim().orEmpty()
            if (url.isBlank()) return DEFAULT_BASE_URL
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
            if (!url.endsWith("/")) url += "/"
            return url
        }
    }
}
