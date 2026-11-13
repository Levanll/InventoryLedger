package cn.levanll.inventory.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.levanll.inventory.i18n.I18n
import cn.levanll.inventory.data.dao.CategoryPick
import cn.levanll.inventory.data.dao.CategoryStat
import cn.levanll.inventory.data.dao.ItemWithCategory
import cn.levanll.inventory.data.db.AppDatabase
import cn.levanll.inventory.data.db.SeedData
import cn.levanll.inventory.domain.AppException
import cn.levanll.inventory.domain.BarcodeLookupService
import cn.levanll.inventory.domain.CategoryService
import cn.levanll.inventory.domain.ClassifyService
import cn.levanll.inventory.domain.Constants
import cn.levanll.inventory.domain.DetailRow
import cn.levanll.inventory.domain.ErrCode
import cn.levanll.inventory.domain.InboundInput
import cn.levanll.inventory.domain.InboundResult
import cn.levanll.inventory.domain.InventoryService
import cn.levanll.inventory.domain.MonthItemRow
import cn.levanll.inventory.domain.MonthOverview
import cn.levanll.inventory.domain.StatsService
import cn.levanll.inventory.domain.UpdateChecker
import cn.levanll.inventory.domain.UpdateDownloader
import cn.levanll.inventory.domain.UpdateInfo
import cn.levanll.inventory.domain.YearMonth
import cn.levanll.inventory.domain.startOfDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 库存页 UI 状态 */
data class InventoryUiState(
    val items: List<ItemWithCategory> = emptyList(),
    val cats: List<CategoryStat> = emptyList(),
    val recent: List<ItemWithCategory> = emptyList(),
    val lowCount: Int = 0,
    val expiringCount: Int = 0,
    val totalQty: Double = 0.0,
    val inStockKinds: Int = 0,
    val keyword: String = "",
    val selectedCat: Long? = null,
    val loading: Boolean = true,
    /** 启动时的已过期清单，非空则弹窗 */
    val expiredOnLaunch: List<ItemWithCategory> = emptyList(),
    val expiredDialogShown: Boolean = false,
    /**
     * 已折叠的一级分类 id。
     *
     * 用「折叠集合」而不是「展开集合」：默认全部展开是符合直觉的初始状态，
     * 新出现的分类也默认展开；只有用户主动折叠的才进集合。
     */
    val collapsedRoots: Set<Long> = emptySet(),
    /**
     * 「已用完」分区的商品：数量归零但用户选择保留的。
     *
     * 单独存放而不是混进 items：主列表是「现在有什么」，
     * 已用完是「曾经有什么」。混在一起会把主列表撑长，
     * 而完全不可见又让「保留记录」这个选项没有意义。
     */
    val usedUp: List<ItemWithCategory> = emptyList(),
    /** 「已用完」分区是否展开，默认折叠，不打扰主列表 */
    val usedUpExpanded: Boolean = false
)

/** 条码改名冲突：让用户决定这个条码从此关联谁 */
data class BarcodeConflictState(
    val barcode: String,
    val oldName: String,
    val newName: String,
    val oldItemId: Long
)

/** 入库页 UI 状态 */
data class InboundUiState(
    val name: String = "",
    val spec: String = "",
    val unit: String = "件",
    val qty: Double = 1.0,
    val categoryId: Long? = null,
    val categoryPath: String = "",
    val needConfirm: Boolean = false,
    val suggests: List<ItemWithCategory> = emptyList(),
    val allCats: List<CategoryPick> = emptyList(),
    val existingItemId: Long? = null,
    val saving: Boolean = false,
    val lowThreshold: Double = 0.0,
    val dupPayload: Map<String, Any>? = null,
    // ---- 扫码 ----
    val barcode: String? = null,
    val showScanner: Boolean = false,
    val scanSuggestion: BarcodeLookupService.ProductInfo? = null,
    val lookingUp: Boolean = false,
    // ---- 保质期 ----
    val expiryEnabled: Boolean = false,
    val productionDate: Long? = null,
    val shelfLifeDays: Int? = null,
    // ---- 条码改名冲突 ----
    val nameConflict: BarcodeConflictState? = null
)

/** 月度统计页状态 */
enum class StatsTab { INVENTORY, CONSUMPTION }

data class StatsUiState(
    val tab: StatsTab = StatsTab.INVENTORY,
    val month: YearMonth = YearMonth.now(),
    val availableMonths: List<YearMonth> = emptyList(),
    val overview: MonthOverview = MonthOverview(),
    val rows: List<MonthItemRow> = emptyList(),
    val loading: Boolean = true,
    val momPercent: Double? = null,
    val detailItemId: Long? = null,
    val detailItemName: String = "",
    val detailRows: List<DetailRow> = emptyList(),
    val showAllDetails: Boolean = false
)

/** 分类管理页状态（三级分类增删改） */
data class CategoryManageState(
    val cats: List<CategoryPick> = emptyList(),
    val editing: Boolean = false,
    val editingId: Long? = null,
    val parentId: Long? = null,
    val parentPath: String = "",
    val inputName: String = "",
    val error: String? = null
)

/** 设置面板状态 */
data class SettingsUiState(
    /**
     * 是否允许检查更新。
     *
     * null 有歧义，不能单独用来判断"要不要弹询问框"：
     *   - 尚未从磁盘加载（冷启动第一帧）
     *   - 加载完了，但用户确实还没表态过
     * 两者都是 null，于是每次冷启动都会先闪一下询问框。
     * 用 [consentLoaded] 区分：只有"已加载 且 仍为 null"才该弹。
     */
    val updateCheckEnabled: Boolean? = null,
    /** 同意状态是否已从磁盘读出。false 表示还在加载，此时不应弹任何询问框 */
    val consentLoaded: Boolean = false,
    val checking: Boolean = false,
    /** 检测到的新版本；null 表示已是最新或还没查过 */
    val newVersion: UpdateInfo? = null,
    val currentVersion: String = "",
    val currentCode: Int = 0,
    /** 上次检查的结果提示，用于「立即检查」后的反馈 */
    val lastCheckMessage: String? = null
)

/** 更新下载状态 */
sealed class DownloadUiState {
    object Idle : DownloadUiState()
    data class Running(val percent: Int, val done: Long, val total: Long) : DownloadUiState()
    object Verifying : DownloadUiState()
    /** 需要先授权「安装未知应用」 */
    object NeedInstallPermission : DownloadUiState()
    data class Failed(val message: String) : DownloadUiState()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val inv = InventoryService(db)
    private val cls = ClassifyService(db)
    private val stats = StatsService(db)
    private val barcodeLookup = BarcodeLookupService(db)
    private val catSvc = CategoryService(db)
    private val updateChecker = UpdateChecker(app)
    private val updateDownloader = UpdateDownloader(app)

    private val _inventory = MutableStateFlow(InventoryUiState())
    val inventory: StateFlow<InventoryUiState> = _inventory.asStateFlow()

    private val _inbound = MutableStateFlow(InboundUiState())
    val inbound: StateFlow<InboundUiState> = _inbound.asStateFlow()

    private val _stats = MutableStateFlow(StatsUiState())
    val statsState: StateFlow<StatsUiState> = _stats.asStateFlow()

    private val _catManage = MutableStateFlow(CategoryManageState())
    val catManage: StateFlow<CategoryManageState> = _catManage.asStateFlow()

    private val _settings = MutableStateFlow(SettingsUiState())
    val settings: StateFlow<SettingsUiState> = _settings.asStateFlow()

    private val _download = MutableStateFlow<DownloadUiState>(DownloadUiState.Idle)
    val download: StateFlow<DownloadUiState> = _download.asStateFlow()

    /** 正在下载时的协程，取消用 */
    private var downloadJob: Job? = null

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    fun showToast(msg: String) { _toast.value = msg }
    fun clearToast() { _toast.value = null }

    init {
        viewModelScope.launch {
            // 种子：全新安装补全三级分类；从旧版升级补齐缺失的三级
            // 顺带清理升级后残留的安装包（续期 24h，见 UpdateDownloader.cleanupStale）
            withContext(Dispatchers.IO) {
                SeedData.ensureSeeded(db)
                updateDownloader.cleanupStale()
            }
            initUpdateState()
            refreshInventory()
            refreshStats()
            // 进入应用即检查已过期商品，非空则弹窗提醒
            checkExpiredOnLaunch()
            // 更新检查放在最后，异步执行，不阻塞首屏
            autoCheckForUpdate()
        }
    }

    /**
     * 启动时检查已过期商品。
     * 只在应用首次进入时弹一次，避免每次回前台都打扰。
     */
    fun checkExpiredOnLaunch() {
        if (_inventory.value.expiredDialogShown) return
        viewModelScope.launch {
            val expired = withContext(Dispatchers.IO) { inv.listExpired() }
            _inventory.value = _inventory.value.copy(
                expiredOnLaunch = expired,
                expiredDialogShown = true
            )
        }
    }

    fun dismissExpiredDialog() {
        _inventory.value = _inventory.value.copy(expiredOnLaunch = emptyList())
    }

    // ==========================================================
    // 库存页
    // ==========================================================

    fun refreshInventory() {
        viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.IO) {
                    Quad(
                        inv.search(_inventory.value.keyword, _inventory.value.selectedCat, 0),
                        db.categoryDao().listRootStats(),
                        inv.listRecentUsed(),
                        inv.countLowStock(),
                        inv.totalQuantity(),
                        inv.countInStock(),
                        inv.countExpiringSoon(),
                        inv.listUsedUp(_inventory.value.keyword, _inventory.value.selectedCat)
                    )
                }
                // 关键：在赋值的那一刻重新读取最新状态，而不是用协程启动前的快照。
                // 否则会覆盖掉并发写入的 expiredOnLaunch 等字段。
                _inventory.value = _inventory.value.copy(
                    items = r.items,
                    cats = r.cats,
                    recent = r.recent,
                    lowCount = r.low,
                    totalQty = r.total,
                    inStockKinds = r.kinds,
                    expiringCount = r.expiring,
                    usedUp = r.usedUp,
                    loading = false
                )
            } catch (e: Exception) {
                _inventory.value = _inventory.value.copy(loading = false)
                _toast.value = I18n.s("读取失败：${e.message}", "Load failed: ${e.message}")
            }
        }
    }

    private data class Quad(
        val items: List<ItemWithCategory>,
        val cats: List<CategoryStat>,
        val recent: List<ItemWithCategory>,
        val low: Int,
        val total: Double,
        val kinds: Int,
        val expiring: Int,
        val usedUp: List<ItemWithCategory>
    )

    /** 展开 / 折叠「已用完」分区 */
    fun toggleUsedUp() {
        _inventory.value = _inventory.value.copy(usedUpExpanded = !_inventory.value.usedUpExpanded)
    }

    /** 从「已用完」恢复入库 */
    fun restoreUsedUp(itemId: Long, itemName: String, qty: Double = 1.0) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { inv.restoreUsedUp(itemId, qty) }
                refreshInventory()
                refreshStatsIfCurrentMonth()
                _toast.value = I18n.s("已恢复「$itemName」", "Restored $itemName")
            } catch (e: AppException) {
                _toast.value = e.message
            }
        }
    }

    fun setKeyword(kw: String) {
        _inventory.value = _inventory.value.copy(keyword = kw)
        refreshInventory()
    }

    fun setCategory(id: Long?) {
        _inventory.value = _inventory.value.copy(selectedCat = id)
        refreshInventory()
    }

    /** 折叠 / 展开某个一级分类 */
    fun toggleGroup(rootId: Long) {
        val cur = _inventory.value.collapsedRoots
        _inventory.value = _inventory.value.copy(
            collapsedRoots = if (rootId in cur) cur - rootId else cur + rootId
        )
    }

    /** 全部折叠 / 全部展开 */
    fun setAllCollapsed(collapse: Boolean) {
        val roots = _inventory.value.cats.map { it.id }.toSet()
        _inventory.value = _inventory.value.copy(
            collapsedRoots = if (collapse) roots else emptySet()
        )
    }

    fun dismissLowStockBar() {
        _inventory.value = _inventory.value.copy(lowCount = 0)
    }

    fun dismissExpiringBar() {
        _inventory.value = _inventory.value.copy(expiringCount = 0)
    }

    fun adjustQty(itemId: Long, delta: Double, onZero: (ItemWithCategory) -> Unit = {}) {
        val item = _inventory.value.items.firstOrNull { it.id == itemId } ?: return
        val next = item.quantity + delta
        if (next < 0) {
            _toast.value = I18n.s("已经是 0 了", "Already 0")
            return
        }
        viewModelScope.launch {
            try {
                val after = withContext(Dispatchers.IO) { inv.adjustQuantity(itemId, delta) }
                refreshInventory()
                refreshStatsIfCurrentMonth()
                if (after <= 0.0 && delta < 0) {
                    onZero(item)
                    // 归零后弹窗可能被「保留记录」关掉。此时商品应从主列表
                    // 移到「已用完」分区 —— 但上面那次 refreshInventory 的查询
                    // 早于本次写入，查不到它。补一次，让两个分区都到位。
                    refreshInventory()
                }
            } catch (e: AppException) {
                _toast.value = e.message
            }
        }
    }

    fun deleteZeroItem(itemId: Long, itemName: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { inv.deleteZeroItem(itemId) }
                refreshInventory()
                refreshStatsIfCurrentMonth()
                _toast.value = I18n.s("已删除「$itemName」", "Deleted $itemName")
            } catch (e: AppException) {
                _toast.value = e.message
            }
        }
    }

    fun revertRecord(recordId: Long) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { inv.revertRecord(recordId) }
                refreshInventory()
                refreshStatsIfCurrentMonth()
                _toast.value = I18n.s("已撤销", "Undone")
            } catch (e: AppException) {
                _toast.value = e.message
            }
        }
    }

    // ==========================================================
    // 入库页
    // ==========================================================

    fun openInbound(presetItemId: Long? = null) {
        viewModelScope.launch {
            val defaults = withContext(Dispatchers.IO) { loadFormDefaults() }
            val cats = withContext(Dispatchers.IO) { catSvc.listForPick() }
            _inbound.value = InboundUiState(
                unit = defaults.first,
                lowThreshold = defaults.second,
                allCats = cats
            )
            if (presetItemId != null) {
                val item = withContext(Dispatchers.IO) { inv.getItem(presetItemId) }
                if (item != null) {
                    val path = item.categoryId?.let {
                        withContext(Dispatchers.IO) { cls.pathOf(it) }
                    } ?: ""
                    _inbound.value = _inbound.value.copy(
                        existingItemId = item.id,
                        name = item.name,
                        spec = item.spec,
                        unit = item.unit,
                        qty = 1.0,
                        categoryId = item.categoryId,
                        categoryPath = path,
                        needConfirm = false,
                        suggests = emptyList(),
                        barcode = item.barcode,
                        expiryEnabled = item.expiryEnabled,
                        productionDate = item.productionDate,
                        shelfLifeDays = item.shelfLifeDays
                    )
                }
            }
        }
    }

    private suspend fun loadFormDefaults(): Pair<String, Double> {
        val unit = db.categoryDao().getMemo(Constants.MEMO_LAST_UNIT) ?: "件"
        val th = db.categoryDao().getMemo(Constants.MEMO_THRESHOLD)?.toDoubleOrNull() ?: 0.0
        return unit to th
    }

    /** 扫码入口：打开相机 */
    fun openScanner() {
        _inbound.value = _inbound.value.copy(showScanner = true)
    }

    fun closeScanner() {
        _inbound.value = _inbound.value.copy(showScanner = false)
    }

    /**
     * 扫到条码。
     *
     * 三种结果的处理：
     *   1. 本地命中（LOCAL/ITEM）→ 直接进入追加模式，字段全填
     *   2. 联网命中（NET_*）→ 作为「建议」展示，用户点采用才写入
     *   3. 都查不到 → 只填条码，其余手填
     */
    fun onBarcodeScanned(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            _inbound.value = _inbound.value.copy(
                showScanner = false,
                barcode = trimmed,
                scanSuggestion = null,
                lookingUp = true
            )

            val info = withContext(Dispatchers.IO) { barcodeLookup.lookup(trimmed) }

            if (info == null) {
                _inbound.value = _inbound.value.copy(lookingUp = false)
                _toast.value = I18n.s(
                    "未查到该条码的商品信息，请手动填写",
                    "No product found for this barcode, please fill in manually"
                )
                return@launch
            }

            if (info.source == BarcodeLookupService.Source.LOCAL ||
                info.source == BarcodeLookupService.Source.ITEM
            ) {
                val path = info.categoryId?.let {
                    withContext(Dispatchers.IO) { cls.pathOf(it) }
                } ?: info.categoryHint ?: ""

                val item = withContext(Dispatchers.IO) { inv.getByBarcode(trimmed) }
                _inbound.value = _inbound.value.copy(
                    lookingUp = false,
                    existingItemId = item?.id,
                    name = info.name,
                    spec = item?.spec ?: info.spec ?: "",
                    unit = item?.unit ?: "件",
                    qty = 1.0,
                    categoryId = info.categoryId,
                    categoryPath = path,
                    needConfirm = false,
                    suggests = emptyList(),
                    expiryEnabled = item?.expiryEnabled ?: false,
                    productionDate = item?.productionDate,
                    shelfLifeDays = item?.shelfLifeDays
                )
                _toast.value = if (item != null) {
                    I18n.s(
                        "识别到已有商品「${info.name}」，将追加数量",
                        "Found existing item ${info.name}, quantity will be appended"
                    )
                } else {
                    I18n.s("已从本地记录识别「${info.name}」", "Recognized ${info.name} from local records")
                }
                return@launch
            }

            // 联网结果：作为建议展示
            _inbound.value = _inbound.value.copy(
                lookingUp = false,
                scanSuggestion = info
            )
        }
    }

    /** 用户点「采用建议」：把结果写进表单，并记入本地自学习库 */
    fun applyScanSuggestion() {
        val info = _inbound.value.scanSuggestion ?: return
        val barcode = _inbound.value.barcode
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { cls.classify(info.name) }
            val finalCatId = info.categoryId ?: r.categoryId ?: _inbound.value.categoryId
            val finalPath = r.categoryPath ?: info.categoryHint ?: _inbound.value.categoryPath
            val finalName = if (info.brand != null) "${info.brand} ${info.name}" else info.name

            _inbound.value = _inbound.value.copy(
                name = finalName,
                spec = info.spec ?: _inbound.value.spec,
                categoryId = finalCatId,
                categoryPath = finalPath,
                needConfirm = r.needConfirm,
                scanSuggestion = null,
                existingItemId = null
            )

            // 采用即记住，下次扫同一商品直接命中本地库
            if (barcode != null) {
                withContext(Dispatchers.IO) {
                    barcodeLookup.remember(
                        barcode = barcode,
                        name = finalName,
                        brand = info.brand,
                        spec = info.spec,
                        categoryId = finalCatId,
                        source = "net"
                    )
                }
            }
        }
    }

    fun dismissScanSuggestion() {
        _inbound.value = _inbound.value.copy(scanSuggestion = null)
    }

    /**
     * 名称变更。
     *
     * 若当前条码已关联了另一个商品，且用户正在改成一个不同的名字，
     * 不阻止，但弹出三选项让用户决定这个条码以后关联谁。
     */
    fun inboundNameChanged(v: String) {
        val cur = _inbound.value
        _inbound.value = cur.copy(name = v)

        if (v.trim().isEmpty()) {
            _inbound.value = _inbound.value.copy(
                suggests = emptyList(), categoryId = null, categoryPath = "", needConfirm = false
            )
            return
        }

        // 条码冲突检查：只在「追加模式」且名字被改动时触发
        val bc = cur.barcode
        val existingId = cur.existingItemId
        if (bc != null && existingId != null && v.trim() != cur.name.trim()) {
            _inbound.value = _inbound.value.copy(
                nameConflict = BarcodeConflictState(
                    barcode = bc,
                    oldName = cur.name,
                    newName = v.trim(),
                    oldItemId = existingId
                )
            )
            return
        }

        viewModelScope.launch {
            val sug = withContext(Dispatchers.IO) { inv.suggestByName(v) }
            val r = withContext(Dispatchers.IO) { cls.prefill(v, null) }
            _inbound.value = _inbound.value.copy(
                suggests = sug,
                categoryId = r.categoryId ?: _inbound.value.categoryId,
                categoryPath = r.categoryPath ?: _inbound.value.categoryPath,
                needConfirm = r.needConfirm
            )
        }
    }

    /** 冲突弹窗：沿用原名 */
    fun resolveConflictKeepOld() {
        val c = _inbound.value.nameConflict ?: return
        _inbound.value = _inbound.value.copy(name = c.oldName, nameConflict = null)
    }

    /** 冲突弹窗：改为新名称（该条码以后关联新名字，旧商品同步改名） */
    fun resolveConflictUseNew() {
        val c = _inbound.value.nameConflict ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    inv.renameItemAndMemo(
                        itemId = c.oldItemId,
                        newName = c.newName,
                        barcode = c.barcode,
                        categoryId = _inbound.value.categoryId
                    )
                    // renameItemAndMemo 直接写 barcode_memo 表，不走 remember()，
                    // 所以内存缓存不会被清。若本次会话早先扫过这个条码，缓存里
                    // 还是旧名称，再扫一次会把旧名字填回表单。
                    barcodeLookup.invalidate(c.barcode)
                }
                _inbound.value = _inbound.value.copy(name = c.newName, nameConflict = null)
                refreshInventory()
                _toast.value = I18n.s("已改为「${c.newName}」", "Renamed to ${c.newName}")
            } catch (e: Exception) {
                _toast.value = I18n.s("改名失败：${e.message}", "Rename failed: ${e.message}")
            }
        }
    }

    /**
     * 冲突弹窗：这是另一件商品，新建条目。
     * 旧商品保留数据但解绑条码，避免两个商品争用同一个唯一条码。
     */
    fun resolveConflictAsNewItem() {
        val c = _inbound.value.nameConflict ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { inv.detachBarcode(c.oldItemId) }
                _inbound.value = _inbound.value.copy(
                    existingItemId = null,
                    spec = "",
                    qty = 1.0,
                    nameConflict = null
                )
                refreshInventory()
                _toast.value = I18n.s(
                    "已解绑旧条目，将新建「${c.newName}」",
                    "Detached from old item, will create ${c.newName}"
                )
            } catch (e: Exception) {
                _toast.value = I18n.s("操作失败：${e.message}", "Operation failed: ${e.message}")
            }
        }
    }

    fun dismissNameConflict() {
        _inbound.value = _inbound.value.copy(nameConflict = null)
    }

    /** 退出追加模式，转为新建（保留条码，但不再累加到原商品） */
    fun exitAppendMode() {
        _inbound.value = _inbound.value.copy(existingItemId = null, spec = "", qty = 1.0)
        _toast.value = I18n.s("已切换为新建商品", "Switched to creating a new item")
    }

    fun inboundSpecChanged(v: String) { _inbound.value = _inbound.value.copy(spec = v) }

    fun inboundUnitChanged(u: String) {
        val st = Constants.stepOfUnit(u)
        val q = if (_inbound.value.qty < st) st else _inbound.value.qty
        _inbound.value = _inbound.value.copy(unit = u, qty = q)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { inv.saveLastUnit(u) }
        }
    }

    fun inboundQtyChanged(q: Double) { _inbound.value = _inbound.value.copy(qty = q) }
    fun inboundThresholdChanged(v: Double) { _inbound.value = _inbound.value.copy(lowThreshold = v) }

    fun inboundPickCategory(id: Long) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) { cls.pathOf(id) } ?: ""
            _inbound.value = _inbound.value.copy(
                categoryId = id, categoryPath = path, needConfirm = false
            )
            withContext(Dispatchers.IO) { cls.rememberCategory(id) }
        }
    }

    fun inboundUseSuggestion(s: ItemWithCategory) {
        viewModelScope.launch {
            val path = s.categoryId?.let { withContext(Dispatchers.IO) { cls.pathOf(it) } } ?: ""
            _inbound.value = _inbound.value.copy(
                existingItemId = s.id,
                name = s.name,
                spec = s.spec,
                unit = s.unit,
                qty = 1.0,
                categoryId = s.categoryId,
                categoryPath = path,
                needConfirm = false,
                suggests = emptyList(),
                barcode = s.barcode,
                expiryEnabled = s.expiryEnabled,
                productionDate = s.productionDate,
                shelfLifeDays = s.shelfLifeDays
            )
        }
    }

    // ---- 保质期表单 ----

    fun setExpiryEnabled(on: Boolean) {
        _inbound.value = if (on) {
            // 打开时给个合理默认：今天生产、保质期 30 天
            _inbound.value.copy(
                expiryEnabled = true,
                productionDate = _inbound.value.productionDate
                    ?: startOfDay(System.currentTimeMillis()),
                shelfLifeDays = _inbound.value.shelfLifeDays ?: 30
            )
        } else {
            // 关闭时清空，避免残留导致「关了还显示到期」
            _inbound.value.copy(
                expiryEnabled = false,
                productionDate = null,
                shelfLifeDays = null
            )
        }
    }

    fun setProductionDate(ms: Long) {
        _inbound.value = _inbound.value.copy(productionDate = startOfDay(ms))
    }

    fun setShelfLifeDays(days: Int?) {
        _inbound.value = _inbound.value.copy(shelfLifeDays = days)
    }

    fun inboundSave(allowCreateSpec: Boolean, onSaved: (InboundResult?) -> Unit) {
        val s = _inbound.value
        if (s.name.trim().isEmpty() || s.qty <= 0 || s.saving) return
        _inbound.value = s.copy(saving = true)

        viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.IO) {
                    val res = inv.inbound(
                        InboundInput(
                            name = s.name, spec = s.spec, unit = s.unit, qty = s.qty,
                            categoryId = s.categoryId, lowThreshold = s.lowThreshold,
                            existingItemId = s.existingItemId,
                            allowCreateSpec = allowCreateSpec,
                            barcode = s.barcode,
                            expiryEnabled = s.expiryEnabled,
                            productionDate = s.productionDate,
                            shelfLifeDays = s.shelfLifeDays
                        )
                    )
                    if (s.categoryId != null) cls.learn(s.name, s.categoryId)
                    // 只要带条码保存，就记住「条码 → 商品信息」。
                    // 这样即使用户纯手填，下次扫同一商品也能自动带出。
                    val bc = s.barcode
                    if (!bc.isNullOrBlank()) {
                        barcodeLookup.remember(
                            barcode = bc,
                            name = s.name,
                            brand = null,
                            spec = s.spec,
                            categoryId = s.categoryId,
                            source = "local"
                        )
                    }
                    res
                }
                _inbound.value = _inbound.value.copy(saving = false)
                refreshInventory()
                refreshStatsIfCurrentMonth()
                onSaved(r)
            } catch (e: AppException) {
                _inbound.value = _inbound.value.copy(saving = false)
                if (e.code == ErrCode.DUPLICATE_ITEM) {
                    _inbound.value = _inbound.value.copy(dupPayload = e.payload)
                } else {
                    _toast.value = e.message
                }
                onSaved(null)
            }
        }
    }

    fun inboundChooseAppend(onSaved: (InboundResult?) -> Unit) {
        val payload = _inbound.value.dupPayload
        val id = (payload?.get("existingId") as? Long)
            ?: (payload?.get("existingId") as? Number)?.toLong()
            ?: _inbound.value.suggests.firstOrNull()?.id
        _inbound.value = _inbound.value.copy(dupPayload = null)
        if (id == null) {
            _toast.value = I18n.s("找不到原商品，请另建规格", "Original item not found, please create a separate spec")
            return
        }
        _inbound.value = _inbound.value.copy(existingItemId = id)
        inboundSave(false, onSaved)
    }

    fun inboundDismissDup() { _inbound.value = _inbound.value.copy(dupPayload = null) }

    fun resetInbound() { _inbound.value = InboundUiState() }

    // ==========================================================
    // 月度统计
    // ==========================================================

    fun refreshStats() {
        viewModelScope.launch {
            val s = _stats.value
            try {
                val months = withContext(Dispatchers.IO) { stats.availableMonths() }
                val merged = (listOf(YearMonth.now()) + months).distinct().sortedDescending()
                val target = if (merged.contains(s.month)) s.month else YearMonth.now()
                loadMonth(target, s.tab, months = merged)
            } catch (e: Exception) {
                _stats.value = s.copy(loading = false)
                _toast.value = I18n.s("读取统计失败：${e.message}", "Failed to load stats: ${e.message}")
            }
        }
    }

    private fun loadMonth(ym: YearMonth, tab: StatsTab, months: List<YearMonth>? = null) {
        viewModelScope.launch {
            _stats.value = _stats.value.copy(month = ym, tab = tab, loading = true)
            try {
                val avail = months ?: withContext(Dispatchers.IO) { stats.availableMonths() }
                    .let { (listOf(YearMonth.now()) + it).distinct().sortedDescending() }
                val ov = withContext(Dispatchers.IO) { stats.overview(ym) }
                val rows = withContext(Dispatchers.IO) {
                    if (tab == StatsTab.INVENTORY) stats.monthlyInventory(ym)
                    else stats.monthlyConsumption(ym)
                }
                val mom = withContext(Dispatchers.IO) { stats.consumptionMoM(ym) }
                _stats.value = _stats.value.copy(
                    month = ym,
                    tab = tab,
                    availableMonths = avail,
                    overview = ov,
                    rows = rows,
                    momPercent = mom,
                    loading = false,
                    detailItemId = null,
                    detailRows = emptyList(),
                    showAllDetails = false
                )
            } catch (e: Exception) {
                _stats.value = _stats.value.copy(loading = false)
                _toast.value = I18n.s("读取失败：${e.message}", "Load failed: ${e.message}")
            }
        }
    }

    fun setStatsTab(tab: StatsTab) {
        val s = _stats.value
        loadMonth(s.month, tab, s.availableMonths)
    }

    fun setMonth(ym: YearMonth) {
        val s = _stats.value
        loadMonth(ym, s.tab, s.availableMonths)
    }

    fun prevMonth() {
        val s = _stats.value
        loadMonth(s.month.prev(), s.tab, s.availableMonths)
    }

    fun nextMonth() {
        val s = _stats.value
        if (s.month.next() > YearMonth.now()) return
        loadMonth(s.month.next(), s.tab, s.availableMonths)
    }

    fun openItemDetail(itemId: Long, itemName: String) {
        val s = _stats.value
        viewModelScope.launch {
            val rows = withContext(Dispatchers.IO) { stats.detailOfItem(itemId, s.month) }
            _stats.value = s.copy(
                detailItemId = itemId,
                detailItemName = itemName,
                detailRows = rows,
                showAllDetails = false
            )
        }
    }

    fun openMonthDetails() {
        val s = _stats.value
        viewModelScope.launch {
            val rows = withContext(Dispatchers.IO) { stats.detailOfMonth(s.month) }
            _stats.value = s.copy(
                detailItemId = null,
                detailItemName = I18n.s(
                    "${s.month.label()}全部流水",
                    "All records in ${s.month.label()}"
                ),
                detailRows = rows,
                showAllDetails = true
            )
        }
    }

    fun closeDetail() {
        _stats.value = _stats.value.copy(
            detailItemId = null, detailItemName = "", detailRows = emptyList(), showAllDetails = false
        )
    }

    private fun refreshStatsIfCurrentMonth() {
        val s = _stats.value
        if (s.month != YearMonth.now()) return
        viewModelScope.launch {
            val ov = withContext(Dispatchers.IO) { stats.overview(s.month) }
            val rows = withContext(Dispatchers.IO) {
                if (s.tab == StatsTab.INVENTORY) stats.monthlyInventory(s.month)
                else stats.monthlyConsumption(s.month)
            }
            val mom = withContext(Dispatchers.IO) { stats.consumptionMoM(s.month) }
            val months = withContext(Dispatchers.IO) { stats.availableMonths() }
            _stats.value = _stats.value.copy(
                overview = ov,
                rows = rows,
                momPercent = mom,
                availableMonths = (listOf(YearMonth.now()) + months).distinct().sortedDescending()
            )
        }
    }

    // ==========================================================
    // 分类管理（三级分类增删改）
    // ==========================================================

    fun loadCategoryManage() {
        viewModelScope.launch {
            val cats = withContext(Dispatchers.IO) { catSvc.listForPick() }
            _catManage.value = _catManage.value.copy(cats = cats)
        }
    }

    /** 打开新建弹窗；parentId 为 null 表示新建一级 */
    fun openCreateCategory(parentId: Long?, parentPath: String) {
        _catManage.value = _catManage.value.copy(
            editing = true,
            editingId = null,
            parentId = parentId,
            parentPath = parentPath,
            inputName = "",
            error = null
        )
    }

    fun openRenameCategory(id: Long, currentName: String) {
        _catManage.value = _catManage.value.copy(
            editing = true,
            editingId = id,
            inputName = currentName,
            error = null
        )
    }

    fun setCategoryInput(v: String) {
        _catManage.value = _catManage.value.copy(inputName = v, error = null)
    }

    fun closeCategoryEditor() {
        _catManage.value = _catManage.value.copy(editing = false, error = null)
    }

    fun submitCategoryEdit() {
        val st = _catManage.value
        val name = st.inputName.trim()
        if (name.isEmpty()) {
            _catManage.value = st.copy(error = I18n.s("名称不能为空", "Name cannot be empty"))
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (st.editingId == null) {
                        catSvc.create(st.parentId, name)
                    } else {
                        catSvc.rename(st.editingId, name)
                    }
                }
                _catManage.value = _catManage.value.copy(editing = false, error = null)
                loadCategoryManage()
                refreshInboundCategories()
                _toast.value = if (st.editingId == null) {
                    I18n.s("已创建「$name」", "Created $name")
                } else {
                    I18n.s("已重命名", "Renamed")
                }
            } catch (e: AppException) {
                _catManage.value = _catManage.value.copy(error = e.message)
            }
        }
    }

    /** 删除分类：有内容时抛错，UI 引导迁移 */
    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { catSvc.delete(id) }
                loadCategoryManage()
                refreshInboundCategories()
                _toast.value = I18n.s("已删除", "Deleted")
            } catch (e: AppException) {
                _toast.value = e.message
            }
        }
    }

    fun migrateAndDeleteCategory(fromId: Long, toId: Long) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { catSvc.migrateAndDelete(fromId, toId) }
                loadCategoryManage()
                refreshInboundCategories()
                refreshInventory()
                _toast.value = I18n.s("已迁移并删除", "Migrated and deleted")
            } catch (e: AppException) {
                _toast.value = e.message
            }
        }
    }

    /** 刷新入库页的分类可选列表 */
    fun refreshInboundCategories() {
        viewModelScope.launch {
            val cats = withContext(Dispatchers.IO) { catSvc.listForPick() }
            _inbound.value = _inbound.value.copy(allCats = cats)
        }
    }

    // ==========================================================
    // 更新检查
    // ==========================================================

    private fun initUpdateState() {
        val app = getApplication<Application>()
        val info = try {
            app.packageManager.getPackageInfo(app.packageName, 0)
        } catch (e: Exception) {
            null
        }
        _settings.value = _settings.value.copy(
            updateCheckEnabled = updateChecker.consentState(),
            // 读完磁盘才允许 UI 决定是否弹询问框。放在同一 copy 里，
            // 保证"值"与"已加载"这两个字段不会出现一个更新一个没更新的中间态。
            consentLoaded = true,
            currentVersion = info?.versionName ?: "",
            currentCode = info?.let {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    it.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    it.versionCode
                }
            } ?: 0
        )
    }

    /** 首次启动询问用户是否允许检查更新 */
    fun setUpdateCheckEnabled(enabled: Boolean) {
        updateChecker.setEnabled(enabled)
        _settings.value = _settings.value.copy(
            updateCheckEnabled = enabled,
            consentLoaded = true,
        )
        if (enabled) autoCheckForUpdate()
    }

    /**
     * 启动时自动检查。只在用户已允许且未被 24 小时闸拦下时才会真正发请求。
     * 有新版就写进状态，由 UI 弹窗；查不到什么都不做。
     */
    private fun autoCheckForUpdate() {
        if (updateChecker.consentState() != true) return
        viewModelScope.launch {
            val info = updateChecker.check(force = false)
            if (info != null) {
                _settings.value = _settings.value.copy(newVersion = info)
            }
        }
    }

    /** 设置面板里点版本行：强制检查，跳过 24 小时闸，并给明确反馈 */
    fun checkForUpdateManually() {
        if (_settings.value.checking) return
        _settings.value = _settings.value.copy(checking = true, lastCheckMessage = null)
        viewModelScope.launch {
            val info = updateChecker.check(force = true)
            _settings.value = _settings.value.copy(
                checking = false,
                newVersion = info,
                lastCheckMessage = if (info == null) {
                    I18n.s("已是最新版本", "Already up to date")
                } else {
                    null
                }
            )
        }
    }

    /** 「以后再说」：只关掉本次弹窗，下次启动仍会提醒 */
    fun dismissUpdate() {
        _settings.value = _settings.value.copy(newVersion = null)
    }

    fun clearCheckMessage() {
        _settings.value = _settings.value.copy(lastCheckMessage = null)
    }

    // ---- 下载与安装 ----

    fun startUpdateDownload() {
        val info = _settings.value.newVersion ?: return
        if (_download.value is DownloadUiState.Running) return

        // 授权没开就先去开，开了再回来点一次
        if (!updateDownloader.canInstall()) {
            _download.value = DownloadUiState.NeedInstallPermission
            updateDownloader.openInstallPermissionSettings()
            return
        }

        downloadJob = viewModelScope.launch {
            _download.value = DownloadUiState.Running(0, 0, info.sizeBytes)
            try {
                val apk = updateDownloader.download(
                    url = info.downloadUrl,
                    expectedSize = info.sizeBytes
                ) { pct, done, total ->
                    _download.value = DownloadUiState.Running(pct, done, total)
                }

                _download.value = DownloadUiState.Verifying
                if (!updateDownloader.verifySignature(apk)) {
                    updateDownloader.discard()
                    apk.delete()
                    _download.value = DownloadUiState.Failed(
                        I18n.s("安装包校验失败，请重试", "Package verification failed, please retry")
                    )
                    return@launch
                }

                // 唤起系统安装器。文件不在这里删 —— 安装是异步的，
                // 立即删除会和系统读取抢跑，导致「解析包时出现问题」。
                // 由下次启动的 cleanupStale() 清理。
                updateDownloader.install(apk)
                _download.value = DownloadUiState.Idle
                _settings.value = _settings.value.copy(newVersion = null)
            } catch (e: kotlinx.coroutines.CancellationException) {
                updateDownloader.discard()
                _download.value = DownloadUiState.Idle
                throw e
            } catch (e: Exception) {
                updateDownloader.discard()
                _download.value = DownloadUiState.Failed(
                    e.message ?: I18n.s("下载失败", "Download failed")
                )
            }
        }
    }

    fun cancelUpdateDownload() {
        downloadJob?.cancel()
        downloadJob = null
        updateDownloader.discard()
        _download.value = DownloadUiState.Idle
    }

    fun dismissDownloadError() {
        _download.value = DownloadUiState.Idle
    }

    /** 用户从「安装未知应用」设置页回来后，重新走下载流程 */
    fun retryInstallAfterPermission() {
        // 先清掉「需要授权」状态，否则再进 startUpdateDownload 时
        // canInstall() 仍为 false，会立刻又被设回这个状态，形成死循环
        _download.value = DownloadUiState.Idle
        if (updateDownloader.canInstall()) {
            startUpdateDownload()
        } else {
            // 用户还没真正授权（可能只是切回来），再跳一次设置页
            updateDownloader.openInstallPermissionSettings()
            _download.value = DownloadUiState.NeedInstallPermission
        }
    }
}
