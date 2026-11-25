package vn.edu.hust.moceir

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import vn.edu.hust.moceir.ui.MoceTheme
import vn.edu.hust.moceir.ui.RestoreScreen
import vn.edu.hust.moceir.ui.RestoreViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MoceTheme {
                val viewModel: RestoreViewModel = viewModel()
                RestoreScreen(viewModel)
            }
        }
    }
}
