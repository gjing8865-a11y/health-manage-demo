package com.example.healthmanager.domain;

import com.example.healthmanager.model.User;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class UserAccountPolicyTest {
    @Test
    public void loginValidationTrimsAccountAndRejectsBlankFields() {
        LoginValidation blankAccount = UserAccountPolicy.INSTANCE.validateLogin("   ", "123456");
        assertEquals("请输入账号", blankAccount.getErrorMessage());

        LoginValidation blankPassword = UserAccountPolicy.INSTANCE.validateLogin(" demo ", "");
        assertEquals("请输入密码", blankPassword.getErrorMessage());

        LoginValidation valid = UserAccountPolicy.INSTANCE.validateLogin(" demo ", "123456");
        assertNull(valid.getErrorMessage());
        assertEquals("demo", valid.getAccount());
        assertEquals("123456", valid.getPassword());
    }

    @Test
    public void registrationValidationNormalizesInputAndKeepsUiMessagesConsistent() {
        assertEquals(
                "请输入昵称",
                UserAccountPolicy.INSTANCE.validateRegistration("demo", "123456", " ", "123456").getErrorMessage()
        );
        assertEquals(
                "请再次输入密码",
                UserAccountPolicy.INSTANCE.validateRegistration("demo", "123456", "Alex", "").getErrorMessage()
        );
        assertEquals(
                "两次输入的密码不一致",
                UserAccountPolicy.INSTANCE.validateRegistration("demo", "123456", "Alex", "654321").getErrorMessage()
        );
        assertEquals(
                "密码长度不能少于 6 位",
                UserAccountPolicy.INSTANCE.validateRegistration("demo", "12345", "Alex", "12345").getErrorMessage()
        );

        RegistrationValidation valid = UserAccountPolicy.INSTANCE.validateRegistration(
                " demo ",
                "123456",
                " Alex ",
                "123456"
        );
        assertNull(valid.getErrorMessage());
        assertEquals("demo", valid.getAccount());
        assertEquals("Alex", valid.getNickName());
    }

    @Test
    public void createUserAppliesPortfolioSafeDefaults() {
        User user = UserAccountPolicy.INSTANCE.createUser(" demo ", "123456", " Alex ");

        assertEquals("demo", user.getAccount());
        assertEquals("123456", user.getPassword());
        assertEquals("Alex", user.getNickName());
        assertEquals("", user.getAvatarUri());
        assertEquals(UserAccountPolicy.DEFAULT_SIGNATURE, user.getSignature());
    }

    @Test
    public void profileTextNormalizationFallsBackToDefaults() {
        assertEquals(UserAccountPolicy.DEFAULT_DISPLAY_NAME, UserAccountPolicy.INSTANCE.normalizeDisplayName(" "));
        assertEquals(UserAccountPolicy.DEFAULT_SIGNATURE, UserAccountPolicy.INSTANCE.normalizeSignature(" "));
        assertEquals("Grace", UserAccountPolicy.INSTANCE.normalizeDisplayName(" Grace "));
        assertEquals("Keep moving", UserAccountPolicy.INSTANCE.normalizeSignature(" Keep moving "));
    }
}
