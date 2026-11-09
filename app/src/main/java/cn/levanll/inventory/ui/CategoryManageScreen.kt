package cn.levanll.inventory.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.levanll.inventory.data.dao.CategoryPick

/**
 * 分类管理页。
 *
 * 系统预置分类与用户自建分类能力完全平等：都能改名、删除、在下面新建子类。
 * 唯一约束是三级上限，避免分类树无限膨胀。
 */
@Composable
fun CategoryManageScreen(vm: MainViewModel, onClose: () -> Unit) {
    val st by vm.catManage.collectAsState()
    var pendingDelete by remember { mutableStateOf<CategoryPick?>(null) }

    LaunchedEffect(Unit) { vm.loadCategoryManage() }

    Column(modifier = Modifier.fillMaxSize()) {
        // 标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = 8.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "‹",
                fontSize = 26.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clickable { onClose() }
                    .padding(horizontal = 10.dp)
            )
            Text(
                "分类管理",
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                "＋ 一级",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { vm.openCreateCategory(null, "") }
                    .padding(8.dp)
            )
        }

        Text(
            "点分类可重命名；右侧 ＋ 在其下新建子分类",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(st.cats, key = { "${it.id}_${it.name}" }) { c ->
                CatRow(
                    c = c,
                    onRename = { vm.openRenameCategory(c.id, c.name) },
                    onCreateChild = { vm.openCreateCategory(c.id, c.pathName) },
                    onDelete = { pendingDelete = c }
                )
            }
        }
    }

    // 新建/重命名弹窗
    if (st.editing) {
        AlertDialog(
            onDismissRequest = { vm.closeCategoryEditor() },
            title = {
                Text(
                    when {
                        st.editingId != null -> "重命名分类"
                        st.parentPath.isEmpty() -> "新建一级分类"
                        else -> "在「${st.parentPath}」下新建"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = st.inputName,
                        onValueChange = { vm.setCategoryInput(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("分类名称", fontSize = 14.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        isError = st.error != null
                    )
                    val err = st.error
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

    // 删除确认
    val del = pendingDelete
    if (del != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除「${del.name}」？", fontSize = 16.sp, fontWeight = FontWeight.Medium) },
            text = {
                Text(
                    "如果该分类下还有商品或子分类，删除会被拒绝，需先迁移。",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteCategory(del.id)
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun CatRow(
    c: CategoryPick,
    onRename: () -> Unit,
    onCreateChild: () -> Unit,
    onDelete: () -> Unit
) {
    val indent = (c.depth - 1) * 18
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onRename() }
            .padding(start = indent.dp + 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            c.name,
            fontSize = if (c.depth == 1) 15.sp else 14.sp,
            fontWeight = if (c.depth == 1) FontWeight.Medium else FontWeight.Normal,
            color = when (c.depth) {
                1 -> MaterialTheme.colorScheme.onSurface
                2 -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // 只在三级上限内允许建子类
        if (c.depth < 3) {
            Text(
                "＋",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { onCreateChild() }
                    .padding(horizontal = 8.dp)
            )
        }
        Text(
            "✕",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clickable { onDelete() }
                .padding(horizontal = 8.dp)
        )
    }
}
