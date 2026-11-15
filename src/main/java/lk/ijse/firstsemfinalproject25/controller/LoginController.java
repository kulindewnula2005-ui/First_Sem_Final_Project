package lk.ijse.firstsemfinalproject25.controller;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import lk.ijse.firstsemfinalproject25.dto.UserDTO;
import lk.ijse.firstsemfinalproject25.model.UserModel;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

public class LoginController implements Initializable {

    @FXML private VBox loginCard;
    @FXML private ImageView logoView, bannerImage;
    @FXML private TextField txtUsername, txtPasswordVisible;
    @FXML private PasswordField txtPassword;
    @FXML private Button btnLogin, btnTogglePassword;
    @FXML private CheckBox chkRemember;
    @FXML private Label lblError;

    private final UserModel userModel = new UserModel();

    private static final String PREF_USER = "remembered_username";
    private final Preferences prefs = Preferences.userNodeForPackage(LoginController.class);

    /* ======================================================= */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setImage(logoView, "/images/logo.png");
        setImage(bannerImage, "/images/login-banner.jpg");

        // keep the hidden and visible password boxes in sync
        txtPasswordVisible.textProperty().bindBidirectional(txtPassword.textProperty());

        // restore the remembered username
        String saved = prefs.get(PREF_USER, "");
        if (!saved.isEmpty()) {
            txtUsername.setText(saved);
            chkRemember.setSelected(true);
            Platform.runLater(txtPassword::requestFocus);
        } else {
            Platform.runLater(txtUsername::requestFocus);
        }

        // hide the message as soon as the user starts typing again
        txtUsername.textProperty().addListener((o, a, b) -> hideMessage());
        txtPassword.textProperty().addListener((o, a, b) -> hideMessage());
    }

    private void setImage(ImageView view, String path) {
        URL res = getClass().getResource(path);
        if (res != null) view.setImage(new Image(res.toExternalForm()));
    }

    /** Called by the sign-up page: fills the username and shows "Account created". */
    public void prefill(String username, String message) {
        txtUsername.setText(username);
        txtPassword.clear();
        showMessage(message, true);
        Platform.runLater(txtPassword::requestFocus);
    }

    /* ---------- show / hide password ---------- */
    @FXML
    private void onTogglePassword(ActionEvent e) {
        boolean show = !txtPasswordVisible.isVisible();
        txtPasswordVisible.setVisible(show);
        txtPasswordVisible.setManaged(show);
        txtPassword.setVisible(!show);
        txtPassword.setManaged(!show);
        btnTogglePassword.setText(show ? "🙈" : "👁");
    }

    /* ---------- SIGN IN ---------- */
    @FXML
    private void onLogin(ActionEvent e) {
        String username = txtUsername.getText().trim();
        String password = txtPassword.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showMessage("Please enter your username and password.", false);
            return;
        }

        setBusy(true);

        // the database check runs on a background thread so the window never freezes
        Task<UserDTO> task = new Task<>() {
            @Override protected UserDTO call() throws Exception {
                return userModel.authenticate(username, password);
            }
        };
        task.setOnSucceeded(ev -> {
            setBusy(false);
            UserDTO user = task.getValue();
            if (user == null) {
                showMessage("Invalid username or password.", false);
                return;
            }
            rememberUser(username);
            openDashboard(user);
        });
        task.setOnFailed(ev -> {
            setBusy(false);
            task.getException().printStackTrace();
            showMessage("Cannot reach the database. Check that MySQL is running.", false);
        });
        Thread t = new Thread(task, "login-auth");
        t.setDaemon(true);
        t.start();
    }

    private void rememberUser(String username) {
        if (chkRemember.isSelected()) prefs.put(PREF_USER, username);
        else prefs.remove(PREF_USER);
    }

    /* ---------- go to the dashboard ---------- */
    private void openDashboard(UserDTO user) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/DashboardPage.fxml"));
            Parent root = loader.load();

            DashboardController dash = loader.getController();
            dash.setUser(user.getFullName(), user.getRole());

            Stage stage = (Stage) btnLogin.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Bike Garage Management System");
            stage.setResizable(true);
            stage.setMinWidth(1100);
            stage.setMinHeight(650);
            stage.centerOnScreen();
            stage.setOnHidden(ev -> dash.stop());
        } catch (IOException ex) {
            ex.printStackTrace();
            showMessage("Could not open the dashboard.", false);
        }
    }

    /* ---------- go to sign up ---------- */
    @FXML
    private void onSignUp(ActionEvent e) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/view/signup.fxml"));
            Stage stage = (Stage) btnLogin.getScene().getWindow();
            stage.getScene().setRoot(root);
            stage.setTitle("Bike Garage - Sign Up");
            stage.sizeToScene();
            stage.centerOnScreen();
        } catch (IOException ex) {
            ex.printStackTrace();
            showMessage("Could not open the sign-up page.", false);
        }
    }

    @FXML
    private void onForgotPassword(ActionEvent e) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/view/ForgetPassword.fxml"));
            Stage stage = (Stage) btnLogin.getScene().getWindow();
            stage.getScene().setRoot(root);
            stage.setTitle("Bike Garage - Reset Password");
            stage.sizeToScene();
            stage.centerOnScreen();
        } catch (IOException ex) {
            ex.printStackTrace();
            showMessage("Could not open the reset password page.", false);
        }
    }

    /* ---------- helpers ---------- */
    private void setBusy(boolean busy) {
        btnLogin.setDisable(busy);
        btnLogin.setText(busy ? "SIGNING IN..." : "LOGIN");
        txtUsername.setDisable(busy);
        txtPassword.setDisable(busy);
        txtPasswordVisible.setDisable(busy);
    }

    /** success = green message (e.g. account created), otherwise red error + shake. */
    private void showMessage(String msg, boolean success) {
        lblError.getStyleClass().removeAll("error-label", "success-label");
        lblError.getStyleClass().add(success ? "success-label" : "error-label");
        lblError.setText(msg);
        lblError.setVisible(true);
        lblError.setManaged(true);
        if (!success) shake();
    }

    private void hideMessage() {
        lblError.setVisible(false);
        lblError.setManaged(false);
    }

    /** Small left-right shake of the form when the login fails. */
    private void shake() {
        Timeline t = new Timeline(
                new KeyFrame(Duration.millis(0),   new KeyValue(loginCard.translateXProperty(), 0)),
                new KeyFrame(Duration.millis(60),  new KeyValue(loginCard.translateXProperty(), -10)),
                new KeyFrame(Duration.millis(120), new KeyValue(loginCard.translateXProperty(), 10)),
                new KeyFrame(Duration.millis(180), new KeyValue(loginCard.translateXProperty(), -8)),
                new KeyFrame(Duration.millis(240), new KeyValue(loginCard.translateXProperty(), 8)),
                new KeyFrame(Duration.millis(300), new KeyValue(loginCard.translateXProperty(), 0)));
        t.play();
    }
}