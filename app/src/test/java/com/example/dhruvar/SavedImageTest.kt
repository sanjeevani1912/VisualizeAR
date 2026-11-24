package com.example.dhruvar

import com.example.dhruvar.ui.ar.capture.ARCaptureManager
import com.example.dhruvar.ui.ar.capture.CaptureMetadata
import com.example.dhruvar.ui.ar.capture.SavedImageSupport
import com.example.dhruvar.ui.ar.capture.savedMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure parts of "save the picture and offer to open it". Opening in a gallery needs a phone. */
class SavedImageTest {

    @Test
    fun fileNameHasPlanNameTimeAndJpgExtension() {
        val name = ARCaptureManager.buildImageFileName(CaptureMetadata(layoutName = "Camp Alpha", timestampMillis = 1700000000000L))
        assertEquals("DHRUV_Camp_Alpha_1700000000000.jpg", name)
    }

    @Test
    fun blankOrSymbolOnlyPlanNameFallsBackToPlan() {
        assertEquals("DHRUV_Plan_5.jpg", ARCaptureManager.buildImageFileName(CaptureMetadata("  ", 5L)))
        assertEquals("DHRUV_Plan_5.jpg", ARCaptureManager.buildImageFileName(CaptureMetadata("***", 5L)))
    }

    @Test
    fun fileNameNeverContainsPathCharacters() {
        val name = ARCaptureManager.buildImageFileName(CaptureMetadata("../../etc/x y", 7L))
        assertFalse(name.contains("/") || name.contains("\\") || name.contains(" "))
        assertTrue(name.endsWith("_7.jpg"))
    }

    @Test
    fun savedMessageNamesFileAndFolder() {
        assertEquals("Saved DHRUV_Plan_5.jpg in Pictures/DhruvAR", savedMessage("DHRUV_Plan_5.jpg"))
    }

    @Test
    fun viewerMimeTypeIsJpeg() {
        assertEquals("image/jpeg", SavedImageSupport.MIME_TYPE)
    }
}
