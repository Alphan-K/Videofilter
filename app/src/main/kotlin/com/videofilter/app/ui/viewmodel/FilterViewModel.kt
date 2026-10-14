package com.videofilter.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.videofilter.app.data.AppDatabase
import com.videofilter.app.data.ScanRecordEntity
import com.videofilter.app.data.pref.AutoMovePreferences
import com.videofilter.app.data.pref.BitrateDefaults
import com.videofilter.app.data.pref.FilterPreferences
import com.videofilter.app.data.pref.FolderPreferences
import com.videofilter.app.data.toModel
import com.videofilter.app.data.toWhitelistEntity
import com.videofilter.app.model.MoveProgressEvent
import com.videofilter.app.model.VideoFilterState
import com.videofilter.app.model.VideoItem
import com.videofilter.app.scanner.FileMovementEngine
import com.videofilter.app.scanner.ScanEvent
import com.videofilter.app.scanner.ScanMode
import com.videofilter.app.scanner.ScanPhase
import com.videofilter.app.scanner.ScanScope
import com.videofilter.app.scanner.ScanStats
import com.videofilter.app.scanner.VideoFolder
import com.videofilter.app.scanner.VideoScannerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** 白名单条目 + 源文件是否仍然存在（用于提示用户该记录已成孤儿） */
data class WhitelistEntry(
    val item: VideoItem,
    val existsOnDisk: Boolean
)

/**
 * 扫描结束后自动弹出的移动询问。
 *
 * [candidates] 是本次扫描目录中「通过当前筛选且不在白名单」的视频，
 * 即用户真正要归档的那一批。
 */
data class AutoMovePrompt(
    val folder: VideoFolder,
    val candidates: List<VideoItem>,
    val targetPath: String
)

sealed interface ScanUiState {
    data object Idle : ScanUiState
    data class Scanning(
        val scope: ScanScope,
        val phase: ScanPhase,
        val processedFiles: Int,
        val currentPath: String
    ) : ScanUiState

    data class Success(val stats: ScanStats) : ScanUiState
    data class Error(val message: String) : ScanUiState
}

@OptIn(FlowPreview::class)
class FilterViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.get(application)
    private val videoDao = database.videoDao()
    private val whitelistDao = database.whitelistDao()
    private val scanRecordDao = database.scanRecordDao()

    private val scannerEngine = VideoScannerEngine(application.applicationContext, videoDao)
    private val fileMovementEngine = FileMovementEngine(application.applicationContext)
    private val folderPreferences = FolderPreferences(application.applicationContext)
    private val filterPreferences = FilterPreferences(application.applicationContext)
    private val autoMovePreferences = AutoMovePreferences(application.applicationContext)

    private var scanJob: Job? = null
    private var moveJob: Job? = null

    /* ==================== 数据源 ==================== */

    /** 磁盘上实际收录的全部视频（含白名单），按所属归档目录分好组 */
    private val videosByFolder: StateFlow<Map<VideoFolder, List<VideoItem>>> =
        videoDao.observeAll()
            .map { entities ->
                entities.map { it.toModel() }.groupBy { VideoFolder.ofPath(it.path) }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 字典序稳定的目录列表 */
    val folders: List<VideoFolder> = VideoFolder.entries

    /** 每个目录的记录数（用于顶部入口的角标） */
    val folderCounts: StateFlow<Map<VideoFolder, Int>> = videosByFolder
        .map { byFolder -> byFolder.mapValues { it.value.size } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 每个目录的上次扫描时间 */
    val folderScanTimes: StateFlow<Map<VideoFolder, Long>> = scanRecordDao.observeAll()
        .map { records ->
            records.mapNotNull { record ->
                VideoFolder.fromNameOrNull(record.folder)?.let { it to record.lastScannedAt }
            }.toMap()
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 白名单路径集合（用于把已审核视频从主列表剔除） */
    private val whitelistedPaths: StateFlow<Set<String>> = whitelistDao.observePaths()
        .map { it.toSet() }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** 当前正在浏览的目录 */
    private val _activeFolder = MutableStateFlow(VideoFolder.DCIM)
    val activeFolder: StateFlow<VideoFolder> = _activeFolder.asStateFlow()

    /**
     * 主列表数据源：**只展示当前浏览目录下的视频**。
     * 其他目录的历史记录不再混入列表，只以顶部入口的角标形式存在。
     */
    val rawVideos: StateFlow<List<VideoItem>> = combine(
        videosByFolder,
        whitelistedPaths,
        _activeFolder
    ) { byFolder, whitelist, folder ->
        val list = byFolder[folder].orEmpty()
        if (whitelist.isEmpty()) list else list.filter { it.path !in whitelist }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 白名单视频（独立持久表，即使源文件已被删除也会保留） */
    val whitelistVideos: StateFlow<List<WhitelistEntry>> = whitelistDao.observeAll()
        .map { entities ->
            entities.map { entity ->
                WhitelistEntry(
                    item = entity.toModel(),
                    // 存在性检查是磁盘 IO，必须留在 IO 线程，不能放到 Compose 里做
                    existsOnDisk = File(entity.path).exists()
                )
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 全部目录的记录总数（顶栏统计用） */
    val totalScannedCount: StateFlow<Int> = videosByFolder
        .map { byFolder -> byFolder.values.sumOf { it.size } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * 当前浏览目录的数据是否「可能不是最新」：
     * 其上次扫描时间早于所有目录中最新的扫描时间，即说明之后又扫过别的目录。
     */
    val isActiveFolderStale: StateFlow<Boolean> = combine(
        folderScanTimes,
        _activeFolder
    ) { times, folder ->
        val mine = times[folder] ?: return@combine false
        val newest = times.values.maxOrNull() ?: return@combine false
        mine < newest
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /* ==================== 扫描状态 ==================== */

    private val _scanState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scanState: StateFlow<ScanUiState> = _scanState.asStateFlow()

    /* ==================== 筛选状态 ==================== */

    private val _filterState = MutableStateFlow(VideoFilterState())
    val filterState: StateFlow<VideoFilterState> = _filterState.asStateFlow()

    /**
     * 记忆的码率默认区间（首次运行为 30000 / 无上限）。
     * 界面上以灰色占位文字呈现，输入框为空时即以此为准参与筛选。
     */
    val bitrateDefaults: StateFlow<BitrateDefaults> = filterPreferences.bitrateDefaults
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BitrateDefaults(FilterPreferences.DEFAULT_MIN_KBPS, null)
        )

    /**
     * 生效后的码率区间是否非法。
     *
     * 必须基于「套用记忆默认值之后」的状态计算：
     * 否则会出现「界面没报错、筛选却按非法区间放行全部结果」的不一致。
     */
    val isBitrateRangeInvalid: StateFlow<Boolean> = combine(
        _filterState.debounce(300L),
        bitrateDefaults
    ) { filter, defaults ->
        filter.withBitrateDefaults(defaults.minKbps, defaults.maxKbps).isBitrateRangeInvalid
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val filteredVideos: StateFlow<List<VideoItem>> = combine(
        rawVideos,
        _filterState.debounce(300L),
        bitrateDefaults
    ) { rawList, filter, defaults ->
        if (rawList.isEmpty()) {
            emptyList()
        } else {
            // 派生一次即可，不要放进 filter 内部逐条重建（每项都会分配一个 State 对象）
            val effective = filter.withBitrateDefaults(defaults.minKbps, defaults.maxKbps)
            rawList.filter { effective.matches(it) }
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /* ==================== 多选状态 ==================== */

    private val _selectedPaths = MutableStateFlow<Set<String>>(emptySet())
    val selectedPaths: StateFlow<Set<String>> = _selectedPaths.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    /* ==================== 白名单面板 ==================== */

    private val _isWhitelistVisible = MutableStateFlow(false)
    val isWhitelistVisible: StateFlow<Boolean> = _isWhitelistVisible.asStateFlow()

    /* ==================== 扫描后自动询问移动 ==================== */

    /** 已开启「扫描后自动询问」的目录集合（出厂默认仅 DCIM 开启） */
    val autoMoveFolders: StateFlow<Set<VideoFolder>> = autoMovePreferences.enabledFolders
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DEFAULT_AUTO_MOVE_FOLDERS
        )

    /** 待用户确认的移动询问；非 null 时展示询问弹窗 */
    private val _autoMovePrompt = MutableStateFlow<AutoMovePrompt?>(null)
    val autoMovePrompt: StateFlow<AutoMovePrompt?> = _autoMovePrompt.asStateFlow()

    private val _isAutoMoveSettingsVisible = MutableStateFlow(false)
    val isAutoMoveSettingsVisible: StateFlow<Boolean> = _isAutoMoveSettingsVisible.asStateFlow()

    /* ==================== 目标目录 ==================== */

    val targetFolderPath: StateFlow<String> = folderPreferences.targetFolderPath
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FolderPreferences.defaultTargetDirectory.absolutePath
        )

    val storageRootPath: String = FolderPreferences.storageRootPath

    /* ==================== 移动任务 ==================== */

    private val _moveProgressEvent = MutableStateFlow<MoveProgressEvent?>(null)
    val moveProgressEvent: StateFlow<MoveProgressEvent?> = _moveProgressEvent.asStateFlow()

    /* ==================== 首启/升级后的目录自愈 + 码率记忆 ==================== */
    init {
        viewModelScope.launch {
            // 拿到首个非空快照后：若当前浏览目录为空、而别的目录有数据，
            // 自动切到记录最多的那个，避免用户误以为数据丢了。
            val byFolder = videosByFolder.filter { it.isNotEmpty() }.first()
            if ((byFolder[_activeFolder.value]?.size ?: 0) == 0) {
                byFolder.maxByOrNull { it.value.size }?.let { _activeFolder.value = it.key }
            }
        }

        viewModelScope.launch {
            // 把用户填写的码率记忆为下次的默认值。
            // 只在输入非空时才写入，避免「清空输入框」把历史记忆抹掉。
            _filterState
                .map { it.minBitrateInput.toLongOrNull() to it.maxBitrateInput.toLongOrNull() }
                .debounce(BITRATE_REMEMBER_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { (min, max) -> filterPreferences.rememberBitrate(min, max) }
        }
    }

    /* ==================== 目录切换 ==================== */

    fun setActiveFolder(folder: VideoFolder) {
        _activeFolder.value = folder
    }

    /* ==================== 扫描 ==================== */

    /** 扫描当前浏览的目录（「其他」目录只能通过全盘刷新） */
    fun scanActiveFolder(mode: ScanMode = ScanMode.INCREMENTAL) {
        startScan(mode, ScanScope.forFolder(_activeFolder.value))
    }

    /** 全盘扫描 */
    fun scanWholeDisk(mode: ScanMode = ScanMode.INCREMENTAL) {
        startScan(mode, ScanScope.ALL)
    }

    fun startScan(mode: ScanMode = ScanMode.INCREMENTAL, scope: ScanScope = ScanScope.ALL) {
        if (_scanState.value is ScanUiState.Scanning) return

        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            scannerEngine.scan(mode, scope).collect { event ->
                when (event) {
                    is ScanEvent.Progress -> {
                        _scanState.value = ScanUiState.Scanning(
                            scope = scope,
                            phase = event.phase,
                            processedFiles = event.processedFiles,
                            currentPath = event.currentPath
                        )
                    }

                    is ScanEvent.Completed -> {
                        _scanState.value = ScanUiState.Success(event.stats)
                        onScanCompleted(event.stats)
                    }

                    is ScanEvent.Failed -> {
                        _scanState.value = ScanUiState.Error(
                            event.cause.localizedMessage ?: "扫描过程发生未知异常"
                        )
                    }
                }
            }
        }
    }

    /** 落库本次扫描覆盖到的各目录的扫描时间，并把浏览目录切到被扫到的目标 */
    private suspend fun onScanCompleted(stats: ScanStats) {
        val targets = stats.scope.targetFolders
        val now = System.currentTimeMillis()
        scanRecordDao.upsertAll(targets.map { ScanRecordEntity(folder = it.name, lastScannedAt = now) })

        val current = _activeFolder.value
        if (current !in targets) {
            targets.firstOrNull { it.isScannable }?.let { _activeFolder.value = it }
        }

        // 自动询问属于「锦上添花」：即使它因为偏好在磁盘上损坏等原因失败，
        // 也绝不能让已经成功的扫描任务崩掉整个应用。
        runCatching { maybePromptAutoMove(targets) }
    }

    /**
     * 扫描结束后判断是否需要弹出移动询问。
     *
     * 判定顺序：
     * 1. 本次扫描覆盖到的目录中，有哪些开启了开关（按枚举顺序，取第一个有候选的）；
     * 2. 候选 = 该目录下「命中当前筛选」且「不在白名单」的视频；
     * 3. 候选为空则不打扰用户。
     *
     * 码率区间非法时直接跳过：此时筛选处于「放行全部」的兜底态，
     * 若仍按它挑候选，可能把用户本不想动的视频也纳入待移动清单。
     */
    private suspend fun maybePromptAutoMove(targets: List<VideoFolder>) {
        // 直接读偏好源而非 StateFlow：避免 UI 尚未订阅时读到初始占位值
        val enabled = autoMovePreferences.enabledFolders.first()
        if (enabled.isEmpty()) return

        val ordered = targets.filter { it in enabled }
        if (ordered.isEmpty()) return

        val defaults = filterPreferences.bitrateDefaults.first()
        val effectiveFilter = _filterState.value
            .withBitrateDefaults(defaults.minKbps, defaults.maxKbps)
        if (effectiveFilter.isBitrateRangeInvalid) return

        val whitelist = whitelistedPaths.value
        val allVideos = videoDao.observeAll().first().map { it.toModel() }
        val destination = folderPreferences.targetFolderPath.first()

        val hit = ordered.firstNotNullOfOrNull { folder ->
            val candidates = allVideos.filter { item ->
                VideoFolder.ofPath(item.path) == folder &&
                    item.path !in whitelist &&
                    effectiveFilter.matches(item)
            }
            candidates.takeIf { it.isNotEmpty() }?.let { folder to it }
        } ?: return

        _autoMovePrompt.value = AutoMovePrompt(
            folder = hit.first,
            candidates = hit.second,
            targetPath = destination
        )
    }

    fun cancelScan() {
        scanJob?.cancel()
        _scanState.value = ScanUiState.Idle
    }

    fun resetScanState() {
        _scanState.value = ScanUiState.Idle
    }

    /* ==================== 多选 ==================== */

    fun enterSelectionMode(initialPath: String? = null) {
        _isSelectionMode.value = true
        if (initialPath != null) {
            _selectedPaths.value = setOf(initialPath)
        }
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedPaths.value = emptySet()
    }

    fun toggleSelection(path: String) {
        val current = _selectedPaths.value.toMutableSet()
        if (current.contains(path)) {
            current.remove(path)
            if (current.isEmpty()) {
                _isSelectionMode.value = false
            }
        } else {
            current.add(path)
            _isSelectionMode.value = true
        }
        _selectedPaths.value = current
    }

    fun selectAllFiltered() {
        val visiblePaths = filteredVideos.value.map { it.path }.toSet()
        _selectedPaths.value = visiblePaths
        _isSelectionMode.value = visiblePaths.isNotEmpty()
    }

    fun clearSelection() {
        _selectedPaths.value = emptySet()
    }

    /* ==================== 白名单 ==================== */

    fun showWhitelist() {
        _isWhitelistVisible.value = true
    }

    fun hideWhitelist() {
        _isWhitelistVisible.value = false
    }

    fun addSelectedToWhitelist() {
        val paths = _selectedPaths.value
        if (paths.isEmpty()) return
        val items = rawVideos.value.filter { it.path in paths }
        if (items.isEmpty()) return

        viewModelScope.launch {
            whitelistDao.insertAll(items.map { it.toWhitelistEntity() })
            exitSelectionMode()
        }
    }

    fun removeFromWhitelist(paths: List<String>) {
        if (paths.isEmpty()) return
        viewModelScope.launch {
            whitelistDao.safeDeleteByPathsInBatches(paths)
        }
    }

    /* ==================== 目标目录 ==================== */

    fun updateTargetFolder(newPath: String) {
        viewModelScope.launch {
            folderPreferences.saveTargetFolder(newPath)
        }
    }

    /* ==================== 移动 ==================== */

    fun executeMoveSelected() {
        performMove(_selectedPaths.value.toList(), targetFolderPath.value)
    }

    /** 自动询问弹窗里点「移动」 */
    fun confirmAutoMove() {
        val prompt = _autoMovePrompt.value ?: return
        _autoMovePrompt.value = null
        performMove(prompt.candidates.map { it.path }, prompt.targetPath)
    }

    /** 自动询问弹窗里点「暂不」 */
    fun dismissAutoMove() {
        _autoMovePrompt.value = null
    }

    /**
     * 执行批量移动（多选移动与自动询问共用同一条管道）。
     *
     * 完成后把 Room 主键路径热更新到新位置，保留全部重型媒体元数据，零二次解码。
     */
    private fun performMove(paths: List<String>, destination: String) {
        if (paths.isEmpty() || destination.isBlank()) return

        moveJob?.cancel()
        moveJob = viewModelScope.launch {
            fileMovementEngine.moveVideos(paths, destination).collect { event ->
                _moveProgressEvent.value = event

                if (event is MoveProgressEvent.Completed) {
                    scannerEngine.syncMovedVideos(event.report.successList)

                    val movedPaths = event.report.successList.map { it.oldPath }.toSet()
                    val remaining = _selectedPaths.value - movedPaths
                    _selectedPaths.value = remaining
                    if (remaining.isEmpty()) {
                        _isSelectionMode.value = false
                    }
                }
            }
        }
    }

    /* ==================== 自动询问开关 ==================== */

    fun showAutoMoveSettings() {
        _isAutoMoveSettingsVisible.value = true
    }

    fun hideAutoMoveSettings() {
        _isAutoMoveSettingsVisible.value = false
    }

    fun setAutoMoveEnabled(folder: VideoFolder, enabled: Boolean) {
        viewModelScope.launch {
            autoMovePreferences.setEnabled(folder, enabled)
        }
    }

    fun dismissMoveDialog() {
        _moveProgressEvent.value = null
    }

    fun updateFilterState(newState: VideoFilterState) {
        _filterState.value = newState
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
        moveJob?.cancel()
    }

    private companion object {
        /** 码率记忆的防抖时长：避免逐字符敲入时频繁落盘 */
        const val BITRATE_REMEMBER_DEBOUNCE_MS = 800L

        /**
         * 首次运行时的自动询问开关初始值（仅 DCIM 开启）。
         *
         * 这只是 DataStore 首帧的占位值：一旦真实偏好流发出，就会被用户的实际设置覆盖。
         */
        val DEFAULT_AUTO_MOVE_FOLDERS: Set<VideoFolder> =
            VideoFolder.entries.filterTo(mutableSetOf()) { it.defaultAutoMoveEnabled }
    }
}
