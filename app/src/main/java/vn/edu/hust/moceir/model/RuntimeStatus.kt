package vn.edu.hust.moceir.model

enum class SessionPhase { NOT_LOADED, INITIALIZING, READY, FAILED, CLOSED }

data class RuntimeStatus(
    val phase: SessionPhase = SessionPhase.NOT_LOADED,
    val configuredProviders: String = "Chưa tạo session",
    val version: String = "",
    val sessionId: Int = 0,
    val initializationMs: Double = 0.0,
    val warning: String? = null,
)
