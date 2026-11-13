package cn.levanll.inventory.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.levanll.inventory.i18n.I18n
import cn.levanll.inventory.domain.InboundResult
import kotlinx.coroutines.launch

/**
 * 主界面：两个 Tab（库存 / 统计）+ 入库浮层。
 *
 * statusBarsPadding 必须加：enableEdgeToEdge 之后，
 * 不加这一层标题栏会画到状态栏下面且点击被系统拦截。
 */
@Composable
fun MainScreen(vm: MainViewModel) {
    var currentTab by remember { mutableIntStateOf(0) }
    var showInbound by remember { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val toast by vm.toast.collectAsState()
    val settings by vm.settings.collectAsState()
    val download by vm.download.collectAsState()

    LaunchedEffect(toast) {
        toast?.let {
            snackbar.showSnackbar(it)
            vm.clearToast()
        }
    }

    fun openInbound(id: Long?) {
        vm.openInbound(id)
        showInbound = true
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!showInbound) {
                BottomTabBar(currentTab) { currentTab = it }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .statusBarsPadding()
        ) {
            when (currentTab) {
                0 -> InventoryScreen(
                    vm = vm,
                    onOpenInbound = { openInbound(it) }
                )
                else -> {
                    // 每次切到统计页都重载本月数据。
                    // key 用 currentTab：切回来时 effect 会重新执行一次。
                    LaunchedEffect(currentTab) {
                        if (currentTab == 1) vm.refreshStats()
                    }
                    StatsScreen(vm = vm)
                }
            }
        }
    }

    // 入库浮层
    if (showInbound) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            InboundScreen(
                vm = vm,
                onSaved = { r: InboundResult ->
                    showInbound = false
                    vm.resetInbound()
                    scope.launch {
                        val res = snackbar.showSnackbar(
                            message = I18n.s(
                                "已入库，数量 ${fmtQty(r.quantityAfter)}",
                                "Added, now ${fmtQty(r.quantityAfter)}"
                            ),
                            actionLabel = I18n.s("撤销", "Undo"),
                            withDismissAction = true,
                            duration = androidx.compose.material3.SnackbarDuration.Short
                        )
                        if (res == SnackbarResult.ActionPerformed) {
                            vm.revertRecord(r.recordId)
                        }
                    }
                },
                onClose = {
                    showInbound = false
                    vm.resetInbound()
                }
            )
        }
    }

    // ==========================================================
    // 更新相关弹窗
    //
    // 挂在 MainScreen 而不是某个 Tab：更新提示与当前在看哪个页面无关，
    // 而且它可能在任意页面里被触发（设置面板里的手动检查）。
    // ==========================================================

    // 优先级最高：首次启动还没表态过
    when (settings.updateCheckEnabled) {
        null -> UpdateConsentDialog(
            onAllow = { vm.setUpdateCheckEnabled(true) },
            onDeny = { vm.setUpdateCheckEnabled(false) }
        )
        else -> Unit
    }

    // 下载中 / 校验中的状态优先于「发现新版本」
    when (val d = download) {
        is DownloadUiState.Running -> UpdateDownloadDialog(
            percent = d.percent,
            done = d.done,
            total = d.total,
            onCancel = { vm.cancelUpdateDownload() }
        )

        DownloadUiState.Verifying -> UpdateVerifyingDialog()

        is DownloadUiState.Failed -> UpdateFailedDialog(
            message = d.message,
            onRetry = {
                vm.dismissDownloadError()
                vm.startUpdateDownload()
            },
            onDismiss = { vm.dismissDownloadError() }
        )

        DownloadUiState.NeedInstallPermission -> UpdateNeedPermissionDialog(
            onOpenSettings = {
                vm.retryInstallAfterPermission()
            },
            onDismiss = { vm.dismissDownloadError() }
        )

        DownloadUiState.Idle -> {
            // 没有下载任务时才显示「发现新版本」
            val nv = settings.newVersion
            if (nv != null && settings.updateCheckEnabled != null) {
                NewVersionDialog(
                    info = nv,
                    currentVersion = settings.currentVersion,
                    onUpdate = { vm.startUpdateDownload() },
                    onLater = { vm.dismissUpdate() }
                )
            }
        }
    }
}

@Composable
private fun BottomTabBar(current: Int, onSelect: (Int) -> Unit) {
    val labels = listOf(I18n.s("库存", "Inventory"), I18n.s("统计", "Stats"))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(56.dp)
        ) {
            labels.forEachIndexed { i, label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        fontSize = 13.sp,
                        color = if (current == i) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
