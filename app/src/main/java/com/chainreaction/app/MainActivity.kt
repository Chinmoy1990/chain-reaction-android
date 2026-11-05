package com.chainreaction.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chainreaction.app.ui.ChainTheme
import com.chainreaction.app.ui.GameScreen
import com.chainreaction.app.ui.LobbyScreen
import com.chainreaction.app.ui.LocalSetupScreen
import com.chainreaction.app.ui.MenuScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Sounds.init(applicationContext)
        setContent {
            ChainTheme {
                val vm: GameViewModel = viewModel()
                when (vm.screen) {
                    Screen.MENU -> MenuScreen(vm)
                    Screen.LOCAL_SETUP -> {
                        BackHandler { vm.goTo(Screen.MENU) }
                        LocalSetupScreen(vm)
                    }
                    Screen.LOBBY -> {
                        BackHandler { vm.goTo(Screen.MENU) }
                        LobbyScreen(vm)
                    }
                    Screen.GAME -> GameScreen(vm)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) Sounds.release()
    }
}
