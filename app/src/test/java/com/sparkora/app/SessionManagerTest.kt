package com.sparkora.app

import com.sparkora.app.data.SessionManager
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionManagerTest {

    @Test
    fun `blank or missing server URLs fall back to production`() {
        assertEquals(SessionManager.DEFAULT_BASE_URL, SessionManager.normalizeBaseUrl(null))
        assertEquals(SessionManager.DEFAULT_BASE_URL, SessionManager.normalizeBaseUrl(""))
        assertEquals(SessionManager.DEFAULT_BASE_URL, SessionManager.normalizeBaseUrl("   "))
    }

    @Test
    fun `scheme and trailing slash are added when missing`() {
        assertEquals("https://api.sparkora.co.uk/", SessionManager.normalizeBaseUrl("api.sparkora.co.uk"))
        assertEquals("https://staging.example.com/", SessionManager.normalizeBaseUrl("https://staging.example.com"))
        assertEquals("http://10.0.2.2:3001/", SessionManager.normalizeBaseUrl("http://10.0.2.2:3001"))
    }

    @Test
    fun `well-formed URLs pass through untouched`() {
        assertEquals("https://api.sparkora.co.uk/", SessionManager.normalizeBaseUrl("https://api.sparkora.co.uk/"))
    }
}
