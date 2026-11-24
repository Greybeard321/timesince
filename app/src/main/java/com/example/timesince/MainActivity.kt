package com.example.timesince

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.timesince.ui.MainScreen
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: ItemViewModel
    private lateinit var exportLauncher: ActivityResultLauncher<Intent>
    private lateinit var importLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[ItemViewModel::class.java]

        exportLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri -> viewModel.exportItems(uri) }
            }
        }
        importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri -> viewModel.importItems(uri) }
            }
        }

        val versionName = @Suppress("DEPRECATION") packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"

        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface {
                    val items by viewModel.items.collectAsStateWithLifecycle()
                    val busy by viewModel.busy.collectAsStateWithLifecycle()
                    val message by viewModel.message.collectAsStateWithLifecycle()

                    MainScreen(
                        items = items,
                        busy = busy,
                        message = message,
                        versionName = versionName,
                        onAdd = viewModel::addItem,
                        onUpdate = viewModel::updateItem,
                        onDelete = viewModel::deleteItem,
                        onExport = ::launchExport,
                        onImport = ::launchImport,
                        onMessageConsumed = viewModel::consumeMessage,
                    )
                }
            }
        }
    }

    private fun launchExport() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, "timesince-${LocalDate.now()}.csv")
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        exportLauncher.launch(intent)
    }

    private fun launchImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        importLauncher.launch(intent)
    }
}
