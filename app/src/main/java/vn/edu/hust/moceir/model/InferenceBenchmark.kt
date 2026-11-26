package vn.edu.hust.moceir.model

data class PatchTiming(
    val index: Int,
    val x: Int,
    val y: Int,
    val preprocessingMs: Double,
    val inferenceMs: Double,
    val postprocessingMs: Double,
)

data class InferenceBenchmark(
    val runtime: RuntimeStatus,
    val sessionReused: Boolean,
    val assetCopyMs: Double,
    val modelLoadMs: Double,
    val preprocessingMs: Double,
    val inferenceMs: Double,
    val postprocessingMs: Double,
    val totalMs: Double,
    val patches: List<PatchTiming>,
) {
    val averageInferenceMs: Double get() = if (patches.isEmpty()) 0.0 else inferenceMs / patches.size
}
