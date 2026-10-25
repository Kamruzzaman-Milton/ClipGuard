package com.example

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.ClipGuardScreen
import com.example.ui.theme.ClipGuardTheme
import com.example.viewmodel.ClipGuardViewModel
import org.lsposed.hiddenapibypass.HiddenApiBypass

class MainActivity : ComponentActivity() {

    private val viewModel: ClipGuardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                HiddenApiBypass.addHiddenApiExemptions("")
            }
        }
        enableEdgeToEdge()
        setContent {
            ClipGuardTheme {
                ClipGuardScreen(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResumeCheck()
    }
}
