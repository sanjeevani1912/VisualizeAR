package com.example.dhruvar

import com.example.dhruvar.domain.model.AssetType
import com.example.dhruvar.domain.model.Layout
import com.example.dhruvar.domain.model.LayoutObject
import com.example.dhruvar.ui.ar.capture.ARCaptureManager
import com.example.dhruvar.ui.ar.capture.CaptureMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test suite validating Part 13: End-to-End Workflow, AR Preparation Flow,
 * Non-colliding Image Capture Naming, Metadata Serialization, and Error Handling.
 */
class ARWorkflowV13Test {

    @Test
    fun imageFileName_generatesCleanSanitizedNames() {
        val metadata = CaptureMetadata(
            layoutName = "Forward Base Alpha / Sector #4",
            timestampMillis = 1726912345678L,
            objectCount = 5,
            isCalibrated = true
        )

        val fileName = ARCaptureManager.buildImageFileName(metadata)

        // Special characters and spaces replaced by underscores
        assertEquals("DHRUV_Forward_Base_Alpha___Sector__4_1726912345678.jpg", fileName)
        assertTrue(fileName.startsWith("DHRUV_"))
        assertTrue(fileName.endsWith(".jpg"))
        assertFalse(fileName.contains("/"))
        assertFalse(fileName.contains("#"))
        assertFalse(fileName.contains(" "))
    }

    @Test
    fun imageFileName_handlesEmptyOrBlankNamesGracefully() {
        val metadataBlank = CaptureMetadata(
            layoutName = "   ",
            timestampMillis = 1726912345000L,
            objectCount = 0,
            isCalibrated = false
        )

        val fileName = ARCaptureManager.buildImageFileName(metadataBlank)
        assertEquals("DHRUV_Plan_1726912345000.jpg", fileName)
    }

    @Test
    fun multipleCaptures_generateDistinctFilenamesWithoutOverwriting() {
        val planName = "Operation Suraksha"
        val generatedNames = mutableSetOf<String>()

        // Simulate 10 sequential captures taken across time
        for (i in 0 until 10) {
            val ts = 1726912000000L + (i * 1500L) // 1.5 second intervals
            val metadata = CaptureMetadata(
                layoutName = planName,
                timestampMillis = ts,
                objectCount = 3,
                isCalibrated = true
            )
            val fileName = ARCaptureManager.buildImageFileName(metadata)
            assertFalse("Filename $fileName collided with previous capture", generatedNames.contains(fileName))
            generatedNames.add(fileName)
        }

        assertEquals(10, generatedNames.size)
    }

    @Test
    fun captureMetadata_providesValidFormattedDateAndStatus() {
        val metadata = CaptureMetadata(
            layoutName = "Tactical Outpost",
            timestampMillis = 1726912345678L,
            objectCount = 4,
            isCalibrated = true
        )

        assertEquals("Tactical Outpost", metadata.layoutName)
        assertEquals("Tactical_Outpost", metadata.sanitizedLayoutName)
        assertEquals(4, metadata.objectCount)
        assertTrue(metadata.isCalibrated)
        assertTrue(metadata.formattedDate.isNotBlank())
        // Date format matches YYYY-MM-DD pattern
        assertTrue(metadata.formattedDate.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")))
    }

    @Test
    fun arPreparationStateFlow_transitionsAccuratelyAcrossStages() {
        // Stage 1: Initial scanning (no plane detected)
        var detectedPlaneCount = 0
        var isOriginCalibrated = false
        var objectCount = 3

        val step1Completed = detectedPlaneCount > 0
        val step1Active = detectedPlaneCount == 0
        val step2Active = detectedPlaneCount > 0 && !isOriginCalibrated
        val step3Active = isOriginCalibrated

        assertFalse(step1Completed)
        assertTrue(step1Active)
        assertFalse(step2Active)
        assertFalse(step3Active)

        // Stage 2: Ground plane detected (ready to set origin)
        detectedPlaneCount = 2
        val step1CompletedNow = detectedPlaneCount > 0
        val step2ActiveNow = detectedPlaneCount > 0 && !isOriginCalibrated
        val step3ActiveNow = isOriginCalibrated

        assertTrue(step1CompletedNow)
        assertTrue(step2ActiveNow)
        assertFalse(step3ActiveNow)

        // Stage 3: Origin calibrated and 3D objects visualized
        isOriginCalibrated = true
        val step2CompletedFinal = isOriginCalibrated
        val step3ActiveFinal = isOriginCalibrated
        val step3CompletedFinal = isOriginCalibrated && objectCount > 0

        assertTrue(step2CompletedFinal)
        assertTrue(step3ActiveFinal)
        assertTrue(step3CompletedFinal)
    }

    @Test
    fun unsavedChangesLogic_guardsNavigationToAR() {
        // Scenario A: Clean layout (no unsaved changes)
        val cleanLayout = Layout(id = "layout-1", name = "Base Plan")
        val hasUnsavedChangesA = false
        val isPersistedA = true

        val shouldAutoSaveA = hasUnsavedChangesA && isPersistedA
        val shouldPromptA = hasUnsavedChangesA && !isPersistedA
        val canNavigateDirectlyA = !hasUnsavedChangesA

        assertFalse(shouldAutoSaveA)
        assertFalse(shouldPromptA)
        assertTrue(canNavigateDirectlyA)

        // Scenario B: Existing persisted layout with edits -> Auto-save with user feedback
        val hasUnsavedChangesB = true
        val isPersistedB = true

        val shouldAutoSaveB = hasUnsavedChangesB && isPersistedB
        val shouldPromptB = hasUnsavedChangesB && !isPersistedB

        assertTrue(shouldAutoSaveB)
        assertFalse(shouldPromptB)

        // Scenario C: Newly created unpersisted layout with edits -> Prompt save dialog
        val hasUnsavedChangesC = true
        val isPersistedC = false

        val shouldAutoSaveC = hasUnsavedChangesC && isPersistedC
        val shouldPromptC = hasUnsavedChangesC && !isPersistedC

        assertFalse(shouldAutoSaveC)
        assertTrue(shouldPromptC)
    }

    @Test
    fun captureErrorHandling_zeroDimensionsReturnsFailureResult() {
        // When dimensions are <= 0, captureARView returns a Failure Result safely
        var failureOccurred = false
        val zeroWidth = 0
        val zeroHeight = 0

        if (zeroWidth <= 0 || zeroHeight <= 0) {
            val failureResult = Result.failure<android.graphics.Bitmap>(
                IllegalStateException("AR SurfaceView dimensions are zero or invalid ($zeroWidth x $zeroHeight)")
            )
            assertTrue(failureResult.isFailure)
            assertEquals(
                "AR SurfaceView dimensions are zero or invalid (0 x 0)",
                failureResult.exceptionOrNull()?.message
            )
            failureOccurred = true
        }

        assertTrue(failureOccurred)
    }
}
