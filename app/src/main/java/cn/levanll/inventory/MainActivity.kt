package cn.levanll.inventory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.levanll.inventory.i18n.I18n
import cn.levanll.inventory.ui.MainScreen
import cn.levanll.inventory.ui.MainViewModel
import cn.levanll.inventory.ui.theme.InventoryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 必须在 setContent 之前恢复语言，否则首帧会闪一下中文
        I18n.init(this)
        enableEdgeToEdge()
        setContent {
            InventoryTheme {
                val vm: MainViewModel = viewModel()
                MainScreen(vm = vm)
            }
        }
    }
}
