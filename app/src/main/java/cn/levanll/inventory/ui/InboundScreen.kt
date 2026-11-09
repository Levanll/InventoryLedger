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
import cn.levanll.inventory.i18n.I18n
import cn.levanll.inventory.i18n.UnitNames
import cn.levanll.inventory.i18n.CategoryNames
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
                        Icons.Default.ArrowBack, I18n.s("返回", "Back"),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    I18n.s("入库", "Add Stock"),
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
                        if (s.saving) I18n.s("保存中…", "Saving…") else I18n.s("保存", "Save"),
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
                        Icons.Default.QrCodeScanner, I18n.s("扫码", "Scan"),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            I18n.s("扫码入库", "Scan to add"),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (s.barcode != null) {
                            Text(
                                I18n.s("条码 ${s.barcode}", "Barcode ${s.barcode}"),
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
                            I18n.s("正在查询商品信息…", "Looking up product…"),
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
                            I18n.s("查到商品信息", "Product found"),
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
                                I18n.s("规格 ${sug.spec}", "Spec ${sug.spec}"),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (sug.categoryHint != null) {
                            Text(
                                I18n.s("参考分类 ${sug.categoryHint}", "Suggested ${CategoryNames.ofPath(sug.categoryHint ?: "")}"),
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
                                Text(I18n.s("采用", "Use"), fontSize = 14.sp, color = Color.White)
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
                                    I18n.s("手动填写", "Enter manually"),
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Text(
                            I18n.s("商品库数据可能不准确，采用后可继续修改", "Product data may be inaccurate; you can edit after using"),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 名称
                Column {
                    SectionLabel(I18n.s("名称", "Name"), required = true)
                    OutlinedTextField(
                        value = s.name,
                        onValueChange = { vm.inboundNameChanged(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(I18n.s("例如：牛奶", "e.g. Milk"), fontSize = 15.sp) },
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
                                        CategoryNames.ofPath(item.categoryPath()),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    I18n.s("已存 ${fmtQty(item.quantity)}", "In stock ${fmtQty(item.quantity)}"),
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
                            I18n.s("追加到已有商品；改名会提示确认", "Appending to an existing item; renaming asks for confirmation"),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            I18n.s("改为新建", "Create new instead"),
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
                    SectionLabel(I18n.s("规格", "Spec"))
                    OutlinedTextField(
                        value = s.spec,
                        onValueChange = { vm.inboundSpecChanged(it) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = s.existingItemId == null,
                        placeholder = { Text(I18n.s("例如：全脂 250ml", "e.g. Whole 250ml"), fontSize = 15.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // 单位
                Column {
                    SectionLabel(I18n.s("单位", "Unit"))
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
                        Text(UnitNames.of(s.unit), fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text("▾", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // 数量
                Column {
                    SectionLabel(I18n.s("数量", "Quantity"), required = true)
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
                            I18n.s("分类", "Category"), fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        if (s.needConfirm && s.categoryId != null) {
                            Text(I18n.s("请确认", "Please confirm"), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        } else if (s.categoryId == null) {
                            Text(
                                I18n.s("未自动识别", "Not detected"), fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            I18n.s("修改 ›", "Change ›"), fontSize = 13.sp,
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
                                s.categoryPath.ifEmpty { I18n.s("未分类", "Uncategorized") },
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
                                I18n.s("启用保质期", "Track expiry"),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                if (s.expiryEnabled) I18n.s("到期后会在列表中提醒", "Will alert in the list when expiring")
                                else I18n.s("默认关闭，需要时打开", "Off by default, turn on when needed"),
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
                                    I18n.s("生产日期", "Production date"),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(72.dp)
                                )
                                Text(
                                    s.productionDate?.let { fmtDate(it) } ?: I18n.s("未设置", "Not set"),
                                    fontSize = 14.sp,
                                    color = if (s.productionDate == null)
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    Icons.Default.DateRange, I18n.s("选择日期", "Select date"),
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
                                    I18n.s("保质期", "Shelf life"),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(72.dp)
                                )
                                OutlinedTextField(
                                    value = s.shelfLifeDays?.toString() ?: "",
                                    onValueChange = { v -> vm.setShelfLifeDays(v.toIntOrNull()) },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text(I18n.s("天数", "days"), fontSize = 14.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    I18n.s("天", "days"),
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
                                            I18n.s("$d 天", "$d d"),
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
                                    if (left >= 0) {
                                        I18n.s(
                                            "预计 ${fmtDate(expiry)} 到期（还剩 $left 天）",
                                            "Expires ${fmtDate(expiry)} ($left days left)"
                                        )
                                    } else {
                                        I18n.s(
                                            "预计 ${fmtDate(expiry)} 到期（已过期 ${-left} 天）",
                                            "Expires ${fmtDate(expiry)} (expired ${-left} days)"
                                        )
                                    },
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
                    SectionLabel(I18n.s("低库存提醒（可选）", "Low stock alert (optional)"))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            I18n.s("低于", "Below"), fontSize = 14.sp,
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
                            UnitNames.of(s.unit), fontSize = 14.sp,
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
                title = { Text(I18n.s("选择单位", "Select unit"), fontSize = 16.sp) },
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
                        Text(I18n.s("选择分类", "Select category"), fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Text(
                            I18n.s("＋ 新建", "+ New"),
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
                                    CategoryNames.of(c.name),
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
                title = { Text(I18n.s("该条码已有记录", "This barcode already exists"), fontSize = 16.sp, fontWeight = FontWeight.Medium) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            I18n.s("条码 ${conflict.barcode}", "Barcode ${conflict.barcode}"),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "已有商品：${conflict.oldName}\n" +
                                "你改为：${conflict.newName}\n\n" +
                                I18n.s("这个条码以后关联哪个商品？", "Which item should this barcode link to?"),
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
                                I18n.s("改为新名称（旧条目同步改名）", "Use new name (rename the old item too)"),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        TextButton(
                            onClick = { vm.resolveConflictAsNewItem() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(I18n.s("这是另一件商品，新建条目", "This is a different item, create new"), color = MaterialTheme.colorScheme.primary)
                        }
                        TextButton(
                            onClick = { vm.resolveConflictKeepOld() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(I18n.s("沿用原名", "Keep the old name"), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    }) { Text(I18n.s("确定", "OK")) }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text(I18n.s("取消", "Cancel")) }
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
                            if (cmState.parentPath.isEmpty()) I18n.s("新建一级分类", "New level-1 category")
                            else I18n.s("在「${cmState.parentPath}」下新建", "New under ${cmState.parentPath}")
                        } else I18n.s("重命名分类", "Rename category"),
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
                            placeholder = { Text(I18n.s("分类名称", "Category name"), fontSize = 14.sp) },
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
                    TextButton(onClick = { vm.submitCategoryEdit() }) { Text(I18n.s("保存", "Save")) }
                },
                dismissButton = {
                    TextButton(onClick = { vm.closeCategoryEditor() }) { Text(I18n.s("取消", "Cancel")) }
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
                title = { Text(I18n.s("已存在同规格商品", "An item with the same spec exists"), fontSize = 16.sp) },
                text = {
                        Text(
                            I18n.s(
                                "${s.name.trim()} 当前有 ${fmtQty(existingQty)} $dupUnit",
                                "${s.name.trim()} currently has ${fmtQty(existingQty)} $dupUnit"
                            )
                        )
                    },
                confirmButton = {
                    TextButton(onClick = {
                        vm.inboundChooseAppend { if (it != null) onSaved(it) }
                    }) { Text(I18n.s("追加数量", "Add quantity")) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        vm.inboundDismissDup()
                        vm.inboundSave(true) { if (it != null) onSaved(it) }
                    }) { Text(I18n.s("另建规格", "Create separate spec")) }
                }
            )
        }
    }
}

private fun fmtDate(ms: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(ms))
