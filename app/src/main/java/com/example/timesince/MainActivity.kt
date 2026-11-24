package com.example.timesince

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.timesince.ui.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface {
                    val vm: ItemViewModel = viewModel()
                    val items by vm.items.collectAsStateWithLifecycle()
                    MainScreen(
                        items = items,
                        onAdd = vm::addItem,
                        onUpdate = vm::updateItem,
                        onDelete = vm::deleteItem,
                    )
                }
            }
        }
    }
}
