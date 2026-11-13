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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.levanll.inventory.i18n.I18n
import cn.levanll.inventory.i18n.Lang

/**
 * 设置面板。
 *
 * 只放与「应用本身」相关的开关，不放库存数据相关的操作 ——
 * 那些已经各自有入口，集中到这里会让用户找不到。
 */
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    onClose: () -> Unit
) {
    val st by vm.settings.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // ---- 标题栏 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = I18n.s("返回", "Back"),
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onClose() }
            )
            Spacer(Modifier.width(12.dp))
            Text(
                I18n.s("设置", "Settings"),
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ---- 检查更新开关 ----
            SectionCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            I18n.s("检查更新", "Check for updates"),
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            I18n.s(
                                "启动时向 GitHub 查询版本号，可随时关闭",
                                "Asks GitHub for the latest version on launch; can be turned off"
                            ),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = st.updateCheckEnabled == true,
                        onCheckedChange = { vm.setUpdateCheckEnabled(it) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---- 版本 ----
            SectionCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = st.updateCheckEnabled == true) {
                            vm.checkForUpdateManually()
                        }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        I18n.s("版本", "Version"),
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    if (st.checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                    }

                    // 有新版时显式标出，用户不用点进去才知道
                    val hasNew = st.newVersion != null
                    Text(
                        buildString {
                            append("v${st.currentVersion}")
                            if (hasNew) append(I18n.s(" (有更新)", " (update available)"))
                        },
                        fontSize = 14.sp,
                        fontWeight = if (hasNew) FontWeight.Medium else FontWeight.Normal,
                        color = if (hasNew) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---- 手动检查的一次性提示 ----
            val msg = st.lastCheckMessage
            if (msg != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    msg,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            // ---- 语言 ----
            SectionCard {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            I18n.s("语言", "Language"),
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            if (I18n.lang == Lang.ZH) "中文" else "English",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { I18n.toggle() }
                                .padding(vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ---- 隐私说明（简短，避免占太多空间）----
            Text(
                I18n.s(
                    "更新检查只发送一个版本查询请求，不含任何库存或个人信息。",
                    "Update checks send a single version query and contain no inventory or personal data."
                ),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

/** 设置项的统一卡片外壳，保持与列表项一致的圆角与底色 */
@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        content()
    }
}
