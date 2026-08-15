package com.nexus.assistant.ai

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import com.nexus.assistant.core.ModelStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Minimum RAM below which we refuse to attempt loading a model, rather than
 * trying and crashing/OOMing mid-generation. This is a conservative floor for
 * a ~1-3B quantized model; NEXUS reports this clearly instead of silently
 * failing.
 */
private const val MIN_REQUIRED_RAM_MB = 3000L
private const val MODEL_DIR = "models"
private const val TAG = "NexusModelManager"

data class ModelInfo(
    val fileName: String,
    val sizeBytes: Long,
    val path: String
)

sealed class ModelLoadError(val userMessage: String) {
    object NoModelFile : ModelLoadError(
        "No local AI model is installed. Import a .task model file in Settings to enable NEXUS's AI features."
    )
    object InsufficientMemory : ModelLoadError(
        "This device doesn't have enough available memory to safely run the local AI model."
    )
    data class LoadFailed(val reason: String) : ModelLoadError(
        "The local AI model failed to load: $reason"
    )
}

/**
 * Owns the local LLM's entire lifecycle. The AI layer (LocalAIEngine) talks
 * only to this class — nothing else in the app touches LlmInference or model
 * files directly.
 *
 * Responsibilities (per spec):
 *  - Loading the model
 *  - Checking model availability
 *  - Starting inference
 *  - Stopping inference
 *  - Reporting model status
 *  - Model import (download is out of scope for on-device code — the user
 *    downloads a .task file via their browser and imports it here, since
 *    NEXUS does not silently fetch multi-GB files over the network)
 *  - Checking memory requirements before attempting a load
 */
class ModelManager(private val context: Context) {

    private val _status = MutableStateFlow(ModelStatus.NOT_LOADED)
    val status: StateFlow<ModelStatus> = _status.asStateFlow()

    private val _lastError = MutableStateFlow<ModelLoadError?>(null)
    val lastError: StateFlow<ModelLoadError?> = _lastError.asStateFlow()

    private var llmInference: LlmInference? = null

    private val modelDir: File
        get() = File(context.filesDir, MODEL_DIR).apply { if (!exists()) mkdirs() }

    /** Returns info about the currently installed model file, if any. */
    fun getInstalledModel(): ModelInfo? {
        val dir = modelDir
        val file = dir.listFiles()?.firstOrNull { it.extension == "task" } ?: return null
        return ModelInfo(fileName = file.name, sizeBytes = file.length(), path = file.absolutePath)
    }

    fun isModelAvailable(): Boolean = getInstalledModel() != null

    /** Reports whether the device likely has enough free memory to load a model. */
    fun checkMemoryRequirements(): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val availMb = memInfo.availMem / (1024 * 1024)
        return availMb >= MIN_REQUIRED_RAM_MB
    }

    /**
     * Copies a user-picked .task model file (from a SAF file picker Uri) into
     * app-private storage. NEXUS never downloads model weights on its own —
     * the user must obtain and select the file themselves, so nothing large
     * or unexpected happens over the network without explicit action.
     */
    suspend fun importModel(sourceUri: Uri): Result<ModelInfo> = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val fileName = resolver.query(sourceUri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                if (nameIndex >= 0) cursor.getString(nameIndex) else null
            } ?: "model.task"

            if (!fileName.endsWith(".task") && !fileName.endsWith(".bin")) {
                return@withContext Result.failure(
                    IllegalArgumentException("Selected file doesn't look like a MediaPipe .task model.")
                )
            }

            // Clear any previously imported model — NEXUS supports one active
            // local model at a time in this phase.
            modelDir.listFiles()?.forEach { it.delete() }

            val destFile = File(modelDir, fileName)
            resolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(IllegalStateException("Could not open selected file."))

            Result.success(ModelInfo(fileName = destFile.name, sizeBytes = destFile.length(), path = destFile.absolutePath))
        } catch (e: Exception) {
            Log.e(TAG, "Model import failed", e)
            Result.failure(e)
        }
    }

    /** Deletes the installed model and unloads it from memory if active. */
    suspend fun deleteModel() = withContext(Dispatchers.IO) {
        stopInference()
        modelDir.listFiles()?.forEach { it.delete() }
        _status.value = ModelStatus.NOT_LOADED
        _lastError.value = null
    }

    /**
     * Loads the model into memory. Safe to call repeatedly; no-ops if already
     * loaded. Never claims READY unless the underlying engine actually
     * initialized successfully.
     */
    suspend fun loadModel(): Result<Unit> = withContext(Dispatchers.Default) {
        if (_status.value == ModelStatus.READY && llmInference != null) {
            return@withContext Result.success(Unit)
        }

        val info = getInstalledModel()
        if (info == null) {
            _status.value = ModelStatus.UNAVAILABLE
            _lastError.value = ModelLoadError.NoModelFile
            return@withContext Result.failure(IllegalStateException(ModelLoadError.NoModelFile.userMessage))
        }

        if (!checkMemoryRequirements()) {
            _status.value = ModelStatus.UNAVAILABLE
            _lastError.value = ModelLoadError.InsufficientMemory
            return@withContext Result.failure(IllegalStateException(ModelLoadError.InsufficientMemory.userMessage))
        }

        _status.value = ModelStatus.LOADING
        _lastError.value = null

        try {
            val options = LlmInferenceOptions.builder()
                .setModelPath(info.path)
                .setMaxTokens(1024)
                .setMaxTopK(40)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            _status.value = ModelStatus.READY
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Model load failed", e)
            llmInference = null
            _status.value = ModelStatus.UNAVAILABLE
            _lastError.value = ModelLoadError.LoadFailed(e.message ?: "unknown error")
            Result.failure(e)
        }
    }

    /** Unloads the model, freeing memory. Call when NEXUS is backgrounded for a long time, or on user request. */
    fun stopInference() {
        llmInference?.close()
        llmInference = null
        if (_status.value == ModelStatus.READY) {
            _status.value = ModelStatus.NOT_LOADED
        }
    }

    /**
     * Runs inference synchronously (call from a background dispatcher).
     * Throws if the model isn't loaded — callers (LocalAIEngine) are expected
     * to check status first and never call this speculatively.
     */
    suspend fun generate(prompt: String): Result<String> = withContext(Dispatchers.Default) {
        val engine = llmInference
        if (engine == null || _status.value != ModelStatus.READY) {
            return@withContext Result.failure(IllegalStateException("Model is not loaded."))
        }
        try {
            val response = engine.generateResponse(prompt)
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed", e)
            Result.failure(e)
        }
    }
}
