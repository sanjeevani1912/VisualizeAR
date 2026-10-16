package com.example.dhruvar

import com.example.dhruvar.ui.components.originChipStatusText
import org.junit.Assert.assertEquals
import org.junit.Test

class OriginChipStatusTest {

    @Test
    fun `unstamped origin reads Not set`() {
        assertEquals("Not set", originChipStatusText(stamped = false))
    }

    @Test
    fun `stamped origin reads Set`() {
        assertEquals("Set", originChipStatusText(stamped = true))
    }
}
