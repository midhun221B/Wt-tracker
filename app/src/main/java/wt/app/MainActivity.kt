package wt.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import wt.app.data.AppDatabase
import wt.core.Safety

/** Step 1 placeholder: seeds the database and shows what's stored. The real UI arrives in step 2. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.get(this)
        setContent {
            LaunchedEffect(Unit) { db.seedIfEmpty() }
            val weights by db.weights().observeAll().collectAsState(initial = emptyList())
            val runs by db.runs().observeAll().collectAsState(initial = emptyList())
            val plan by db.plans().observeActiveCheckpoints().collectAsState(initial = emptyList())
            MaterialTheme {
                Surface {
                    Column(Modifier.padding(16.dp)) {
                        Text("Wt Tracker", style = MaterialTheme.typography.headlineMedium)
                        Text("Weigh-ins: ${weights.size}, runs: ${runs.size}")
                        plan.forEach { Text("Plan ${it.date}: ${it.kg} kg") }
                        Text(Safety.DISCLAIMER, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
