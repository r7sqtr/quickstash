package com.example.quickstash.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onSaved: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var restoreConfirmOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    if (restoreConfirmOpen) {
        AlertDialog(
            onDismissRequest = { restoreConfirmOpen = false },
            title = { Text("復元の確認") },
            text = {
                Text("NAS 上のバックアップから復元します。現在のタグ・Tier・グループ・並び順はすべて上書きされます。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        restoreConfirmOpen = false
                        viewModel.restoreBackup()
                    },
                ) {
                    Text("復元する", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { restoreConfirmOpen = false }) {
                    Text("キャンセル")
                }
            },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("設定") }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard("接続設定") {
            OutlinedTextField(
                value = state.host,
                onValueChange = viewModel::onHostChange,
                label = { Text("ホスト（IPアドレス）") },
                placeholder = { Text("192.168.1.10") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.secondaryHost,
                onValueChange = viewModel::onSecondaryHostChange,
                label = { Text("セカンダリホスト（任意）") },
                placeholder = { Text("100.x.y.z（Tailscale 等）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.port,
                onValueChange = viewModel::onPortChange,
                label = { Text("ポート") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.share,
                onValueChange = viewModel::onShareChange,
                label = { Text("共有名") },
                placeholder = { Text("photos") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.username,
                onValueChange = viewModel::onUsernameChange,
                label = { Text("ユーザー名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("パスワード") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.rootPath,
                onValueChange = viewModel::onRootPathChange,
                label = { Text("ルートパス（任意）") },
                placeholder = { Text("Photos/2024") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            state.testResult?.let { result ->
                Text(
                    text = result,
                    color = if (result.startsWith("接続成功")) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = viewModel::test,
                    enabled = state.canSubmit && !state.testing,
                ) {
                    if (state.testing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                    }
                    Text("接続テスト")
                }
                Button(
                    onClick = viewModel::save,
                    enabled = state.canSubmit && !state.testing,
                ) {
                    Text("保存して開く")
                }
            }
            }

            SectionCard("閲覧設定") {
            OutlinedTextField(
                value = state.skipSeconds,
                onValueChange = viewModel::onSkipSecondsChange,
                label = { Text("動画スキップ秒数（ダブルタップ・ボタン共通）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.slideshowSeconds,
                onValueChange = viewModel::onSlideshowSecondsChange,
                label = { Text("スライドショー間隔（秒）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tierバッジを表示", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "一覧のサムネイルに手動/自動Tierのバッジを表示します",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.showTierBadges,
                    onCheckedChange = viewModel::onShowTierBadgesChange,
                )
            }
            }

            SectionCard("セキュリティ") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("アプリロック", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "起動時・復帰時に生体認証（指紋/顔）または画面ロックで解除",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.appLockEnabled,
                    onCheckedChange = viewModel::onAppLockChange,
                )
            }
            }

            SectionCard("ストレージ") {
            LaunchedEffect(Unit) { viewModel.refreshCacheSize() }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("画像キャッシュ", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = if (state.cacheSizeBytes < 0) {
                            "計算中..."
                        } else {
                            "%.1f MB 使用中（サムネイル最大512MB・原画最大256MBで自動整理）"
                                .format(state.cacheSizeBytes / 1024.0 / 1024.0)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = viewModel::clearCache) {
                    Text("クリア")
                }
            }
            }

            SectionCard("バックアップ") {
            Text(
                text = "タグ・Tier・グループ・並び順を NAS に保存/復元します。24時間ごとに起動時に自動保存されます",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.lastAutoBackupMillis > 0) {
                Text(
                    text = "最終自動バックアップ: " +
                        SimpleDateFormat("M/d HH:mm", Locale.JAPAN)
                            .format(Date(state.lastAutoBackupMillis)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = viewModel::exportBackup,
                    enabled = !state.backupBusy,
                ) {
                    Text("バックアップを保存")
                }
                OutlinedButton(
                    onClick = { restoreConfirmOpen = true },
                    enabled = !state.backupBusy,
                ) {
                    Text("復元")
                }
            }
            state.backupResult?.let { result ->
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (result.contains("失敗")) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}
