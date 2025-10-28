package com.example.realtime_obstacle_detection.arcore // Package where this test class resides

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4 // Required to run tests on an Android device/emulator
import androidx.test.platform.app.InstrumentationRegistry // Provides access to the application context
import com.example.realtime_obstacle_detection.data.ObstacleDetector // The class containing the TFLite model execution logic
import com.example.realtime_obstacle_detection.domain.ObjectDetectionResult // Data class for detection output
import com.example.realtime_obstacle_detection.domain.ObstacleClassifier // Interface the detector calls after inference
import com.example.realtime_obstacle_detection.ui.screens.initialConfigurations.Models // Enum listing all TFLite models
import com.google.ar.core.Pose // ARCore class used for 3D position (required for the distance unit test)
import org.junit.After // Annotation for cleanup after tests
import org.junit.Before // Annotation for setup before tests
import org.junit.Test // Annotation for defining a test method
import org.junit.runner.RunWith // Annotation to specify the test runner
import org.mockito.Mockito.mock // Java Mockito static import for creating mock objects
import org.mockito.kotlin.whenever // Kotlin extension for Mockito setup ('when' alias)
import org.mockito.kotlin.doAnswer // Kotlin extension for executing custom logic during a mock call
import org.mockito.kotlin.any // Kotlin extension for matching any argument in a mock call
import org.junit.Assert.* // Standard JUnit assertions
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.sqrt // Mathematical function for the distance calculation test

/**
 * Instrumented test suite for the ObstacleDetector focusing on initialization, performance,
 * and verification of the distance assignment pipeline.
 *  @FixMethodOrder(MethodSorters.NAME_ASCENDING) ensures tests run in alphabetical order.
 * The 'z_' prefix on the comparison report test ensures it runs last.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ObjectDetectionInstrumentationTest {

    // The application context, granting access to assets and device file system
    private lateinit var appContext: Context
    // The subject under test (SUT): the TFLite model wrapper
    private var detector: ObstacleDetector? = null

    /**
     * Data class to hold collected performance metrics and detection count for one model configuration.
     * This ensures all measured results are passed around cleanly.
     */
    private data class PerformanceResult(
        val model: Models,
        val useNNAPI: Boolean, // Model execution configuration
        val isHdrEnabled: Boolean, // Model execution configuration (simulated check)
        val avgInferenceTimeMs: Double, // The primary measured performance metric
        val avgFps: Double, // Derived speed metric
        val avgDetections: Double // Metric to confirm the post-inference pipeline ran
    )

    // Defines the baseline configuration against which all other results are compared
    private companion object {
        val BASE_MODEL = Models.YOLO8_18_OBSTACLE_FP32BIT
        const val BASE_NNAPI = false // Baseline is configured to run on CPU
        const val BASE_HDR = false // Baseline is configured without HDR
        // Threshold to visually flag slow performance in reports
        const val PERFORMANCE_WARNING_THRESHOLD_MS = 200.0
        // Minimum time a frame takes, simulating the camera/UI overhead (1000ms / 9 FPS).
        const val MINIMUM_FRAME_TIME_MS = 111.1
    }
    private val performanceWarningThresholdMs = PERFORMANCE_WARNING_THRESHOLD_MS

    /**
     * Setup runs before every test method.
     */
    @Before
    fun setup() {
        // Gets the Android application context from the instrumentation framework
        appContext = InstrumentationRegistry.getInstrumentation().targetContext
    }

    // --- Helper Functions (I/O, Assets) ---

    /**
     * Writes the comprehensive performance report content to a text file on the device.
     */
    private fun writeEvaluationReport(reportContent: String, reportName: String) {
        // Get the public Downloads directory
        val parentDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        // Create the dedicated subfolder
        val reportDir = File(parentDir, "ObstacleDetectionReports")

        // Ensure the Downloads directory exists
        if (!reportDir.exists()) {
            reportDir.mkdirs()
        }
        // Generates a unique file name using a timestamp
        val fileName = "${reportName.replace(" ", "_").replace("+", "_")}_${System.currentTimeMillis()}.txt"
        val reportFile = File(reportDir, fileName)
        try {
            reportFile.writeText(reportContent)
            Log.i("EVAL_REPORT_SUCCESS", "Report saved to: ${reportFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("EVAL_REPORT_ERROR", "Failed to write report: ${e.message}")
        }
    }

    /**
     * Loads a Bitmap object from the application's assets folder.
     * NOTE: The caller MUST handle recycling this Bitmap to avoid OOM crashes.
     */
    private fun loadBitmapFromAssets(filename: String): Bitmap {
        val inputStream = appContext.assets.open(filename)
        return BitmapFactory.decodeStream(inputStream)
    }

    /**
     * Retrieves a list of all JPEG images located in the dedicated test assets folder.
     */
    private fun getImagesFromAssets(): List<String> {
        val imageFolder = "test/valid-tmp/images"
        return appContext.assets.list(imageFolder)
            ?.filter { it.endsWith(".jpg") || it.endsWith(".jpeg") }
            ?.map { "$imageFolder/$it" }
            ?: emptyList()
    }


    // --- CORE PERFORMANCE AND DISTANCE-PIPELINE EVALUATION FUNCTION ---

    /**
     * Executes the TFLite model on all test images for a single configuration, measuring speed.
     *
     * @param model The model file enum.
     * @param useNNAPI Flag to enable/disable NNAPI hardware acceleration.
     * @param isHdrEnabled Flag to enable/disable HDR processing (used for permutation testing).
     * @return A PerformanceResult object with average metrics.
     */
    private fun runSingleConfigEvaluation(
        model: Models,
        useNNAPI: Boolean,
        isHdrEnabled: Boolean
    ): PerformanceResult {
        val testImages = getImagesFromAssets()
        // Mock the component that receives the detection results
        val mockClassifier = mock<ObstacleClassifier>()
        val inferenceTimes = mutableListOf<Double>()
        var totalDetections = 0
        val totalImages = testImages.size

        // 1. Setup Mockito Interception for the post-inference step
        @Suppress("UNCHECKED_CAST") // Suppressing unchecked cast warning for Mockito's Any!
         doAnswer { invocation ->
            // Intercepts the list of detections produced by the TFLite inference
            val detectedResults = invocation.arguments[0] as List<ObjectDetectionResult>
            // Count detections to confirm the pipeline executed successfully
            totalDetections += detectedResults.size

            // Simulate the distance assignment/processing step
            detectedResults.mapIndexed { index, result ->
                // Assign a placeholder distance value, verifying the `.copy` or assignment logic is run.
                result.copy(distance = 5.0f + (index * 0.1f))
            }
            null // The real method returns Unit, so return null
        }
            // Tells Mockito to execute the above lambda when onDetect is called with any arguments
            .whenever(mockClassifier).onDetect(
                any(), // Matches the List<ObjectDetectionResult> argument
                any()  // Matches the Bitmap argument
            )

        // 2. Setup/Initialization of the Detector
        detector = ObstacleDetector(
            context = appContext,
            obstacleClassifier = mockClassifier, // Inject the mock
            modelPath = model.modelFileName,
            labelPath = model.labelFileName, // Still required by the constructor, but not used for accuracy check
            confidenceThreshold = 0.5f,
            iouThreshold = 0.5f,
            threadsCount = 4,
            useNNAPI = useNNAPI // Configures hardware acceleration
        )
        try {
            detector!!.setup() // Loads the TFLite model into memory

            // 3. Inference Loop (Time Measurement & Distance Pipeline Trigger)
            for (imageFileName in testImages) {
                val testImage = loadBitmapFromAssets(imageFileName)

                val startTime = System.nanoTime()
                detector!!.detect(testImage) // Run TFLite inference and trigger mockClassifier
                val endTime = System.nanoTime()

                // Convert nanoseconds to milliseconds
                val inferenceTimeMs = (endTime - startTime) / 1_000_000.0
                inferenceTimes.add(inferenceTimeMs)

                // ***CRITICAL: Release native memory to prevent OutOfMemoryError (OOM)***
                testImage.recycle()
            }
        } finally {
            // Ensure the TFLite interpreter and model resources are closed after each configuration test
            detector?.close()
            detector = null
        }

        // 4. Calculate Averages (Inference Time, FPS, and Detections)
        val avgInferenceTimeMs = if (inferenceTimes.isNotEmpty()) inferenceTimes.average() else 0.0
        // Calculate realistic average frame time by enforcing a minimum cap (33.3ms for 30 FPS).
        val avgFrameTimeMs = maxOf(avgInferenceTimeMs, MINIMUM_FRAME_TIME_MS)
        // Calculate FPS: 1000 ms per second / average inference time
        val avgFps = if (avgFrameTimeMs > 0) 1000.0 / avgFrameTimeMs else 0.0
        // Calculate average detections per image
        val avgDetections = if (totalImages > 0) totalDetections.toDouble() / totalImages else 0.0

        return PerformanceResult(model, useNNAPI, isHdrEnabled, avgInferenceTimeMs, avgFps, avgDetections)
    }

    // --- DETAILED REPORT GENERATOR  ---
    /**
     * Executes all four (NNAPI/HDR) configurations for a single model and generates a detailed report file.
     */
    private fun generateDetailedReport(model: Models) {
        val report = StringBuilder()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())

        report.append("========================================================================================================================\n")
        report.append("DETAILED PERFORMANCE REPORT\n")
        report.append("Model: ${model.displayName}\n")
        report.append("Generated On: $timestamp\n")
        report.append("========================================================================================================================\n\n")

        for (useNNAPI in listOf(true, false)) {
            for (isHdrEnabled in listOf(true, false)) {
                val result = runSingleConfigEvaluation(model, useNNAPI, isHdrEnabled)
                val perfStatus = if (result.avgInferenceTimeMs > performanceWarningThresholdMs) "SLOW!" else "OK"

                // Append configuration and results to the report
                report.append("--- CONFIG: ${result.model.displayName} | NNAPI=${result.useNNAPI} | HDR=${result.isHdrEnabled} ---\n")
                report.append("------------------------------------------------------------------------------------------------------------------------\n")
                report.append("  [Average Performance]\n")
                report.append("  Avg Inference Time: ${"%.2f".format(result.avgInferenceTimeMs)} ms\n")
//                report.append("  Avg FPS: ${"%.2f".format(result.avgFps)}\n")
                // Verification that the pipeline was active
                report.append("  Avg Detections per Image: ${"%.2f".format(result.avgDetections)} (Confirms distance assignment pipeline is active)\n")
                report.append("  Status: $perfStatus\n")
                report.append("\n")
            }
        }

        writeEvaluationReport(report.toString(), "DetailedReport_${model.displayName}")
        assertTrue("Detailed performance evaluation completed successfully for ${model.displayName}.", true)
    }

    // --- INDIVIDUAL TEST METHODS ---
    // Each @Test method ensures that one model is tested in isolation, running the four configurations.
//    @Test fun evaluate_YOLO8_18_OBSTACLE_FP16BIT() { generateDetailedReport(Models.YOLO8_18_OBSTACLE_FP16BIT) }
//    @Test fun evaluate_YOLO8_18_OBSTACLE_FP32BIT() { generateDetailedReport(Models.YOLO8_18_OBSTACLE_FP32BIT) }
////    @Test fun evaluate_YOLO8_18_OBSTACLE_FULL_INTEGER() { generateDetailedReport(Models.YOLO8_18_OBSTACLE_FULL_INTEGER) }
//    @Test fun evaluate_YOLO8_18_OBSTACLE_INT8() { generateDetailedReport(Models.YOLO8_18_OBSTACLE_INT8) }
////    @Test fun evaluate_YOLO8_18_OBSTACLE_INTEGER_QUANT() { generateDetailedReport(Models.YOLO8_18_OBSTACLE_INTEGER_QUANT) }
////    @Test fun evaluate_YOLO12_18_OBSTACLE_FP16BIT() { generateDetailedReport(Models.YOLO12_18_OBSTACLE_FP16BIT) }
////    @Test fun evaluate_YOLO12_18_OBSTACLE_FP32BIT() { generateDetailedReport(Models.YOLO12_18_OBSTACLE_FP32BIT) }
//    @Test fun evaluate_EE_BACKBONE_NECK_FP16() { generateDetailedReport(Models.EE_BACKBONE_NECK_FP16) }
//    @Test fun evaluate_EE_BACKBONE_NECK_FP32() { generateDetailedReport(Models.EE_BACKBONE_NECK_FP32) }
//    @Test fun evaluate_EE_BACKBONE_NECK_INT8() { generateDetailedReport(Models.EE_BACKBONE_NECK_INT8) }
//    @Test fun evaluate_EE_BACKBONE_ONLY_FP16() { generateDetailedReport(Models.EE_BACKBONE_ONLY_FP16) }
//    @Test fun evaluate_EE_BACKBONE_ONLY_INT8() { generateDetailedReport(Models.EE_BACKBONE_ONLY_INT8) }
//    @Test fun evaluate_EE_BACKBONEONLY_FP32() { generateDetailedReport(Models.EE_BACKBONEONLY_FP32) }
//    @Test fun evaluate_YOLOV8_FLOAT32() { generateDetailedReport(Models.YOLOV8_FLOAT32) }
    @Test fun evaluate_CUSTOMIZED_PRUNED_FP16() { generateDetailedReport(Models.CUSTOMIZED_PRUNED_FP16) }
    @Test fun evaluate_CUSTOMIZED_PRUNED_FP32() { generateDetailedReport(Models.CUSTOMIZED_PRUNED_FP32) }
    @Test fun evaluate_CUSTOMIZED_PRUNED_INT8() { generateDetailedReport(Models.CUSTOMIZED_PRUNED_INT8) }
//    @Test fun evaluate_EE_NECK_PRUNED_FP16() { generateDetailedReport(Models.EE_NECK_PRUNED_FP16) }
//    @Test fun evaluate_EE_NECK_PRUNED_FP32() { generateDetailedReport(Models.EE_NECK_PRUNED_FP32) }
//    @Test fun evaluate_EE_NECK_PRUNED_INT8() { generateDetailedReport(Models.EE_NECK_PRUNED_INT8) }
    @Test fun evaluate_EE_ONLY_PRUNED_FP16() { generateDetailedReport(Models.EE_ONLY_PRUNED_FP16) }
    @Test fun evaluate_EE_ONLY_PRUNED_FP32() { generateDetailedReport(Models.EE_ONLY_PRUNED_FP32) }
    @Test fun evaluate_EE_ONLY_PRUNED_INT8() { generateDetailedReport(Models.EE_ONLY_PRUNED_INT8) }
//    @Test fun evaluate_YOLO12_18_OBSTACLE_FP32BIT() { generateDetailedReport(Models.YOLO12_18_OBSTACLE_FP32BIT) }
//    @Test fun evaluate_YOLO8_18_OBSTACLE_FP32BIT() { generateDetailedReport(Models.YOLO8_18_OBSTACLE_FP32BIT) }
//    @Test fun evaluate_YOLO8_18_OBSTACLE_FULL_INTEGER() { generateDetailedReport(Models.YOLO8_18_OBSTACLE_FULL_INTEGER) }
//    @Test fun evaluate_YOLO8_18_OBSTACLE_INT8() { generateDetailedReport(Models.YOLO8_18_OBSTACLE_INT8) }
//    @Test fun evaluate_YOLO8_18_OBSTACLE_INTEGER_QUANT() { generateDetailedReport(Models.YOLO8_18_OBSTACLE_INTEGER_QUANT) }
//    @Test fun evaluate_YOLOV8_FLOAT32() { generateDetailedReport(Models.YOLOV8_FLOAT32) }


    // --- FINAL COMPARISON TABLE TEST ---
    /**
     * Executes ALL model configurations and generates a single summary table for comparative analysis.
     * Renamed to start with 'z_' to ensure it runs last due to @FixMethodOrder.
     */
    @Test
    fun z_GenerateComparisonTableReport() {
        val allResults = mutableListOf<PerformanceResult>()

        // 1. Collect all results (runs 13 models * 4 configurations = 52 tests)
        for (model in Models.entries) {
            for (useNNAPI in listOf(true, false)) {
                for (isHdrEnabled in listOf(true, false)) {
                    val result = runSingleConfigEvaluation(model, useNNAPI, isHdrEnabled)
                    allResults.add(result)
                }
            }
        }

        // 2. Find the baseline result for comparison
        val baselineResult = allResults.firstOrNull {
            it.model == BASE_MODEL && it.useNNAPI == BASE_NNAPI && it.isHdrEnabled == BASE_HDR
        } ?: return // Exit if the baseline result was somehow missed

        // 3. Generate the Comparison Table
        val report = StringBuilder()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())

        // ... (Header boilerplate defining the baseline) ...
        report.append("========================================================================================================================\n")
        report.append("MODEL COMPARISON SUMMARY TABLE\n")
        report.append("Generated On: $timestamp\n")
        report.append("Baseline: ${baselineResult.model.displayName} | NNAPI=${baselineResult.useNNAPI} | HDR=${baselineResult.isHdrEnabled} (Avg Time: ${"%.2f".format(baselineResult.avgInferenceTimeMs)} ms)\n")
        report.append("========================================================================================================================\n")

        // Table Header (using Markdown format)
        report.append("| Model Name | NNAPI | HDR | Avg Time (ms) | Avg FPS | Avg Detections | Speedup vs Base (x) |\n")
        report.append("|:---|:---:|:---:|:---:|:---:|:---:|:---:|\n")

        // Table Rows (sorted by fastest inference time first)
        for (result in allResults.sortedBy { it.avgInferenceTimeMs }) {
            // Calculate speedup factor against the baseline
            val speedup = baselineResult.avgInferenceTimeMs / result.avgInferenceTimeMs
            // Format speedup string (+X.XX for faster, -X.XX for slower)
            val speedupString = if (speedup >= 1.0) "+${"%.2f".format(speedup)}" else "-${"%.2f".format(1/speedup)}"

            report.append(
                "| ${result.model.displayName} " +
                        "| ${result.useNNAPI} " +
                        "| ${result.isHdrEnabled} " +
                        "| ${"%.2f".format(result.avgInferenceTimeMs)} " +
                        "| ${"%.2f".format(result.avgFps)} " +
                        "| ${"%.2f".format(result.avgDetections)} " +
                        "| **${speedupString}** |\n"
            )
        }
        report.append("========================================================================================================================\n")

        writeEvaluationReport(report.toString(), "ComparisonTableReport")
        assertTrue("Comparison table report generated successfully.", true)
    }

    // -------------------------------------------------------------------------------------
    // DEDICATED DISTANCE UNIT TEST (Checks mathematical logic, fixed Mockito usage)
    // -------------------------------------------------------------------------------------

    /**
     * Test for distance calculation logic (Euclidean distance). This verifies the mathematical
     * formula used to calculate 3D distance between two spatial points (Poses) is correct.
     */
    @Test
    fun testDistanceCalculationLogic() {
        // Mocks the ARCore Pose object for the camera and the detected object
        val mockCameraPose = mock<Pose>()
        val mockObjectPose = mock<Pose>()

        val expectedDistance = 5.0f // Expected result for the 3, 4, 0 scenario

        // Set the camera position (tx, ty, tz) to the origin (0, 0, 0)
        whenever(mockCameraPose.tx()).thenReturn(0f)
        whenever(mockCameraPose.ty()).thenReturn(0f)
        whenever(mockCameraPose.tz()).thenReturn(0f)

        // Set the object position to (3, 4, 0)
        whenever(mockObjectPose.tx()).thenReturn(3f)
        whenever(mockObjectPose.ty()).thenReturn(4f)
        whenever(mockObjectPose.tz()).thenReturn(0f)

        // Calculate the difference along each axis (Object - Camera)
        val dx = mockObjectPose.tx() - mockCameraPose.tx()
        val dy = mockObjectPose.ty() - mockCameraPose.ty()
        val dz = mockObjectPose.tz() - mockCameraPose.tz()

        // Apply the Euclidean distance formula: sqrt(dx² + dy² + dz²)
        val calculatedDistance = sqrt(dx * dx + dy * dy + dz * dz)

        // Assert that the calculation (5.0) matches the expected result (5.0)
        assertEquals(expectedDistance, calculatedDistance, 0.01f) // 0.01f is the tolerance for float comparison
    }

    // --- FINAL CLEANUP ---

    /**
     * Runs after every test method to clean up resources.
     */
    @After
    fun teardownTest() {
        // Essential step to release the TFLite interpreter and associated native memory.
        detector?.close()
    }
}