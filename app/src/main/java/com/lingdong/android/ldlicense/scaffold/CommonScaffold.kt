package com.lingdong.android.ldlicense.scaffold

// ... existing code ...
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lingdong.android.ldlicense.AppContextProvider
import com.lingdong.android.ldlicense.LoginActivity
import com.lingdong.android.ldlicense.MainActivity
import com.lingdong.android.ldlicense.util.Util

// ... existing code ...
// ... existing code ...

// Add this new reusable Scaffold component
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun CommonScaffold(
    onLogout: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("LD License") },
                actions = {
                    var menuExpanded by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多选项")
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text("退出登录") }, onClick = {
                            Util.setToken(AppContextProvider.context, "")
                            val intent = Intent(AppContextProvider.context, LoginActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            AppContextProvider.context.startActivity(intent)
                            onLogout()
                            menuExpanded = false
                        })
                    }
                }
            )
        }
    ) { innerPadding ->
        content(innerPadding)
    }
}

