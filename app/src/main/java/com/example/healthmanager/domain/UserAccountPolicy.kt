package com.example.healthmanager.domain

import com.example.healthmanager.model.User

data class LoginValidation(
    val account: String,
    val password: String,
    val errorMessage: String?
) {
    val isValid: Boolean
        get() = errorMessage == null
}

data class RegistrationValidation(
    val account: String,
    val password: String,
    val nickName: String,
    val errorMessage: String?
) {
    val isValid: Boolean
        get() = errorMessage == null
}

object UserAccountPolicy {
    const val DEFAULT_DISPLAY_NAME = "Alex"
    const val DEFAULT_SIGNATURE = "保持活力!"
    private const val MIN_PASSWORD_LENGTH = 6

    fun validateLogin(account: String, password: String): LoginValidation {
        val normalizedAccount = account.trim()
        return when {
            normalizedAccount.isBlank() -> LoginValidation(normalizedAccount, password, "请输入账号")
            password.isBlank() -> LoginValidation(normalizedAccount, password, "请输入密码")
            else -> LoginValidation(normalizedAccount, password, null)
        }
    }

    fun validateRegistration(
        account: String,
        password: String,
        nickName: String,
        confirmPassword: String = password
    ): RegistrationValidation {
        val normalizedAccount = account.trim()
        val normalizedNickName = nickName.trim()

        return when {
            normalizedAccount.isBlank() -> {
                RegistrationValidation(normalizedAccount, password, normalizedNickName, "请输入账号")
            }
            normalizedNickName.isBlank() -> {
                RegistrationValidation(normalizedAccount, password, normalizedNickName, "请输入昵称")
            }
            password.isBlank() -> {
                RegistrationValidation(normalizedAccount, password, normalizedNickName, "请输入密码")
            }
            confirmPassword.isBlank() -> {
                RegistrationValidation(normalizedAccount, password, normalizedNickName, "请再次输入密码")
            }
            password != confirmPassword -> {
                RegistrationValidation(normalizedAccount, password, normalizedNickName, "两次输入的密码不一致")
            }
            password.length < MIN_PASSWORD_LENGTH -> {
                RegistrationValidation(normalizedAccount, password, normalizedNickName, "密码长度不能少于 6 位")
            }
            else -> RegistrationValidation(normalizedAccount, password, normalizedNickName, null)
        }
    }

    fun createUser(account: String, password: String, nickName: String): User {
        return User(
            account = account.trim(),
            password = password,
            nickName = normalizeDisplayName(nickName),
            avatarUri = "",
            signature = DEFAULT_SIGNATURE
        )
    }

    fun normalizeDisplayName(name: String): String =
        name.trim().ifBlank { DEFAULT_DISPLAY_NAME }

    fun normalizeSignature(signature: String): String =
        signature.trim().ifBlank { DEFAULT_SIGNATURE }
}
