package com.example.kanshiwarehousemanagementsystem;

import com.example.kanshiwarehousemanagementsystem.database.DatabaseManager;
import com.example.kanshiwarehousemanagementsystem.database.UserDao;
import com.example.kanshiwarehousemanagementsystem.model.User;
import com.example.kanshiwarehousemanagementsystem.service.PasswordValidator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AuthTest {

    @BeforeAll
    static void setup() {
        DatabaseManager.initializeDatabase();
    }

    @Test
    void testPasswordValidatorLevels() {
        // Weak
        PasswordValidator.ValidationResult weak = PasswordValidator.evaluate("pass");
        assertEquals(PasswordValidator.StrengthLevel.WEAK, weak.getLevel());
        assertFalse(weak.isAcceptable());

        // Medium
        PasswordValidator.ValidationResult medium = PasswordValidator.evaluate("SecurePass1");
        assertEquals(PasswordValidator.StrengthLevel.MEDIUM, medium.getLevel());
        assertTrue(medium.isAcceptable());

        // Strong
        PasswordValidator.ValidationResult strong = PasswordValidator.evaluate("Super#Secure99!");
        assertEquals(PasswordValidator.StrengthLevel.STRONG, strong.getLevel());
        assertTrue(strong.isAcceptable());
    }

    @Test
    void testDefaultSeedUsersAuthenticationByUsernameAndEmail() {
        UserDao dao = new UserDao();

        // 1. Authenticate by Username
        User userByUsername = dao.authenticate("admin", "Admin@123");
        assertNotNull(userByUsername, "Should authenticate by username");
        assertEquals("admin@gmail.com", userByUsername.getEmail());
        assertEquals("admin", userByUsername.getUsername());

        // 2. Authenticate by Email
        User userByEmail = dao.authenticate("admin@gmail.com", "Admin@123");
        assertNotNull(userByEmail, "Should authenticate by email");
        assertEquals("admin", userByEmail.getUsername());

        // 3. User account
        User standardUser = dao.authenticate("user@gmail.com", "User@123");
        assertNotNull(standardUser, "Default user should authenticate");
    }

    @Test
    void testUserRegistrationAndDuplicateHandling() {
        UserDao dao = new UserDao();
        long timestamp = System.currentTimeMillis();
        String testEmail = "operator" + timestamp + "@gmail.com";
        String testUser = "operator" + timestamp;

        assertFalse(dao.emailExists(testEmail));
        assertFalse(dao.usernameExists(testUser));

        // Register new account
        boolean registered = dao.register(testEmail, testUser, "Pass@12345");
        assertTrue(registered, "User registration should succeed");

        assertTrue(dao.emailExists(testEmail));
        assertTrue(dao.usernameExists(testUser));

        // Duplicate email
        boolean dupEmail = dao.register(testEmail, "differentUser", "Pass@12345");
        assertFalse(dupEmail, "Duplicate email registration must fail");

        // Duplicate username
        boolean dupUsername = dao.register("different@gmail.com", testUser, "Pass@12345");
        assertFalse(dupUsername, "Duplicate username registration must fail");

        // Authenticate via email
        User authViaEmail = dao.authenticate(testEmail, "Pass@12345");
        assertNotNull(authViaEmail);

        // Authenticate via username
        User authViaUsername = dao.authenticate(testUser, "Pass@12345");
        assertNotNull(authViaUsername);

        // Wrong password
        User failed = dao.authenticate(testUser, "WrongPassword");
        assertNull(failed);
    }
}
