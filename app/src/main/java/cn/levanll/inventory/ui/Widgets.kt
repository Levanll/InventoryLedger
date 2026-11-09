package cn.levanll.inventory.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 数量步进器：按钮热区 48dp，按单位步进 */
@Composable
fun QtyStepper(
    qty: Double,
    unit: String,
    min: Double,
    max: Double,
    onChange: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val step = cn.levanll.inventory.domain.Constants.stepOfUnit(unit)
    val canMinus = qty - step >= min
    val canPlus = qty + step <= max

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StepButton("－", canMinus, MaterialTheme.colorScheme.onSurface) {
            if (canMinus) onChange(cn.levanll.inventory.domain.round2(qty - step))
        }
        Text(
            text = fmtQty(qty),
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        StepButton("＋", canPlus, MaterialTheme.colorScheme.primary) {
            if (canPlus) onChange(cn.levanll.inventory.domain.round2(qty + step))
        }
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, tint: Color, onClick: () -> Unit) {
    // 用 Box + clickable 而不是 TextButton：TextButton 自带最小尺寸与内边距，
    // 套进固定尺寸的 Box 会把实际热区压到很小，导致点击不灵敏。
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 22.sp,
            color = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

fun fmtQty(v: Double): String {
    return if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString()
    else (Math.round(v * 100.0) / 100.0).toString()
}

/** 区块标题 */
@Composable
fun SectionLabel(text: String, required: Boolean = false) {
    Row(modifier = Modifier.padding(bottom = 6.dp)) {
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (required) {
            Text(" *", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** 空状态 */
@Composable
fun EmptyState(title: String, action: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onAction) {
            Text(
                action,
                fontSize = 15.sp,
                color = Color.White,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            )
        }
    }
}

/** 确认对话框 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium) },
        text = { Text(message, fontSize = 13.sp) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
