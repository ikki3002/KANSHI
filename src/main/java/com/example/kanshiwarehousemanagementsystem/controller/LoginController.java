package com.example.kanshiwarehousemanagementsystem.controller;

import com.example.kanshiwarehousemanagementsystem.database.UserDao;
import com.example.kanshiwarehousemanagementsystem.model.User;
import com.example.kanshiwarehousemanagementsystem.service.PasswordValidator;
import com.example.kanshiwarehousemanagementsystem.service.PasswordValidator.ValidationResult;
import com.example.kanshiwarehousemanagementsystem.HelloApplication;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * Controller for the simplified, professional Kanshi WMS Login & Register portal.
 * Supports industry-standard login (Username or Email) and Gmail/Email registration.
 */
public class LoginController implements Initializable {

    // Tab buttons
    @FXML private Button btnSignInTab;
    @FXML private Button btnRegisterTab;

    // Containers
    @FXML private VBox loginFormBox;
    @FXML private VBox registerFormBox;

    // Login fields (Industry standard: Username or Email)
    @FXML private TextField txtLoginIdentifier;
    @FXML private PasswordField txtLoginPassword;
    @FXML private Button btnLoginSubmit;

    // Register fields (Email/Gmail, Username, Password)
    @FXML private TextField txtRegEmail;
    @FXML private TextField txtRegUsername;
    @FXML private PasswordField txtRegPassword;
    @FXML private PasswordField txtRegConfirmPassword;
    @FXML private Button btnRegSubmit;

    // Password strength UI
    @FXML private Label lblStrengthLevel;
    @FXML private ProgressBar progressStrength;
    @FXML private Label lblStrengthHint;

    // Feedback banner
    @FXML private Label lblStatus;

    private final UserDao userDao = new UserDao();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // Password Strength Real-time Listener
        txtRegPassword.textProperty().addListener((obs, oldVal, newVal) -> {
            updatePasswordStrength(newVal);
        });

        updatePasswordStrength("");
    }

    private void updatePasswordStrength(String password) {
        ValidationResult result = PasswordValidator.evaluate(password);

        progressStrength.setProgress(result.getProgress());
        lblStrengthLevel.setText(result.getLevel().getLabel());
        lblStrengthHint.setText(result.getHint());

        // Update progress bar CSS styling
        progressStrength.getStyleClass().removeAll("strength-weak", "strength-medium", "strength-strong");
        progressStrength.getStyleClass().add(result.getLevel().getCssClass());

        // Text color for label
        switch (result.getLevel()) {
            case WEAK -> lblStrengthLevel.setStyle("-fx-text-fill: #dc2626;");
            case MEDIUM -> lblStrengthLevel.setStyle("-fx-text-fill: #d97706;");
            case STRONG -> lblStrengthLevel.setStyle("-fx-text-fill: #059669;");
            default -> lblStrengthLevel.setStyle("-fx-text-fill: #4b5563;");
        }
    }

    @FXML
    private void handleShowSignIn(ActionEvent event) {
        loginFormBox.setVisible(true);
        loginFormBox.setManaged(true);
        registerFormBox.setVisible(false);
        registerFormBox.setManaged(false);

        btnSignInTab.getStyleClass().add("mode-tab-btn-active");
        btnRegisterTab.getStyleClass().remove("mode-tab-btn-active");

        hideStatus();
    }

    @FXML
    private void handleShowRegister(ActionEvent event) {
        loginFormBox.setVisible(false);
        loginFormBox.setManaged(false);
        registerFormBox.setVisible(true);
        registerFormBox.setManaged(true);

        btnRegisterTab.getStyleClass().add("mode-tab-btn-active");
        btnSignInTab.getStyleClass().remove("mode-tab-btn-active");

        hideStatus();
    }

    @FXML
    private void handleLoginSubmit(ActionEvent event) {
        String identifier = txtLoginIdentifier.getText() == null ? "" : txtLoginIdentifier.getText().trim();
        String password = txtLoginPassword.getText() == null ? "" : txtLoginPassword.getText();

        if (identifier.isEmpty() || password.isEmpty()) {
            showStatus("Please enter your username/email and password.", false);
            return;
        }

        User user = userDao.authenticate(identifier, password);
        if (user != null) {
            showStatus("Welcome back, " + user.getUsername() + "!", true);
            navigateToMainApp(user);
        } else {
            showStatus("Invalid username/email or password.", false);
        }
    }

    private void navigateToMainApp(User user) {
        try {
            Stage stage = (Stage) btnLoginSubmit.getScene().getWindow();
            FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("main-app-view.fxml"));
            Scene scene = new Scene(fxmlLoader.load());

            URL cssResource = HelloApplication.class.getResource("css/industrial-dark.css");
            if (cssResource != null) {
                scene.getStylesheets().add(cssResource.toExternalForm());
            }

            MainAppController controller = fxmlLoader.getController();
            controller.setUserSession(user);

            stage.setScene(scene);
            stage.setMaximized(true);
        } catch (IOException e) {
            System.err.println("Failed to navigate to main app: " + e.getMessage());
            e.printStackTrace();
            showStatus("Error loading main dashboard view.", false);
        }
    }

    @FXML
    private void handleRegisterSubmit(ActionEvent event) {
        String email = txtRegEmail.getText() == null ? "" : txtRegEmail.getText().trim();
        String username = txtRegUsername.getText() == null ? "" : txtRegUsername.getText().trim();
        String password = txtRegPassword.getText() == null ? "" : txtRegPassword.getText();
        String confirmPassword = txtRegConfirmPassword.getText() == null ? "" : txtRegConfirmPassword.getText();

        // 1. Email validation
        if (email.isEmpty()) {
            showStatus("Please enter your email address.", false);
            return;
        }

        if (!isValidEmail(email)) {
            showStatus("Please enter a valid email address (e.g. name@gmail.com).", false);
            return;
        }

        // 2. Username validation
        if (username.isEmpty()) {
            showStatus("Please choose a username.", false);
            return;
        }

        if (username.length() < 3) {
            showStatus("Username must contain at least 3 characters.", false);
            return;
        }

        // 3. Password validation & strength check
        if (password.isEmpty()) {
            showStatus("Please enter a password.", false);
            return;
        }

        ValidationResult strength = PasswordValidator.evaluate(password);
        if (!strength.isAcceptable()) {
            showStatus("Password is too weak: " + strength.getHint(), false);
            return;
        }

        if (!password.equals(confirmPassword)) {
            showStatus("Passwords do not match.", false);
            return;
        }

        // 4. Duplicate checks
        if (userDao.emailExists(email)) {
            showStatus("An account with this email already exists.", false);
            return;
        }

        if (userDao.usernameExists(username)) {
            showStatus("Username '" + username + "' is already taken.", false);
            return;
        }

        // 5. Registration
        boolean success = userDao.register(email, username, password);
        if (success) {
            showStatus("Account created successfully! Switching to sign in...", true);
            txtRegEmail.clear();
            txtRegUsername.clear();
            txtRegPassword.clear();
            txtRegConfirmPassword.clear();
            updatePasswordStrength("");

            // Smooth transition to Sign In tab with pre-filled identifier
            new Thread(() -> {
                try {
                    Thread.sleep(1100);
                } catch (InterruptedException ignored) {}
                Platform.runLater(() -> {
                    handleShowSignIn(null);
                    txtLoginIdentifier.setText(username);
                    txtLoginPassword.requestFocus();
                });
            }).start();
        } else {
            showStatus("Unable to register account. Please try again.", false);
        }
    }

    private boolean isValidEmail(String email) {
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }

    private void showStatus(String message, boolean isSuccess) {
        lblStatus.setText(message);
        lblStatus.setVisible(true);
        lblStatus.setManaged(true);
        lblStatus.getStyleClass().removeAll("status-banner-error", "status-banner-success", "status-banner");
        lblStatus.getStyleClass().addAll("status-banner", isSuccess ? "status-banner-success" : "status-banner-error");
    }

    private void hideStatus() {
        lblStatus.setVisible(false);
        lblStatus.setManaged(false);
        lblStatus.setText("");
    }
}
