package com.sparkora.app

import com.sparkora.app.ui.money
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {

    @Test
    fun `money renders GBP with grouping and two decimals`() {
        assertEquals("£1,234.50", money(1234.5))
        assertEquals("£0.00", money(0.0))
        assertEquals("£1,906.25", money(1906.25))
        assertEquals("—", money(null))
    }
}
