package wt.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import wt.app.ui.AppScaffold
import wt.app.ui.AppViewModel
import wt.app.ui.WtTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { WtTheme { AppScaffold(vm) } }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshToday()
    }
}
