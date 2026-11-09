package cn.levanll.inventory.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.levanll.inventory.domain.Constants
import cn.levanll.inventory.domain.InboundResult
import cn.levanll.inventory.domain.startOfDay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 入库页
 *
 * 能力：
 *   1. 顶部扫码入口（相机权限运行时申请）
 *   2. 保质期区块（默认关闭，开启后填生产日期 + 保质期天数）
 *   3. 三级分类选择器（显示完整路径，缩进表达层级，可新建）
 *   4. 条码改名冲突的三选项（沿用原名 / 改为新名 / 新建条目）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboundScreen(
    vm: MainViewModel,
    onSaved: (InboundResult) -> Unit,
    onClose: () -> Unit
) {
    val s by vm.inbound.collectAsState()
    val cmState by vm.catManage.collectAsState()
    var showUnitPicker by remember { mutableStateOf(false) }
    var showCatPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    // 扫码全屏覆盖
    if (s.showScanner) {
        ScanScreen(
            onScanned = { code -> vm.onBarcodeScanned(code) },
            onClose = { vm.closeScanner() }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(start = 4.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(48.dp).clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.ArrowBack, "返回",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    "入库",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                val valid = s.name.trim().isNotEmpty() && s.qty > 0 && !s.saving
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = valid) {
                            vm.inboundSave(false) { if (it != null) onSaved(it) }
                        }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (s.saving) "保存中…" else "保存",
                        fontSize = 15.sp,
                        color = if (valid) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ---- 扫码入口 ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { vm.openScanner() }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.QrCodeScanner, "扫码",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "扫码入库",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (s.barcode != null) {
                            Text(
                                "条码 ${s.barcode}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text("›", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // ---- 扫码查到的商品建议 ----
                val sug = s.scanSuggestion
                if (s.lookingUp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "正在查询商品信息…",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (sug != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "查到商品信息",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            if (sug.brand != null) "${sug.brand} ${sug.name}" else sug.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (sug.spec != null) {
                            Text(
                                "规格 ${sug.spec}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (sug.categoryHint != null) {
                            Text(
                                "参考分类 ${sug.categoryHint}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(19.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .clickable { vm.applyScanSuggestion() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("采用", fontSize = 14.sp, color = Color.White)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(19.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { vm.dismissScanSuggestion() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "手动填写",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Text(
                            "商品库数据可能不准确，采用后可继续修改",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 名称
                Column {
                    SectionLabel("名称", required = true)
                    OutlinedTextField(
                        value = s.name,
                        onValueChange = { vm.inboundNameChanged(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("例如：牛奶", fontSize = 15.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            if (s.name.isNotEmpty()) {
                                Text(
                                    "✕",
                                    fontSize = 14.sp,
                                    modifier = Modifier
                                        .clickable { vm.inboundNameChanged("") }
                                        .padding(8.dp)
                                )
                            }
                        }
                    )
                }

                // 联想面板
                if (s.suggests.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        s.suggests.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .clickable { vm.inboundUseSuggestion(item) }
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        item.name, fontSize = 15.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        item.categoryPath(),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    "已存 ${fmtQty(item.quantity)}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // 追加模式提示
                if (s.existingItemId != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x1A007DFF), RoundedCornerShape(8.dp))
                            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "追加到已有商品；改名会提示确认",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "改为新建",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clickable { vm.exitAppendMode() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // 规格
                Column {
                    SectionLabel("规格")
                    OutlinedTextField(
                        value = s.spec,
                        onValueChange = { vm.inboundSpecChanged(it) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = s.existingItemId == null,
                        placeholder = { Text("例如：全脂 250ml", fontSize = 15.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // 单位
                Column {
                    SectionLabel("单位")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { if (s.existingItemId == null) showUnitPicker = true }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(s.unit, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text("▾", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // 数量
                Column {
                    SectionLabel("数量", required = true)
                    QtyStepper(
                        qty = s.qty,
                        unit = s.unit,
                        min = 1.0,
                        max = Constants.QTY_MAX,
                        onChange = { vm.inboundQtyChanged(it) }
                    )
                }

                // 分类
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "分类", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        if (s.needConfirm && s.categoryId != null) {
                            Text("请确认", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        } else if (s.categoryId == null) {
                            Text(
                                "未自动识别", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "修改 ›", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { showCatPicker = true }
                                .padding(start = 8.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    if (s.categoryId != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                s.categoryPath.ifEmpty { "未分类" },
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // ---- 保质期 ----
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "启用保质期",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                if (s.expiryEnabled) "到期后会在列表中提醒"
                                else "默认关闭，需要时打开",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = s.expiryEnabled,
                            onCheckedChange = { vm.setExpiryEnabled(it) }
                        )
                    }

                    if (s.expiryEnabled) {
                        Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // 生产日期
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .clickable { showDatePicker = true }
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "生产日期",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(72.dp)
                                )
                                Text(
                                    s.productionDate?.let { fmtDate(it) } ?: "未设置",
                                    fontSize = 14.sp,
                                    color = if (s.productionDate == null)
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    Icons.Default.DateRange, "选择日期",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // 保质期天数
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 14.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "保质期",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(72.dp)
                                )
                                OutlinedTextField(
                                    value = s.shelfLifeDays?.toString() ?: "",
                                    onValueChange = { v -> vm.setShelfLifeDays(v.toIntOrNull()) },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text("天数", fontSize = 14.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "天",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // 快捷预设
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(7, 30, 90, 180, 365).forEach { d ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable { vm.setShelfLifeDays(d) }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            "$d 天",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            val prod = s.productionDate
                            val days = s.shelfLifeDays
                            if (prod != null && days != null && days > 0) {
                                val expiry = prod + days.toLong() * 86400000L
                                val left = ((expiry - startOfDay(System.currentTimeMillis())) / 86400000L).toInt()
                                Text(
                                    "预计 ${fmtDate(expiry)} 到期" +
                                        if (left >= 0) "（还剩 $left 天）" else "（已过期 ${-left} 天）",
                                    fontSize = 12.sp,
                                    color = if (left < 7) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 低库存阈值
                Column {
                    SectionLabel("低库存提醒（可选）")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "低于", fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = if (s.lowThreshold > 0) fmtQty(s.lowThreshold) else "",
                            onValueChange = { vm.inboundThresholdChanged(it.toDoubleOrNull() ?: 0.0) },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            s.unit, fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(48.dp))
            }
        }

        // ---- 单位选择器 ----
        if (showUnitPicker) {
            AlertDialog(
                onDismissRequest = { showUnitPicker = false },
                title = { Text("选择单位", fontSize = 16.sp) },
                text = {
                    Column {
                        Constants.UNITS.chunked(3).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row.forEach { u ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .clip(RoundedCornerShape(19.dp))
                                            .background(
                                                if (u == s.unit) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            .clickable {
                                                vm.inboundUnitChanged(u)
                                                showUnitPicker = false
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            u, fontSize = 14.sp,
                                            color = if (u == s.unit) Color.White
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }

        // ---- 三级分类选择器 ----
        if (showCatPicker) {
            AlertDialog(
                onDismissRequest = { showCatPicker = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("选择分类", fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Text(
                            "＋ 新建",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clickable {
                                    showCatPicker = false
                                    vm.openCreateCategory(null, "")
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        s.allCats.forEach { c ->
                            val indent = (c.depth - 1) * 16
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .clickable {
                                        vm.inboundPickCategory(c.id)
                                        showCatPicker = false
                                    }
                                    .padding(start = indent.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    c.name,
                                    fontSize = if (c.depth == 1) 15.sp else 14.sp,
                                    fontWeight = if (c.depth == 1) FontWeight.Medium
                                    else FontWeight.Normal,
                                    color = when (c.depth) {
                                        1 -> MaterialTheme.colorScheme.onSurface
                                        2 -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                // 二级分类右侧提供「在此下新建三级」
                                if (c.depth == 2) {
                                    Text(
                                        "＋",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clickable {
                                                showCatPicker = false
                                                vm.openCreateCategory(c.id, c.pathName)
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                if (c.id == s.categoryId) {
                                    Text(
                                        "✓",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }

        // ---- 条码改名冲突：三选项 ----
        val conflict = s.nameConflict
        if (conflict != null) {
            AlertDialog(
                onDismissRequest = { vm.dismissNameConflict() },
                title = { Text("该条码已有记录", fontSize = 16.sp, fontWeight = FontWeight.Medium) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "条码 ${conflict.barcode}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "已有商品：${conflict.oldName}\n" +
                                "你改为：${conflict.newName}\n\n" +
                                "这个条码以后关联哪个商品？",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                confirmButton = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextButton(
                            onClick = { vm.resolveConflictUseNew() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "改为新名称（旧条目同步改名）",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        TextButton(
                            onClick = { vm.resolveConflictAsNewItem() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("这是另一件商品，新建条目", color = MaterialTheme.colorScheme.primary)
                        }
                        TextButton(
                            onClick = { vm.resolveConflictKeepOld() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("沿用原名", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            )
        }

        // ---- 生产日期选择 ----
        if (showDatePicker) {
            val pickerState = rememberDatePickerState(
                initialSelectedDateMillis = s.productionDate ?: System.currentTimeMillis()
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        pickerState.selectedDateMillis?.let { vm.setProductionDate(it) }
                        showDatePicker = false
                    }) { Text("确定") }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("取消") }
                }
            ) {
                DatePicker(state = pickerState)
            }
        }

        // ---- 分类新建/重命名弹窗 ----
        if (cmState.editing) {
            AlertDialog(
                onDismissRequest = { vm.closeCategoryEditor() },
                title = {
                    Text(
                        if (cmState.editingId == null) {
                            if (cmState.parentPath.isEmpty()) "新建一级分类"
                            else "在「${cmState.parentPath}」下新建"
                        } else "重命名分类",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = cmState.inputName,
                            onValueChange = { vm.setCategoryInput(it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("分类名称", fontSize = 14.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            isError = cmState.error != null
                        )
                        val err = cmState.error
                        if (err != null) {
                            Text(err, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { vm.submitCategoryEdit() }) { Text("保存") }
                },
                dismissButton = {
                    TextButton(onClick = { vm.closeCategoryEditor() }) { Text("取消") }
                }
            )
        }

        // ---- 同名同规格冲突 ----
        val dup = s.dupPayload
        if (dup != null) {
            val existingQty = (dup["existingQty"] as? Double)
                ?: (dup["existingQty"] as? Number)?.toDouble() ?: 0.0
            val dupUnit = (dup["unit"] as? String) ?: s.unit
            AlertDialog(
                onDismissRequest = { vm.inboundDismissDup() },
                title = { Text("已存在同规格商品", fontSize = 16.sp) },
                text = { Text("${s.name.trim()} 当前有 ${fmtQty(existingQty)} $dupUnit") },
                confirmButton = {
                    TextButton(onClick = {
                        vm.inboundChooseAppend { if (it != null) onSaved(it) }
                    }) { Text("追加数量") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        vm.inboundDismissDup()
                        vm.inboundSave(true) { if (it != null) onSaved(it) }
                    }) { Text("另建规格") }
                }
            )
        }
    }
}

private fun fmtDate(ms: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(ms))
