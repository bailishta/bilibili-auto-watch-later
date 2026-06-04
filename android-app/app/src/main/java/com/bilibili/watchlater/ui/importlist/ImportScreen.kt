package com.bilibili.watchlater.ui.importlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bilibili.watchlater.ui.theme.BgGray
import com.bilibili.watchlater.ui.theme.BgWhite
import com.bilibili.watchlater.ui.theme.Pink
import com.bilibili.watchlater.ui.theme.PinkBg
import com.bilibili.watchlater.ui.theme.Success
import com.bilibili.watchlater.ui.theme.SuccessBg
import com.bilibili.watchlater.ui.theme.TextHint
import com.bilibili.watchlater.ui.theme.TextPrimary
import com.bilibili.watchlater.ui.theme.TextSecondary
import com.bilibili.watchlater.ui.theme.Warning
import com.bilibili.watchlater.ui.theme.WarningBg

@Composable
fun ImportScreen(
    uid: Long,
    viewModel: ImportViewModel,
    onDone: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 图标
        Text(text = "▶", fontSize = 48.sp, color = Pink)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "B站稍后观看助手",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "自动帮你把关注的UP主新视频加入稍后观看",
            fontSize = 14.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 功能说明
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = BgGray),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                FeatureItem("自动检测追踪名单中UP主的新视频")
                FeatureItem("发现新视频后自动添加到稍后观看")
                FeatureItem("可自行增删追踪的UP主名单")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (!uiState.isImporting && uiState.result == null && uiState.error == null) {
            // 初始状态：两个按钮
            Button(
                onClick = { viewModel.startImport(uid) },
                colors = ButtonDefaults.buttonColors(containerColor = Pink),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("从B站关注列表导入", fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    viewModel.skip()
                    onDone()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("稍后再说，手动添加", fontSize = 15.sp, color = TextSecondary)
            }
        }

        // 导入中
        if (uiState.isImporting) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = BgWhite),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = Pink, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = uiState.progressText, fontSize = 14.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Pink,
                        trackColor = PinkBg
                    )
                }
            }
        }

        // 导入结果
        uiState.result?.let { result ->
            val isSuccess = result.status == "ok"
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = if (isSuccess) SuccessBg else WarningBg),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isSuccess) {
                            "导入完成！成功导入 ${result.imported} 位UP主" +
                                    if (result.skipped > 0) "，跳过 ${result.skipped} 个特殊账号" else ""
                        } else {
                            "导入出错"
                        },
                        fontSize = 14.sp,
                        color = if (isSuccess) Success else Warning
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onDone,
                        colors = ButtonDefaults.buttonColors(containerColor = Pink),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("开始使用")
                    }
                }
            }
        }

        // 错误
        uiState.error?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = WarningBg),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = error, fontSize = 14.sp, color = Warning)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onDone,
                        colors = ButtonDefaults.buttonColors(containerColor = Pink),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("稍后再说")
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureItem(text: String) {
    Text(
        text = "✓  $text",
        fontSize = 13.sp,
        color = TextHint,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}
