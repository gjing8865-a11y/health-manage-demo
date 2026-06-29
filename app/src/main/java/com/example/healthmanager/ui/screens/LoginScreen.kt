package com.example.healthmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthmanager.ui.theme.*
import com.example.healthmanager.viewmodel.MainViewModel

@Composable
fun LoginScreen(
    viewModel: MainViewModel,
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgLight)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(90.dp),
            shape = CircleShape,
            color = PrimaryTeal.copy(alpha = 0.1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.HealthAndSafety,
                    contentDescription = null,
                    tint = PrimaryTeal,
                    modifier = Modifier.size(50.dp)
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(
            "健康管家",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            "守护您的每一天",
            fontSize = 14.sp,
            color = TextSecondary
        )

        Spacer(Modifier.height(48.dp))

        OutlinedTextField(
            value = account,
            onValueChange = {
                account = it
                errorText = ""
            },
            label = { Text("账号") },
            placeholder = { Text("手机号 / 邮箱 / 用户名") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            leadingIcon = {
                Icon(Icons.Rounded.Person, contentDescription = null, tint = PrimaryTeal)
            },
            singleLine = true
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                errorText = ""
            },
            label = { Text("密码") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            leadingIcon = {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = PrimaryTeal)
            },
            trailingIcon = {
                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                    Icon(
                        if (isPasswordVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                        contentDescription = null
                    )
                }
            },
            visualTransformation = if (isPasswordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            singleLine = true
        )

        if (errorText.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = errorText,
                color = Color.Red,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                when {
                    account.isBlank() -> errorText = "请输入账号"
                    password.isBlank() -> errorText = "请输入密码"
                    else -> {
                        isLoading = true
                        viewModel.loginUser(
                            account = account.trim(),
                            password = password,
                            onSuccess = {
                                isLoading = false
                                onLoginSuccess()
                            },
                            onError = { msg ->
                                isLoading = false
                                errorText = msg
                            }
                        )
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text("立即登录", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(24.dp))

        Row {
            Text("没有账号？", color = TextSecondary)
            Text(
                " 点击注册",
                color = PrimaryTeal,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onNavigateToRegister() }
            )
        }
    }
}

@Composable
fun RegisterScreen(
    viewModel: MainViewModel,
    onRegisterSuccess: () -> Unit,
    onBackToLogin: () -> Unit
) {
    var account by remember { mutableStateOf("") }
    var nickName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgLight)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "创建账号",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            "开启您的智能健康之旅",
            fontSize = 14.sp,
            color = TextSecondary
        )

        Spacer(Modifier.height(40.dp))

        OutlinedTextField(
            value = account,
            onValueChange = {
                account = it
                errorText = ""
            },
            label = { Text("设置账号") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            leadingIcon = {
                Icon(Icons.Rounded.Badge, contentDescription = null, tint = PrimaryTeal)
            },
            singleLine = true
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = nickName,
            onValueChange = {
                nickName = it
                errorText = ""
            },
            label = { Text("昵称") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            leadingIcon = {
                Icon(Icons.Rounded.Person, contentDescription = null, tint = PrimaryTeal)
            },
            singleLine = true
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                errorText = ""
            },
            label = { Text("设置密码") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            leadingIcon = {
                Icon(Icons.Rounded.VpnKey, contentDescription = null, tint = PrimaryTeal)
            },
            trailingIcon = {
                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                    Icon(
                        if (isPasswordVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                        contentDescription = null
                    )
                }
            },
            visualTransformation = if (isPasswordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            singleLine = true
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = {
                confirmPassword = it
                errorText = ""
            },
            label = { Text("确认密码") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            leadingIcon = {
                Icon(Icons.Rounded.CheckBox, contentDescription = null, tint = PrimaryTeal)
            },
            trailingIcon = {
                IconButton(onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible }) {
                    Icon(
                        if (isConfirmPasswordVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                        contentDescription = null
                    )
                }
            },
            visualTransformation = if (isConfirmPasswordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            singleLine = true
        )

        if (errorText.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = errorText,
                color = Color.Red,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                when {
                    account.isBlank() -> errorText = "请输入账号"
                    nickName.isBlank() -> errorText = "请输入昵称"
                    password.isBlank() -> errorText = "请输入密码"
                    confirmPassword.isBlank() -> errorText = "请再次输入密码"
                    password != confirmPassword -> errorText = "两次输入的密码不一致"
                    password.length < 6 -> errorText = "密码长度不能少于 6 位"
                    else -> {
                        isLoading = true
                        viewModel.registerUser(
                            account = account.trim(),
                            password = password,
                            nickName = nickName.trim(),
                            onSuccess = {
                                isLoading = false
                                onRegisterSuccess()
                            },
                            onError = { msg ->
                                isLoading = false
                                errorText = msg
                            }
                        )
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text("完成注册", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "返回登录",
            color = TextSecondary,
            modifier = Modifier.clickable { onBackToLogin() }
        )
    }
}