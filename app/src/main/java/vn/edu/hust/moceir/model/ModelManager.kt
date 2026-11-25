package vn.edu.hust.moceir.model

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OrtLoggingLevel
import ai.onnxruntime.TensorInfo
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import vn.edu.hust.moceir.image.ImageProcessor
import vn.edu.hust.moceir.image.PatchComposer
import vn.edu.hust.moceir.image.PatchGrid

data class PatchProgress(val completed: Int, val total: Int, val elapsedMs: Double)
data class RestorationResult(
    val bitmap: Bitmap,
    val patchCount: Int,
    val overlap: Int,
    val benchmark: InferenceBenchmark,
) {
    val inferenceMs: Double get() = benchmark.inferenceMs
    val totalMs: Double get() = benchmark.totalMs
}

/** Session creation, runs and close operations share one serial worker. */
class ModelManager(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "moce-ir-worker") }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val closing = AtomicBoolean(false)
    private val mutableStatus = MutableStateFlow(RuntimeStatus())
    val status = mutableStatus.asStateFlow()
    private var environment: OrtEnvironment? = null
    private var sessionOptions: OrtSession.SessionOptions? = null
    private var session: OrtSession? = null
    private var inputName: String? = null
    private var outputName: String? = null
    private var copiedModel: File? = null
    private var generation = 0

    private class Counters {
        var copyNs = 0L
        var loadNs = 0L
    }

    suspend fun prepare() = withContext(dispatcher) {
        check(!closing.get()) { "ModelManager đã đóng." }
        ensureActive()
        getOrCreateSession(Counters())
        Unit
    }

    suspend fun restore(
        bitmap: Bitmap,
        overlap: Int = 0,
        onProgress: (PatchProgress) -> Unit = {},
    ): RestorationResult = withContext(dispatcher) {
        check(!closing.get()) { "ModelManager đã đóng." }
        ensureActive()
        val startNs = now()
        val counters = Counters()
        val sessionReused = session != null
        val grid = PatchGrid(bitmap.width, bitmap.height, overlap)
        onProgress(PatchProgress(0, grid.total, 0.0))
        getOrCreateSession(counters)
        ensureActive()
        var preprocessingNs = 0L
        var inferenceNs = 0L
        var postprocessingNs = 0L
        val patchTimings = ArrayList<PatchTiming>(grid.total)
        var phaseStart = now()
        val inputBuffer = ImageProcessor.allocateInputBuffer()
        val pixels = IntArray(ImageProcessor.PIXEL_COUNT)
        preprocessingNs += now() - phaseStart
        phaseStart = now()
        PatchComposer(grid).use { composer ->
            postprocessingNs += now() - phaseStart
            phaseStart = now()
            OnnxTensor.createTensor(checkNotNull(environment), inputBuffer, ImageProcessor.tensorShape()).use { input ->
                preprocessingNs += now() - phaseStart
                val inputs = mapOf(checkNotNull(inputName) to input)
                for (row in 0 until grid.rows) for (column in 0 until grid.columns) {
                    ensureActive()
                    check(!closing.get()) { "ModelManager đã đóng." }
                    val patch = grid.patch(column, row)
                    phaseStart = now()
                    ImageProcessor.readPatch(bitmap, patch, inputBuffer, pixels)
                    val preNs = now() - phaseStart
                    preprocessingNs += preNs
                    val runStart = now()
                    val results = checkNotNull(session).run(inputs)
                    val runNs = now() - runStart
                    inferenceNs += runNs
                    phaseStart = now()
                    results.use {
                        ensureActive()
                        val output = results.get(checkNotNull(outputName)).orElseThrow {
                            IllegalStateException("Không tìm thấy output của model.")
                        } as? OnnxTensor ?: error("Output không phải OnnxTensor.")
                        validateTensor("Output thực tế", output.info)
                        composer.add(patch, output.floatBuffer)
                    }
                    val postNs = now() - phaseStart
                    postprocessingNs += postNs
                    val index = patchTimings.size + 1
                    patchTimings += PatchTiming(index, patch.x, patch.y, preNs.ms(), runNs.ms(),
                        postNs.ms())
                    onProgress(PatchProgress(index, grid.total, (now() - startNs).ms()))
                }
            }
            ensureActive()
            phaseStart = now()
            val image = composer.finish()
            postprocessingNs += now() - phaseStart
            val benchmark = InferenceBenchmark(
                mutableStatus.value, sessionReused, counters.copyNs.ms(), counters.loadNs.ms(),
                preprocessingNs.ms(), inferenceNs.ms(), postprocessingNs.ms(),
                (now() - startNs).ms(), patchTimings.toList(),
            )
            Log.i(TAG, "BENCHMARK provider=${benchmark.runtime.configuredProviders} " +
                "size=${bitmap.width}x${bitmap.height} overlap=$overlap patches=${grid.total} reused=$sessionReused " +
                "copy_ms=${fmt(benchmark.assetCopyMs)} load_ms=${fmt(benchmark.modelLoadMs)} " +
                "pre_ms=${fmt(benchmark.preprocessingMs)} inference_ms=${fmt(benchmark.inferenceMs)} " +
                "post_ms=${fmt(benchmark.postprocessingMs)} " +
                "total_ms=${fmt(benchmark.totalMs)} avg_patch_ms=${fmt(benchmark.averageInferenceMs)}")
            RestorationResult(image, grid.total, overlap, benchmark)
        }
    }

    private fun getOrCreateSession(counters: Counters): OrtSession {
        session?.let { return it }
        mutableStatus.value = mutableStatus.value.copy(phase = SessionPhase.INITIALIZING)
        try {
            if (copiedModel == null) {
                val start = now()
                try { copiedModel = copyModelAssets() } finally { counters.copyNs += now() - start }
            }
            val env = OrtEnvironment.getEnvironment()
            environment = env
            mutableStatus.value = mutableStatus.value.copy(version = env.version)
            return createSession(counters)
        } catch (error: Throwable) {
            mutableStatus.value = mutableStatus.value.copy(phase = SessionPhase.FAILED,
                warning = "Không tạo được session: ${error.message}")
            throw error
        }
    }

    private fun createSession(counters: Counters): OrtSession {
        val start = now()
        val env = checkNotNull(environment)
        val options = OrtSession.SessionOptions()
        var candidate: OrtSession? = null
        try {
            options.setIntraOpNumThreads(minOf(4, Runtime.getRuntime().availableProcessors()))
            options.setInterOpNumThreads(1)
            options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            options.setMemoryPatternOptimization(true)
            options.setCPUArenaAllocator(true)
            options.setLoggerId("MoceIR-CPU-${generation + 1}")
            options.setSessionLogLevel(OrtLoggingLevel.ORT_LOGGING_LEVEL_WARNING)
            options.addCPU(true)
            // Path load preserves external data resolution relative to the original graph.
            val created = env.createSession(checkNotNull(copiedModel).absolutePath, options)
            candidate = created
            require(created.inputInfo.size == 1 && created.outputInfo.size == 1) {
                "Cần model có đúng một input và một output."
            }
            val input = created.inputInfo.entries.single()
            val output = created.outputInfo.entries.single()
            validateTensor("Input", input.value.info as? TensorInfo)
            validateTensor("Output", output.value.info as? TensorInfo)
            inputName = input.key
            outputName = output.key
            sessionOptions = options
            session = created
            generation++
            val initMs = (now() - start).ms()
            mutableStatus.value = mutableStatus.value.copy(
                phase = SessionPhase.READY, configuredProviders = "CPUExecutionProvider",
                sessionId = generation, initializationMs = initMs, warning = null,
            )
            Log.i(TAG, "SESSION_READY id=$generation provider=CPUExecutionProvider load_ms=${fmt(initMs)}")
            return created
        } catch (error: Throwable) {
            try { candidate?.close() } finally { options.close() }
            throw error
        } finally {
            counters.loadNs += now() - start
        }
    }

    private fun closeSession() {
        val active = session
        session = null
        try {
            if (active != null) {
                active.close()
                Log.i(TAG, "SESSION_CLOSED id=$generation")
            }
        } finally {
            sessionOptions?.close()
            sessionOptions = null
        }
    }

    private fun validateTensor(label: String, info: TensorInfo?) {
        require(info != null && info.type == OnnxJavaType.FLOAT &&
            info.shape.contentEquals(ImageProcessor.tensorShape())) {
            "$label phải là Float32 [1,3,256,256], nhận được $info."
        }
    }

    /** Stream both unchanged files. Temp + rename avoids using an interrupted copy. */
    private fun copyModelAssets(): File {
        val directory = File(appContext.filesDir, "models")
        check(directory.isDirectory || directory.mkdirs()) { "Không tạo được ${directory.absolutePath}" }
        // Copy once per manager so an updated APK cannot reuse old model weights.
        // No whole-model ByteArray is ever allocated.
        for (name in listOf(DATA_NAME, MODEL_NAME)) {
            val destination = File(directory, name)
            val temporary = File(directory, "$name.tmp")
            try {
                appContext.assets.open("models/$name").use { input ->
                    temporary.outputStream().use { output ->
                        check(input.copyTo(output, 64 * 1024) > 0) { "File model rỗng: $name" }
                        output.fd.sync()
                    }
                }
                check(temporary.renameTo(destination)) { "Không thể ghi file model: $name" }
            } catch (error: Throwable) {
                temporary.delete()
                throw error
            }
        }
        return File(directory, MODEL_NAME)
    }

    /** Nonblocking. Cleanup waits behind any native run already in progress. Idempotent. */
    override fun close() {
        if (!closing.compareAndSet(false, true)) return
        executor.execute {
            try {
                closeSession()
                environment = null // ORT owns the process singleton; close() on it is a no-op.
                mutableStatus.value = mutableStatus.value.copy(phase = SessionPhase.CLOSED)
            } catch (error: Exception) {
                Log.e(TAG, "Could not close ONNX session", error)
            } finally {
                dispatcher.close()
            }
        }
    }

    private fun now(): Long = SystemClock.elapsedRealtimeNanos()
    private fun Long.ms(): Double = this / 1_000_000.0
    private fun fmt(value: Double): String = String.format(Locale.US, "%.3f", value)

    companion object {
        private const val TAG = "MoceIR"
        private const val MODEL_NAME = "moce_ir_s_aio5.onnx"
        private const val DATA_NAME = "moce_ir_s_aio5.onnx.data"
    }
}
