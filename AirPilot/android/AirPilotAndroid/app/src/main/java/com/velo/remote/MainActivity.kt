package com.velo.remote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.velo.remote.ui.screens.MainScreen
import com.velo.remote.ui.theme.VeloTheme
import com.velo.remote.ui.viewmodel.VeloViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: VeloViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VeloTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}
