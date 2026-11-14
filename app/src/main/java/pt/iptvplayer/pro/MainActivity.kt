package pt.iptvplayer.pro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LusaTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color(0xFF0A1330), Bg)))
                ) {
                    BackHandler(enabled = vm.canGoBack()) { vm.goBack() }
                    when (vm.screen) {
                        Screen.ADD_SOURCE -> AddSourceScreen(vm)
                        Screen.HOME -> HomeScreen(vm)
                        Screen.CATALOG -> CatalogScreen(vm)
                        Screen.SERIES -> SeriesScreen(vm)
                        Screen.SEARCH -> SearchScreen(vm)
                        Screen.SETTINGS -> SettingsScreen(vm)
                        Screen.PLAYER -> PlayerScreen(vm)
                    }
                }
            }
        }
    }
}
