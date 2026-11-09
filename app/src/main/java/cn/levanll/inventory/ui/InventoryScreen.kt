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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.levanll.inventory.data.dao.CategoryStat
import cn.levanll.inventory.data.dao.ItemWithCategory
import cn.levanll.inventory.domain.Constants
import cn.levanll.inventory.domain.ExpiryStatus

/**
 * 库存总览
 *
 * 三个视觉层次的取舍：
 *   1. 分类路径用「·」分隔且逐级变浅，用户一眼能看出层级
 *   2. 保质期做成右侧徽标而非整行变色，避免列表变成一片红黄
 *   3. 数量与加减按钮固定在右侧，形成垂直对齐的视觉柱，扫视效率高
 */
@Composable
fun InventoryScreen(
    vm: MainViewModel,
    onOpenInbound: (Long?) -> Unit
) {
    val state by vm.inventory.collectAsState()
    var dismissLow by remember { mutableStateOf(false) }
    var dismissExpiring by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ItemWithCategory?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "库存",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                if (state.inStockKinds > 0) {
                    Text(
                        "${state.inStockKinds} 种 · ${fmtQty(state.totalQty)} 件",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 搜索
            OutlinedTextField(
                value = state.keyword,
                onValueChange = { vm.setKeyword(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(52.dp),
                placeholder = { Text("搜索名称或规格", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (state.keyword.isNotEmpty()) {
                        Icon(
                            Icons.Default.Close, "清空",
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { vm.setKeyword("") }
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )

            Spacer(Modifier.height(8.dp))

            // 一级分类 chips
            if (state.cats.size >= 2) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Chip("全部", state.selectedCat == null) { vm.setCategory(null) } }
                    items(state.cats, key = { "${it.id}_${it.count}" }) { c: CategoryStat ->
                        Chip(c.name, state.selectedCat == c.id) { vm.setCategory(c.id) }
                    }
                }
            }

            // 提示条：临期优先于低库存
            if (state.expiringCount > 0 && !dismissExpiring) {
                HintBar(
                    text = "${state.expiringCount} 件临期或已过期",
                    tint = MaterialTheme.colorScheme.error,
                    onDismiss = { dismissExpiring = true }
                )
            } else if (state.lowCount > 0 && !dismissLow) {
                HintBar(
                    text = "${state.lowCount} 件库存偏低",
                    tint = Color(0xFFE8940A),
                    onDismiss = { dismissLow = true }
                )
            }

            if (state.recent.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("最近：", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.recent.forEach { r ->
                        Text(
                            r.name,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                                .clickable { onOpenInbound(r.id) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            when {
                state.loading -> LoadingSkeleton()
                state.items.isEmpty() -> EmptyState("还没有商品", "立即入库") { onOpenInbound(null) }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
                    ) {
                        items(state.items, key = { "${it.id}_${it.updatedAt}" }) { item ->
                            ItemRowWithStepper(
                                item = item,
                                onOpen = { onOpenInbound(item.id) },
                                onMinus = {
                                    val step = Constants.stepOfUnit(item.unit)
                                    vm.adjustQty(item.id, -step) { zero -> pendingDelete = zero }
                                },
                                onPlus = {
                                    val step = Constants.stepOfUnit(item.unit)
                                    vm.adjustQty(item.id, step)
                                }
                            )
                        }
                    }
                }
            }
        }

        // FAB
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 88.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable { onOpenInbound(null) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, "入库", tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
    }

    // 归零删除确认
    val target = pendingDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除「${target.name}」？", fontSize = 16.sp, fontWeight = FontWeight.Medium) },
            text = {
                Text(
                    "数量已为 0，已从库存列表移除。\n是否同时删除该商品？\n\n" +
                        "删除后仍可在「统计 → 月度明细」的历史记录中查到它。",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteZeroItem(target.id, target.name)
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("保留记录") }
            }
        )
    }

    // 进入应用时的已过期提醒
    val expired = state.expiredOnLaunch
    if (expired.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { vm.dismissExpiredDialog() },
            title = {
                Text("有 ${expired.size} 件商品已过期", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    expired.forEach { it ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    it.name,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (it.categoryPath().isNotEmpty()) {
                                    Text(
                                        it.categoryPath(),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                "已过期 ${-(it.daysLeft ?: 0)} 天",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.dismissExpiredDialog() }) { Text("知道了") }
            }
        )
    }
}

@Composable
private fun HintBar(text: String, tint: Color, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(tint.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontSize = 13.sp, color = tint, modifier = Modifier.weight(1f))
        Text(
            "✕", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable { onDismiss() }.padding(4.dp)
        )
    }
}

/**
 * 商品行。
 * 布局：左侧信息区（可点进详情）/ 右侧固定宽度的步进区
 * 保质期徽标放在名称右侧，扫视时形成一列。
 */
@Composable
private fun ItemRowWithStepper(
    item: ItemWithCategory,
    onOpen: () -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    val isLow = item.lowThreshold > 0 && item.quantity <= item.lowThreshold
    val step = Constants.stepOfUnit(item.unit)
    val status = ExpiryStatus.of(item.daysLeft)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 14.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { onOpen() },
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.name,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (status != ExpiryStatus.NONE) {
                    Spacer(Modifier.width(8.dp))
                    ExpiryBadge(status, item.daysLeft)
                }
            }

            CategoryPathLine(item)

            if (item.spec.isNotEmpty()) {
                Text(
                    item.spec,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (isLow) {
            Text("偏低", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(4.dp))
        }

        StepCircle("−", enabled = item.quantity - step >= 0) { onMinus() }

        Text(
            fmtQty(item.quantity),
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = if (isLow) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(46.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        StepCircle("＋", enabled = true) { onPlus() }

        Text(
            item.unit,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 3.dp).width(24.dp)
        )
    }
}

/**
 * 分类路径行。
 * 用「·」而不是「/」，因为斜杠在中文语境里容易被读成「或者」。
 * 层级用字号和颜色递减表达：一级最淡、三级最实。
 */
@Composable
private fun CategoryPathLine(item: ItemWithCategory) {
    val l1 = item.catL1
    val l2 = item.catL2
    val l3 = item.catL3
    if (l1 == null) return

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (l3 != null) {
            Text(l1, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            Text(" · ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            Text(l2 ?: "", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
            Text(" · ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            Text(
                l3,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        } else if (l2 != null) {
            Text(l1, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            Text(" · ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            Text(
                l2,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        } else {
            Text(l1, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
        }
    }
}

/** 保质期徽标：文字而非图标，因为「还剩 3 天」比任何图标都直接 */
@Composable
private fun ExpiryBadge(status: ExpiryStatus, daysLeft: Int?) {
    val (text, fg, bg) = when (status) {
        ExpiryStatus.EXPIRED ->
            Triple("已过期 ${-(daysLeft ?: 0)} 天", Color(0xFFD32F2F), Color(0x1FD32F2F))
        ExpiryStatus.URGENT ->
            Triple("剩 ${daysLeft} 天", Color(0xFFD32F2F), Color(0x1FD32F2F))
        ExpiryStatus.SOON ->
            Triple("剩 ${daysLeft} 天", Color(0xFFE8940A), Color(0x1FE8940A))
        ExpiryStatus.NEAR ->
            Triple("剩 ${daysLeft} 天", Color(0xFF8A9A2B), Color(0x1F8A9A2B))
        ExpiryStatus.NORMAL ->
            Triple(
                "剩 ${daysLeft} 天",
                MaterialTheme.colorScheme.onSurfaceVariant,
                MaterialTheme.colorScheme.surfaceVariant
            )
        ExpiryStatus.NONE -> Triple("", Color.Transparent, Color.Transparent)
    }
    if (text.isEmpty()) return

    Text(
        text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        color = fg,
        maxLines = 1,
        modifier = Modifier
            .background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    )
}

@Composable
private fun StepCircle(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 19.sp,
            color = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = if (active) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun LoadingSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
            )
        }
    }
}
