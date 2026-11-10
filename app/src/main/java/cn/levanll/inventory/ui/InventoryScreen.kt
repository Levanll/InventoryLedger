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
import cn.levanll.inventory.i18n.I18n
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import cn.levanll.inventory.i18n.UnitNames
import cn.levanll.inventory.i18n.Lang
import cn.levanll.inventory.i18n.CategoryNames
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
                    I18n.s("库存", "Inventory"),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                if (state.inStockKinds > 0) {
                    Text(
                        I18n.s(
                            "${state.inStockKinds} 种 · ${fmtQty(state.totalQty)} 件",
                            "${state.inStockKinds} kinds · ${fmtQty(state.totalQty)} pcs"
                        ),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 语言切换：库存页直接可切，不必绕到统计页
                Text(
                    if (I18n.lang == Lang.ZH) "EN" else "中文",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { I18n.toggle() }
                        .padding(start = 10.dp, top = 6.dp, bottom = 6.dp, end = 2.dp)
                )
            }

            // 搜索
            OutlinedTextField(
                value = state.keyword,
                onValueChange = { vm.setKeyword(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(52.dp),
                placeholder = { Text(I18n.s("搜索名称或规格", "Search name or spec"), fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (state.keyword.isNotEmpty()) {
                        Icon(
                            Icons.Default.Close, I18n.s("清空", "Clear"),
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
                    item { Chip(I18n.s("全部", "All"), state.selectedCat == null) { vm.setCategory(null) } }
                    items(state.cats, key = { it.id }) { c: CategoryStat ->
                        Chip(CategoryNames.of(c.name), state.selectedCat == c.id) { vm.setCategory(c.id) }
                    }
                }
            }

            // 提示条：临期优先于低库存
            if (state.expiringCount > 0 && !dismissExpiring) {
                HintBar(
                    text = I18n.s(
                        "${state.expiringCount} 件临期或已过期",
                        "${state.expiringCount} expiring or expired"
                    ),
                    tint = MaterialTheme.colorScheme.error,
                    onDismiss = { dismissExpiring = true }
                )
            } else if (state.lowCount > 0 && !dismissLow) {
                HintBar(
                    text = I18n.s(
                        "${state.lowCount} 件库存偏低",
                        "${state.lowCount} low on stock"
                    ),
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
                    Text(I18n.s("最近：", "Recent: "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                // 只有「主列表和已用完都为空」才算真正空库存。
                // 若只看 items，把最后一个商品归零并选「保留记录」后，
                // 已用完分区会连同「恢复/删除」入口一起消失，
                // 界面反而显示「还没有商品」—— 正是本版本要修的那个问题。
                state.items.isEmpty() && state.usedUp.isEmpty() ->
                    EmptyState(I18n.s("还没有商品", "No items yet"), I18n.s("立即入库", "Add now")) { onOpenInbound(null) }
                else -> {
                    val listState = rememberLazyListState()

                    // items 已按分类树排好序（一级→二级→三级），
                    // groupBy 保持插入顺序，嵌套两层即得到正确的两级分组。
                    val groups = remember(state.items) {
                        state.items.groupBy { it.catL1 ?: "" }
                            .mapValues { (_, rows) -> rows.groupBy { it.catL2 ?: "" } }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 16.dp, end = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            // 底部留白 = FAB 底边距 88 + FAB 高度 56 + 呼吸 8 = 152。
                            // 原来是 96，只够让最后一项露出，但会被 FAB 压住右侧按钮
                            // （「已用完」行的「删除」就点不到）。
                            contentPadding = PaddingValues(top = 8.dp, bottom = 152.dp)
                        ) {
                            groups.forEach { (rootName, subGroups) ->
                                val allRows = subGroups.values.flatten()
                                val rootId = allRows.firstOrNull()?.catL1Id
                                val rootCollapsed = rootId != null && rootId in state.collapsedRoots

                                if (rootName.isNotEmpty()) {
                                    item(key = "h1_$rootName") {
                                        GroupHeader(
                                            title = CategoryNames.of(rootName),
                                            count = allRows.size,
                                            collapsed = rootCollapsed,
                                            level = 1,
                                            onToggle = { rootId?.let { vm.toggleGroup(it) } }
                                        )
                                    }
                                }

                                if (!rootCollapsed) {
                                    subGroups.forEach { (subName, rows) ->
                                        val subId = rows.firstOrNull()?.catL2Id
                                        val subCollapsed =
                                            subId != null && subId in state.collapsedRoots

                                        if (subName.isNotEmpty()) {
                                            item(key = "h2_${rootName}_$subName") {
                                                GroupHeader(
                                                    title = CategoryNames.of(subName),
                                                    count = rows.size,
                                                    collapsed = subCollapsed,
                                                    level = 2,
                                                    onToggle = { subId?.let { vm.toggleGroup(it) } }
                                                )
                                            }
                                        }

                                        if (!subCollapsed) {
                                            items(rows, key = { it.id }) { item ->
                                                // 商品行跟着二级分类缩进。
                                                // 不缩进会让商品卡片与一级标题左对齐，
                                                // 看起来像是「属于整个数码配件」而不是「属于线材」，
                                                // 层级关系就断了。
                                                Box(
                                                    modifier = Modifier.padding(start = 14.dp)
                                                ) {
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
                            }

                            // 「已用完」分区：数量归零但用户选择保留的商品。
                            //
                            // 放在主列表之后，默认折叠。这样「保留记录」有了可见的落点，
                            // 又不把历史条目混进「现在有什么」的主视图。
                            if (state.usedUp.isNotEmpty()) {
                                item(key = "usedup_header") {
                                    UsedUpHeader(
                                        count = state.usedUp.size,
                                        expanded = state.usedUpExpanded,
                                        onToggle = { vm.toggleUsedUp() }
                                    )
                                }
                                if (state.usedUpExpanded) {
                                    items(state.usedUp, key = { "used_${it.id}" }) { item ->
                                        Box(modifier = Modifier.padding(start = 14.dp)) {
                                            UsedUpRow(
                                                item = item,
                                                onRestore = {
                                                    vm.restoreUsedUp(item.id, item.name)
                                                },
                                                onDelete = {
                                                    vm.deleteZeroItem(item.id, item.name)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 滚动指示条：贴在列表右缘，不占布局宽度。
                        //
                        // 上沿对齐「最近」栏下方，下沿对齐「库存 / 统计」TabBar 上沿，
                        // 且上下留同样的 10dp —— 不等距会让滑块看起来偏下、像是没到底。
                        // 注意不能用列表的 contentPadding（152dp）：那个留白属于内容区，
                        // 是让最后一项能滚到 TabBar 上方，与滑块代表的「可视区进度」无关。
                        ScrollIndicator(
                            listState = listState,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 10.dp, bottom = 10.dp, end = 4.dp)
                        )
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
                Icon(Icons.Default.Add, I18n.s("入库", "Add Stock"), tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
    }

    // 归零删除确认
    val target = pendingDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(I18n.s("删除「${target.name}」？", "Delete ${target.name}?"), fontSize = 16.sp, fontWeight = FontWeight.Medium) },
            text = {
                Text(
                    I18n.s(
                        "数量已为 0，已从库存列表移除。\n是否同时删除该商品？\n\n",
                        "Quantity is 0, so it is hidden from the inventory list.\nDelete this item as well?\n\n"
                    ) +
                        I18n.s("删除后仍可在「统计 → 月度明细」的历史记录中查到它。", "It still appears in Stats -> Monthly details."),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteZeroItem(target.id, target.name)
                    pendingDelete = null
                }) { Text(I18n.s("删除", "Delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = {
                    // 归零写入发生在 adjustQty 的 refreshInventory() 之后，
                    // 那一刻 usedUp 还查不到这条。保留记录必须再刷一次，
                    // 否则用户点了「保留」却看不到任何东西。
                    vm.refreshInventory()
                    pendingDelete = null
                }) { Text(I18n.s("保留记录", "Keep record")) }
            }
        )
    }

    // 进入应用时的已过期提醒
    val expired = state.expiredOnLaunch
    if (expired.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { vm.dismissExpiredDialog() },
            title = {
                Text(I18n.s("有 ${expired.size} 件商品已过期", "${expired.size} items expired"), fontSize = 16.sp, fontWeight = FontWeight.Medium)
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
                                        CategoryNames.ofPath(it.categoryPath()),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                I18n.s("已过期 ${-(it.daysLeft ?: 0)} 天", "Expired ${-(it.daysLeft ?: 0)}d"),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.dismissExpiredDialog() }) { Text(I18n.s("知道了", "Got it")) }
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
            Text(I18n.s("偏低", "Low"), fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
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
            UnitNames.of(item.unit),
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
 *
 * 每个 Text 必须显式 maxLines = 1：Row 内的 Text 默认可以任意换行，
 * 英文分类名（如 Digital Accessories · Cables · Data Cable）比中文长得多，
 * 不加约束时每个词会被挤成竖排，整行变成一堆碎字。
 */
@Composable
private fun CategoryPathLine(item: ItemWithCategory) {
    val l1 = item.catL1
    val l2 = item.catL2
    val l3 = item.catL3
    if (l1 == null) return

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (l3 != null) {
            Text(
                CategoryNames.of(l1), fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(" · ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), maxLines = 1)
            Text(
                CategoryNames.of(l2 ?: ""), fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(" · ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), maxLines = 1)
            Text(
                CategoryNames.of(l3),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        } else if (l2 != null) {
            Text(
                CategoryNames.of(l1), fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(" · ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), maxLines = 1)
            Text(
                CategoryNames.of(l2),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        } else {
            Text(
                CategoryNames.of(l1), fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
    }
}

/** 保质期徽标：文字而非图标，因为「还剩 3 天」比任何图标都直接 */
@Composable
private fun ExpiryBadge(status: ExpiryStatus, daysLeft: Int?) {
    val (text, fg, bg) = when (status) {
        ExpiryStatus.EXPIRED ->
            Triple(I18n.s("已过期 ${-(daysLeft ?: 0)} 天", "Expired ${-(daysLeft ?: 0)}d"), Color(0xFFD32F2F), Color(0x1FD32F2F))
        ExpiryStatus.URGENT ->
            Triple(I18n.s("剩 ${daysLeft} 天", "${daysLeft}d left"), Color(0xFFD32F2F), Color(0x1FD32F2F))
        ExpiryStatus.SOON ->
            Triple(I18n.s("剩 ${daysLeft} 天", "${daysLeft}d left"), Color(0xFFE8940A), Color(0x1FE8940A))
        ExpiryStatus.NEAR ->
            Triple(I18n.s("剩 ${daysLeft} 天", "${daysLeft}d left"), Color(0xFF8A9A2B), Color(0x1F8A9A2B))
        ExpiryStatus.NORMAL ->
            Triple(
                I18n.s("剩 ${daysLeft} 天", "${daysLeft}d left"),
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

/**
 * 分类分组头，支持两级。
 *
 * 整行可点（不只是箭头），因为用户的意图是「收起这一类」，
 * 强迫他们精确点中 24dp 的箭头是无谓的门槛。
 *
 * 两级用视觉区分而不是加缩进：一级用实底、二级用浅底 + 左侧色条。
 * 缩进会让二级标题看起来像商品行，反而混淆层级。
 *
 * 二级原本是 alpha 0.25 底色配灰字，实测对比度只有一级的一半，
 * 在长列表里会被当成普通分隔线而完全被忽略（用户反馈「线材不显眼」）。
 * 现在二级加一条主色竖条作为锚点，文字提到 onSurface，
 * 底色提到 0.35 —— 仍是「次级」，但不会再被跳过。
 */
@Composable
private fun GroupHeader(
    title: String,
    count: Int,
    collapsed: Boolean,
    level: Int,
    onToggle: () -> Unit
) {
    val isL1 = level == 1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isL1) 44.dp else 40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isL1) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
            .clickable { onToggle() }
            .padding(start = if (isL1) 12.dp else 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 二级：左侧主色竖条，作为「这是一层分类」的视觉锚点
        if (!isL1) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
            )
            Spacer(Modifier.width(8.dp))
        }

        // 箭头：折叠时指向右，展开时指向下。旋转而非换图标，过渡更连贯
        val rotation by animateFloatAsState(
            targetValue = if (collapsed) -90f else 0f,
            animationSpec = tween(180),
            label = "groupArrow"
        )
        Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = if (collapsed) {
                I18n.s("展开 $title", "Expand $title")
            } else {
                I18n.s("折叠 $title", "Collapse $title")
            },
            tint = if (isL1) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            modifier = Modifier
                .size(if (isL1) 20.dp else 18.dp)
                .rotate(rotation)
        )

        Spacer(Modifier.width(if (isL1) 6.dp else 5.dp))

        Text(
            I18n.s("$title · $count 种", "$title · $count"),
            fontSize = if (isL1) 13.sp else 12.sp,
            fontWeight = if (isL1) FontWeight.Medium else FontWeight.Medium,
            color = if (isL1) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (collapsed) {
            Text(
                I18n.s("已折叠", "collapsed"),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


/**
 * 「已用完」分区头。
 *
 * 与分类分组头刻意区分：底色更淡、右侧标注「仅保留记录」，
 * 表示「这不是在库商品」。用户扫视时不应把它误认成又一个分类。
 */
@Composable
private fun UsedUpHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .clickable { onToggle() }
            .padding(start = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val rotation by animateFloatAsState(
            targetValue = if (expanded) 0f else -90f,
            animationSpec = tween(180),
            label = "usedUpArrow"
        )
        Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = if (expanded) {
                I18n.s("收起已用完", "Collapse used-up")
            } else {
                I18n.s("展开已用完", "Expand used-up")
            },
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(20.dp)
                .rotate(rotation)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            I18n.s("已用完 · $count 种", "Used up · $count"),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            I18n.s("仅保留记录", "Records only"),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/**
 * 「已用完」商品行。
 *
 * 与在库商品行的差别：
 *   - 数量固定显示 0，没有加减按钮（没有库存可加）
 *   - 用「恢复 / 删除」替代加减，这是这两个动作在这里唯一有意义的形式
 *   - 名称与底色都压暗，视觉上退到次级，不与在库商品抢注意力
 */
@Composable
private fun UsedUpRow(
    item: ItemWithCategory,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .padding(start = 14.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                item.name,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            CategoryPathLine(item)
        }

        Text(
            "0",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(32.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        TextButton(
            onClick = onRestore,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            modifier = Modifier.height(32.dp)
        ) {
            Text(
                I18n.s("恢复", "Restore"),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        TextButton(
            onClick = onDelete,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            modifier = Modifier.height(32.dp)
        ) {
            Text(
                I18n.s("删除", "Delete"),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
