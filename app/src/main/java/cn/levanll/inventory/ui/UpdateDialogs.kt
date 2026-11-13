package cn.levanll.inventory.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.levanll.inventory.domain.UpdateInfo
import cn.levanll.inventory.i18n.I18n

/**
 * 更新相关的四个弹窗。
 *
 * 之所以集中在一个文件：它们共享同一套视觉与文案风格，
 * 分散到各 Screen 里会让「更新」这件事的措辞漂移。
 */

/**
 * 首次启动询问是否允许检查更新。
 *
 * 这个项目的一贯做法是把选择权交给用户（保存时弹窗确认、条码冲突三选一、
 * 归零问删除还是保留），检查更新同样如此 —— 不默认开启。
 */
@Composable
fun UpdateConsentDialog(
    onAllow: () -> Unit,
    onDeny: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { },   // 必须明确表态，不能点外面关掉
        title = {
            Text(
                I18n.s("允许检查更新？", "Allow update checks?"),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    I18n.s(
                        "启动时会向 GitHub 查询最新版本号。",
                        "On launch, the app asks GitHub for the latest version number."
                    ),
                    fontSize = 13.sp
                )
                Text(
                    I18n.s(
                        "请求不含任何本地数据，也不需要账号。可随时在设置里关闭。",
                        "The request contains no local data and needs no account. You can turn it off in Settings at any time."
                    ),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAllow) {
                Text(I18n.s("允许", "Allow"), color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDeny) {
                Text(
                    I18n.s("不允许", "Don't allow"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

/** 发现新版本 */
@Composable
fun NewVersionDialog(
    info: UpdateInfo,
    currentVersion: String,
    onUpdate: () -> Unit,
    onLater: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = {
            Text(
                I18n.s("发现新版本 v${info.version}", "Version ${info.version} available"),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        I18n.s("当前 v$currentVersion", "Current v$currentVersion"),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (info.sizeBytes > 0) {
                        Text(
                            fmtSize(info.sizeBytes),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (info.notes.isNotEmpty()) {
                    Text(
                        info.notes,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 提前告知系统会要一次授权，免得用户在安装时被警示框吓到
                Text(
                    I18n.s(
                        "安装时系统会询问一次「允许安装未知应用」。",
                        "Android will ask once to allow installing unknown apps."
                    ),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onUpdate) {
                Text(I18n.s("立即更新", "Update now"), color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onLater) {
                Text(
                    I18n.s("以后再说", "Later"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

/** 下载进度。不可点外部关闭，但可以取消 */
@Composable
fun UpdateDownloadDialog(
    percent: Int,
    done: Long,
    total: Long,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { },   // 防误触，只能点取消
        title = {
            Text(
                I18n.s("正在下载更新", "Downloading update"),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
                Row {
                    Text(
                        "$percent%",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${fmtSize(done)} / ${fmtSize(total)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text(
                    I18n.s("取消", "Cancel"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

/** 下载校验中的短暂状态 */
@Composable
fun UpdateVerifyingDialog() {
    AlertDialog(
        onDismissRequest = { },
        title = {
            Text(
                I18n.s("正在校验安装包", "Verifying package"),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        },
        text = {
            Text(
                I18n.s("确认文件完整且签名一致…", "Checking the file is complete and correctly signed…"),
                fontSize = 13.sp
            )
        },
        confirmButton = { }
    )
}

/** 下载失败 */
@Composable
fun UpdateFailedDialog(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                I18n.s("更新失败", "Update failed"),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        },
        text = { Text(message, fontSize = 13.sp) },
        confirmButton = {
            TextButton(onClick = onRetry) {
                Text(I18n.s("重试", "Retry"), color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    I18n.s("取消", "Cancel"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

/** 需要先去系统设置里授权 */
@Composable
fun UpdateNeedPermissionDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                I18n.s("需要允许安装应用", "Permission required"),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        },
        text = {
            Text(
                I18n.s(
                    "请在系统设置里允许本应用「安装未知应用」，然后回来重试。",
                    "Please allow this app to install unknown apps in system settings, then come back and retry."
                ),
                fontSize = 13.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(I18n.s("去设置", "Open settings"), color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    I18n.s("取消", "Cancel"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

/**
 * 字节数格式化。
 *
 * 只用于安装包大小与下载进度，量级在 KB–MB 之间，
 * 不需要处理 GB 或更精细的单位换算。
 */
private fun fmtSize(bytes: Long): String = when {
    bytes <= 0 -> "—"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format("%.0f KB", bytes / 1024.0)
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}
