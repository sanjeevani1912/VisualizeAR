package com.example.dhruvar.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class UserInitialsTest {
    @Test fun twoWords() = assertEquals("SK", userInitials("Sanjay Kumar"))
    @Test fun manyWordsUsesFirstAndLast() = assertEquals("AS", userInitials("anil kumar singh"))
    @Test fun oneWord() = assertEquals("S", userInitials("sanjay"))
    @Test fun blankFallsBackToUsername() = assertEquals("O", userInitials("  ", "officer1"))
    @Test fun nothingAtAll() = assertEquals("?", userInitials("", ""))
}
