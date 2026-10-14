package com.videofilter.app.model

/**
 * 单个文件迁移成功回执（用于 Room 增量比对热更新）
 */
data class MoveSuccessItem(
    val oldPath: String,
    val newPath: String,
    val sizeBytes: Long,
    val lastModified: Long
)

/**
 * 单个文件迁移失败回执
 */
data class MoveFailedItem(
    val oldPath: String,
    val reason: String
)

/**
 * 移动流水线终态报告
 */
data class MoveReport(
    val totalRequested: Int,
    val successList: List<MoveSuccessItem>,
    val failedList: List<MoveFailedItem>,
    val skippedCount: Int
) {
    val isAllSucceeded: Boolean
        get() = failedList.isEmpty() && successList.size + skippedCount == totalRequested
}

/**
 * 流式进度事件
 */
sealed interface MoveProgressEvent {
    data class Validating(val message: String) : MoveProgressEvent

    data class Processing(
        val currentIndex: Int,
        val totalCount: Int,
        val currentFileName: String,
        val processedBytes: Long,
        val totalBytes: Long
    ) : MoveProgressEvent

    data class Completed(val report: MoveReport) : MoveProgressEvent

    data class InsufficientStorage(
        val requiredBytes: Long,
        val availableBytes: Long
    ) : MoveProgressEvent
}
