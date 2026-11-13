package lk.ijse.firstsemfinalproject25.controller;

import lk.ijse.firstsemfinalproject25.model.PasswordResetModel;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class ForgotPasswordController implements Initializable {

    @FXML private ImageView logoView, bannerImage;
    @FXML private TextField txtUsername, txtPin;
    @FXML private PasswordField txtNewPassword, txtConfirm;
    @FXML private ProgressBar barStrength;
    @FXML private Label lblStrength, lblConfirmStatus, lblError;
    @FXML private Button btnReset;

    private static final int MIN_PASSWORD = 6;

    private final PasswordResetModel resetModel = new PasswordResetModel();

    /* ======================================================= */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setImage(logoView, "/images/logo.png");
        setImage(bannerImage, "/images/login-banner.jpg");

        // PIN box: digits only, max 6
        txtPin.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().matches("\\d{0," + PasswordResetModel.PIN_LENGTH + "}") ? change : null));

        // everything below updates LIVE while the user types
        txtUsername.textProperty().addListener((o, a, b) -> refresh());
        txtPin.textProperty().addListener((o, a, b) -> refresh());
        txtNewPassword.textProperty().addListener((o, a, b) -> { updateStrength(); updateConfirm(); refresh(); });
        txtConfirm.textProperty().addListener((o, a, b) -> { updateConfirm(); refresh(); });

        Platform.runLater(txtUsername::requestFocus);
    }

    private void setImage(ImageView view, String path) {
        URL res = getClass().getResource(path);
        if (res != null) view.setImage(new Image(res.toExternalForm()));
    }

    /* ---------- live feedback ---------- */
    private void updateStrength() {
        String p = txtNewPassword.getText();
        int score = 0;
        if (p.length() >= MIN_PASSWORD) score++;
        if (p.length() >= 10) score++;
        if (p.matches(".*[a-z].*") && p.matches(".*[A-Z].*")) score++;
        if (p.matches(".*\\d.*")) score++;
        if (p.matches(".*[^A-Za-z0-9].*")) score++;

        barStrength.getStyleClass().removeAll("strength-weak", "strength-medium", "strength-strong");
        if (p.isEmpty()) {
            barStrength.setProgress(0);
            setStatus(lblStrength, "", "hint-label");
            return;
        }
        barStrength.setProgress(Math.max(score, 1) / 5.0);
        if (p.length() < MIN_PASSWORD || score <= 2) {
            barStrength.getStyleClass().add("strength-weak");
            setStatus(lblStrength, "Weak", "status-bad");
        } else if (score == 3) {
            barStrength.getStyleClass().add("strength-medium");
            setStatus(lblStrength, "Medium", "status-wait");
        } else {
            barStrength.getStyleClass().add("strength-strong");
            setStatus(lblStrength, "Strong", "status-ok");
        }
    }

    private void updateConfirm() {
        String c = txtConfirm.getText();
        if (c.isEmpty()) setStatus(lblConfirmStatus, "", "hint-label");
        else if (c.equals(txtNewPassword.getText())) setStatus(lblConfirmStatus, "✔ Passwords match", "status-ok");
        else setStatus(lblConfirmStatus, "✖ Passwords do not match", "status-bad");
    }

    /** The button stays disabled until every field is valid. */
    private void refresh() {
        hideError();
        boolean valid =
                !txtUsername.getText().trim().isEmpty()
                        && txtPin.getText().length() == PasswordResetModel.PIN_LENGTH
                        && txtNewPassword.getText().length() >= MIN_PASSWORD
                        && txtNewPassword.getText().equals(txtConfirm.getText());
        btnReset.setDisable(!valid);
    }

    /* ---------- reset ---------- */
    @FXML
    private void onResetPassword(ActionEvent e) {
        final String username = txtUsername.getText().trim();
        final String pin = txtPin.getText();
        final String newPassword = txtNewPassword.getText();

        setBusy(true);
        Task<Boolean> task = new Task<>() {
            @Override protected Boolean call() throws Exception {
                return resetModel.resetPassword(username, pin, newPassword);
            }
        };
        task.setOnSucceeded(ev -> goToLogin(username, "Password changed! Please sign in."));
        task.setOnFailed(ev -> {
            setBusy(false);
            Throwable ex = task.getException();
            if (ex instanceof PasswordResetModel.InvalidPinException) {
                txtPin.clear();
                showError(ex.getMessage());
            } else {
                ex.printStackTrace();
                showError("Could not reset the password. Check that MySQL is running.");
            }
        });
        Thread t = new Thread(task, "password-reset");
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void onBackToLogin(ActionEvent e) {
        goToLogin(null, null);
    }

    private void goToLogin(String username, String message) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/login.fxml"));
            Parent root = loader.load();
            if (username != null) {
                LoginController login = loader.getController();
                login.prefill(username, message);
            }
            Stage stage = (Stage) btnReset.getScene().getWindow();
            stage.getScene().setRoot(root);
            stage.setTitle("Bike Garage - Login");
            stage.sizeToScene();
            stage.centerOnScreen();
        } catch (IOException ex) {
            ex.printStackTrace();
            showError("Could not open the login page.");
        }
    }

    /* ---------- helpers ---------- */
    private void setBusy(boolean busy) {
        btnReset.setDisable(busy);
        btnReset.setText(busy ? "RESETTING..." : "RESET PASSWORD");
        txtUsername.setDisable(busy);
        txtPin.setDisable(busy);
        txtNewPassword.setDisable(busy);
        txtConfirm.setDisable(busy);
        if (!busy) refresh();
    }

    private void setStatus(Label label, String text, String styleClass) {
        label.setText(text);
        label.getStyleClass().removeAll("hint-label", "status-ok", "status-bad", "status-wait");
        label.getStyleClass().add(styleClass);
    }

    private void showError(String msg) {
        lblError.setText(msg);
        lblError.setVisible(true);
        lblError.setManaged(true);
    }

    private void hideError() {
        lblError.setVisible(false);
        lblError.setManaged(false);
    }
}