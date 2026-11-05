package com.stupidtree.hita.ui.anime.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stupidtree.hita.ui.anime.data.BgmApi
import com.stupidtree.hita.ui.anime.data.CoverLoader
import com.stupidtree.hita.ui.anime.data.HitaniApi
import com.stupidtree.hita.ui.anime.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProfileScreen(prefs: Prefs, bgm: BgmApi, hitani: HitaniApi) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var signingIn by remember { mutableStateOf(false) }
    var hitaniError by remember { mutableStateOf<String?>(null) }
    var hitaniMessage by remember { mutableStateOf<String?>(null) }

    var tokenInput by remember { mutableStateOf(prefs.bgmToken) }
    var baseInput by remember { mutableStateOf(prefs.hitaniBase) }
    var validating by remember { mutableStateOf(false) }
    var tokenError by remember { mutableStateOf<String?>(null) }
    var tokenMessage by remember { mutableStateOf<String?>(null) }
    var miscMessage by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("我的", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold)
        }

        Card(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            elevation = 0.dp,
            backgroundColor = MaterialTheme.colors.surface,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Bangumi", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                if (prefs.bgmToken.isBlank()) {
                    Text(
                        "填入个人访问令牌后可以读取收藏、回写进度、评分与短评。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (prefs.bgmUsername.isBlank()) "已保存令牌" else "已绑定 @${prefs.bgmUsername}",
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            prefs.bgmToken = ""
                            prefs.bgmUsername = ""
                            tokenInput = ""
                            tokenMessage = "已解绑"
                        }) { Text("解绑") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("个人访问令牌", fontSize = 12.sp) },
                    visualTransformation = PasswordVisualTransformation(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://next.bgm.tv/demo/access-token"),
                            ),
                        )
                    }) { Text("去生成", fontSize = 12.sp) }
                    Spacer(Modifier.height(0.dp))
                    Text(
                        "scope 至少包含 write:collection",
                        fontSize = 11.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            if (validating) return@Button
                            validating = true
                            tokenError = null
                            tokenMessage = null
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        BgmApi { tokenInput.trim() }.me()
                                    }
                                }.onSuccess {
                                    prefs.bgmToken = tokenInput.trim()
                                    prefs.bgmUsername = it.username
                                    tokenMessage = "校验通过：@${it.username}"
                                }.onFailure {
                                    tokenError = it.message ?: "校验失败"
                                }
                                validating = false
                            }
                        },
                        enabled = !validating,
                    ) { Text("保存并校验") }
                    if (validating) {
                        Spacer(Modifier.height(0.dp))
                        CircularProgressIndicator(Modifier.padding(start = 10.dp).height(16.dp), strokeWidth = 2.dp)
                    }
                }
                tokenError?.let {
                    Text(it, color = MaterialTheme.colors.error, fontSize = 12.sp)
                }
                tokenMessage?.let {
                    Text(it, color = MaterialTheme.colors.primary, fontSize = 12.sp)
                }
            }
        }

        Card(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            elevation = 0.dp,
            backgroundColor = MaterialTheme.colors.surface,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("校内站点", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                if (prefs.hitaniNickname.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "已登录：${prefs.hitaniNickname}",
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            prefs.logoutHitani()
                            hitaniMessage = "已退出站点登录"
                        }) { Text("退出") }
                    }
                } else {
                    Text(
                        "站点账号用于发送弹幕、查看校内评论与资源索引。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = identifier,
                        onValueChange = { identifier = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("邮箱或学号", fontSize = 12.sp) },
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("密码", fontSize = 12.sp) },
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Button(
                        onClick = {
                            if (signingIn) return@Button
                            signingIn = true
                            hitaniError = null
                            hitaniMessage = null
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        hitani.login(identifier.trim(), password)
                                    }
                                }.onSuccess { result ->
                                    prefs.hitaniCookie = result.cookie
                                    prefs.hitaniNickname = result.user.nickname
                                    password = ""
                                    hitaniMessage = "登录成功：${result.user.nickname}"
                                }.onFailure {
                                    hitaniError = it.message ?: "登录失败"
                                }
                                signingIn = false
                            }
                        },
                        enabled = !signingIn,
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text("登录") }
                    hitaniError?.let {
                        Text(it, color = MaterialTheme.colors.error, fontSize = 12.sp)
                    }
                    hitaniMessage?.let {
                        Text(it, color = MaterialTheme.colors.primary, fontSize = 12.sp)
                    }
                }
                Divider(Modifier.padding(vertical = 12.dp))
                Text("站点地址", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                OutlinedTextField(
                    value = baseInput,
                    onValueChange = { baseInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("https://ani.kajimi.cc", fontSize = 12.sp) },
                )
                TextButton(onClick = {
                    prefs.hitaniBase = baseInput
                    baseInput = prefs.hitaniBase
                    miscMessage = "已保存站点地址：${prefs.hitaniBase}"
                }) { Text("保存地址") }
            }
        }

        Card(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            elevation = 0.dp,
            backgroundColor = MaterialTheme.colors.surface,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("其它", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                TextButton(onClick = {
                    CoverLoader.clear(context)
                    prefs.clearSearchHistory()
                    miscMessage = "已清理封面缓存与搜索历史"
                }) { Text("清理缓存") }
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/kajimi16/hita-community-plugins")),
                    )
                }) { Text("插件源码 / 反馈") }
                miscMessage?.let {
                    Text(it, fontSize = 12.sp, color = MaterialTheme.colors.primary)
                }
                Divider(Modifier.padding(vertical = 10.dp))
                Text(
                    "番剧插件 0.2.2 · API 5 · Compose 1.6.8",
                    fontSize = 11.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.45f),
                )
                Text(
                    "元数据来自 Bangumi，本站不托管视频内容。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.45f),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
