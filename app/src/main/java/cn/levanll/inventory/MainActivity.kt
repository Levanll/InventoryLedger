package cn.levanll.inventory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.levanll.inventory.ui.MainScreen
import cn.levanll.inventory.ui.MainViewModel
import cn.levanll.inventory.ui.theme.InventoryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            InventoryTheme {
                val vm: MainViewModel = viewModel()
                MainScreen(vm = vm)
            }
        }
    }
}
