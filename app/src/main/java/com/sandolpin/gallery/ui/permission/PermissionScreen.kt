package com.sandolpin.gallery.ui.permission

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * 「すべてのファイルへのアクセス」が未許可の間、このダイアログを表示する。
 * この権限は通常の許可ダイアログでは付与できず、システムの設定画面で
 * 手動でオンにしてもらう必要があるため、まず理由を説明してから遷移する。
 */
@Composable
fun AllFilesAccessGate(
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(isAllFilesAccessGranted()) }

    // 設定画面から戻ってきたタイミングで、許可状態を再チェックする
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        granted = isAllFilesAccessGranted()
    }

    if (granted) {
        content()
        return
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "ファイルへのアクセスを許可してください",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "写真の削除・名前の変更・移動などを、確認ダイアログなしで" +
                    "スムーズに行うために、「すべてのファイルへのアクセス」を" +
                    "設定画面で許可してください。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = {
                val intent = allFilesAccessIntent(context.packageName)
                launcher.launch(intent)
            }) {
                Text("設定画面を開く")
            }
        }
    }
}

private fun isAllFilesAccessGranted(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

private fun allFilesAccessIntent(packageName: String): Intent =
    Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.parse("package:$packageName"),
    )
