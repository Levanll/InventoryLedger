package cn.levanll.inventory.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.levanll.inventory.domain.DetailRow
import cn.levanll.inventory.domain.MonthItemRow
import cn.levanll.inventory.domain.YearMonth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 月度统计（「统计」页）
 *
 * 两个视角，共用月份选择器：
 *   1. 月度库存量：这个月进、出、净变化、月末结存
 *   2. 月度消耗：这个月用掉了多少，按消耗量倒序
 *
 * 都支持下钻到单商品流水。
 */
@Composable
fun StatsScreen(vm: MainViewModel) {
    val s by vm.statsState.collectAsState()
    var showMonthPicker by remember { mutableStateOf(false) }
    var showCatManage by remember { mutableStateOf(false) }

    if (showCatManage) {
        CategoryManageScreen(vm = vm, onClose = { showCatManage = false })
        return
    }

    // 下钻页优先
    if (s.detailRows.isNotEmpty() || s.showAllDetails) {
        DetailScreen(
            title = s.detailItemName,
            month = s.month,
            rows = s.detailRows,
            onBack = { vm.closeDetail() }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 标题
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "统计",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            Text(
                "分类管理",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable {
                        vm.loadCategoryManage()
                        showCatManage = true
                    }
                    .padding(8.dp)
            )
        }

        // Tab 切换
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            StatsTabItem("月度库存量", s.tab == StatsTab.INVENTORY, Modifier.weight(1f)) {
                vm.setStatsTab(StatsTab.INVENTORY)
            }
            StatsTabItem("月度消耗", s.tab == StatsTab.CONSUMPTION, Modifier.weight(1f)) {
                vm.setStatsTab(StatsTab.CONSUMPTION)
            }
        }

        Spacer(Modifier.height(12.dp))

        // 月份切换
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.KeyboardArrowLeft, "上月",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(36.dp)
                    .clickable { vm.prevMonth() }
                    .padding(4.dp)
            )
            Text(
                s.month.label(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clickable { showMonthPicker = true }
            )
            val canNext = s.month.next() <= YearMonth.now()
            Icon(
                Icons.Default.KeyboardArrowRight, "下月",
                tint = if (canNext) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(36.dp)
                    .clickable(enabled = canNext) { vm.nextMonth() }
                    .padding(4.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        if (s.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                OverviewCard(s, onOpenAll = { vm.openMonthDetails() })
            }

            if (s.rows.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            if (s.tab == StatsTab.INVENTORY) "本月还没有出入库记录"
                            else "本月还没有消耗记录",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "在库存页用 ＋/− 调整数量，或入库后就会出现在这里",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(
                    s.rows,
                    key = { "${it.itemId}_${s.month.year}_${s.month.month}_${s.tab}" }
                ) { r ->
                    MonthRow(r, s.tab) { vm.openItemDetail(r.itemId, r.itemName) }
                }
            }
        }
    }

    if (showMonthPicker) {
        MonthPickerDialog(
            months = s.availableMonths,
            current = s.month,
            onPick = {
                vm.setMonth(it)
                showMonthPicker = false
            },
            onDismiss = { showMonthPicker = false }
        )
    }
}

@Composable
private fun StatsTabItem(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OverviewCard(s: StatsUiState, onOpenAll: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            if (s.tab == StatsTab.INVENTORY) {
                StatCell("${fmtQty(s.overview.inQty)}", "入库合计", MaterialTheme.colorScheme.primary)
                StatCell("${fmtQty(s.overview.outQty)}", "出库合计", MaterialTheme.colorScheme.error)
                StatCell("${s.overview.activeItems}", "涉及品种", MaterialTheme.colorScheme.onSurface)
            } else {
                StatCell("${fmtQty(s.overview.outQty)}", "消耗合计", MaterialTheme.colorScheme.error)
                StatCell(
                    s.momPercent?.let { p ->
                        if (p >= 0) "↑${fmtQty(p)}%" else "↓${fmtQty(-p)}%"
                    } ?: "—",
                    "较上月",
                    if ((s.momPercent ?: 0.0) > 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
                StatCell("${s.overview.activeItems}", "涉及品种", MaterialTheme.colorScheme.onSurface)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenAll() }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${s.month.label()}全部流水（${s.overview.inCount + s.overview.outCount} 条）",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Text("›", fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun StatCell(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MonthRow(r: MonthItemRow, tab: StatsTab, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                r.itemName,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (tab == StatsTab.INVENTORY) {
                Text(
                    "入库 ${fmtQty(r.inQty)} · 出库 ${fmtQty(r.outQty)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "${r.recordCount} 次出库",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (tab == StatsTab.INVENTORY) "结存 ${fmtQty(r.endQty)}"
                else "消耗 ${fmtQty(r.outQty)}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (tab == StatsTab.CONSUMPTION) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            )
            Text("${r.unit}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "›",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

@Composable
private fun MonthPickerDialog(
    months: List<YearMonth>,
    current: YearMonth,
    onPick: (YearMonth) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择月份", fontSize = 16.sp, fontWeight = FontWeight.Medium) },
        text = {
            if (months.isEmpty()) {
                Text("暂无历史记录", fontSize = 13.sp)
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    months.forEach { m ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clickable { onPick(m) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                m.label(),
                                fontSize = 15.sp,
                                color = if (m == current) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (m == current) {
                                Text(
                                    "✓",
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}

/** 明细页：某商品在某月的全部流水，或某月全部流水 */
@Composable
private fun DetailScreen(
    title: String,
    month: YearMonth,
    rows: List<DetailRow>,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "‹",
                fontSize = 26.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clickable { onBack() }
                    .padding(horizontal = 12.dp)
            )
            Column {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    month.label(),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "该月没有记录",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(rows, key = { it.id }) { d ->
                DetailRowItem(d)
            }
        }
    }
}

@Composable
private fun DetailRowItem(d: DetailRow) {
    val typeText = when (d.type) {
        "IN" -> "入库"
        "OUT" -> "出库"
        "ADJUST" -> "调整"
        else -> d.type
    }
    val tint = when (d.type) {
        "IN" -> MaterialTheme.colorScheme.primary
        "OUT" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val reverted = d.revertedBy != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    d.itemName,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (reverted) {
                    Text(
                        "  已撤销",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                fmtDateTime(d.createdAt) + if (d.note != null) " · ${d.note}" else "",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(typeText, fontSize = 11.sp, color = tint)
            Text(
                (if (d.delta > 0) "+" else "") + fmtQty(d.delta) + " " + d.unit,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (reverted) MaterialTheme.colorScheme.onSurfaceVariant else tint
            )
        }
    }
}

private fun fmtDateTime(ms: Long): String =
    SimpleDateFormat("M月d日 HH:mm", Locale.getDefault()).format(Date(ms))
