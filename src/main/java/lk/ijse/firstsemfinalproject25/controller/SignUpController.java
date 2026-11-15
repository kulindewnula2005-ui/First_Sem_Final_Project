package lk.ijse.firstsemfinalproject25.controller;

import lk.ijse.firstsemfinalproject25.model.UserModel;
import javafx.animation.PauseTransition;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

public class SignUpController implements Initializable {

    @FXML private VBox signupCard;
    @FXML private ImageView logoView, bannerImage;
    @FXML private TextField txtFullName, txtUsername;
    @FXML private ComboBox<String> cmbRole;
    @FXML private PasswordField txtPassword, txtConfirm;
    @FXML private Label lblUsernameStatus, lblStrength, lblConfirmStatus, lblError;
    @FXML private ProgressBar barStrength;
    @FXML private Button btnSignUp;

    private static final Pattern USERNAME_RULE = Pattern.compile("^[A-Za-z0-9_.]{4,20}$");
    private static final int MIN_PASSWORD = 6;

    private final UserModel userModel = new UserModel();

    /** null = not checked yet, TRUE = free to use, FALSE = already taken */
    private Boolean usernameAvailable = null;

    /** waits until the user stops typing before asking the database */
    private final PauseTransition debounce = new PauseTransition(Duration.millis(400));

    /* ======================================================= */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setImage(logoView, "/images/logo.png");
        setImage(bannerImage, "/images/login-banner.jpg");

        cmbRole.getItems().setAll("Receptionist", "Mechanic");
        cmbRole.getSelectionModel().selectFirst();
        lockRoleIfFirstAccount();

        // ---- everything below updates LIVE while the user types ----
        debounce.setOnFinished(e -> checkUsernameInDatabase());

        txtFullName.textProperty().addListener((o, a, b) -> refresh());
        txtUsername.textProperty().addListener((o, a, b) -> onUsernameTyped());
        cmbRole.valueProperty().addListener((o, a, b) -> refresh());
        txtPassword.textProperty().addListener((o, a, b) -> { updateStrength(); updateConfirm(); refresh(); });
        txtConfirm.textProperty().addListener((o, a, b) -> { updateConfirm(); refresh(); });

        javafx.application.Platform.runLater(txtFullName::requestFocus);
    }

    private void setImage(ImageView view, String path) {
        URL res = getClass().getResource(path);
        if (res != null) view.setImage(new Image(res.toExternalForm()));
    }

    /** If the user table is empty, the first account is created as Admin. */
    private void lockRoleIfFirstAccount() {
        Task<Boolean> task = new Task<>() {
            @Override protected Boolean call() throws Exception { return userModel.isEmpty(); }
        };
        task.setOnSucceeded(e -> {
            if (task.getValue()) {
                cmbRole.getItems().setAll(UserModel.SUPER_ADMIN);
                cmbRole.getSelectionModel().selectFirst();
                cmbRole.setDisable(true);
            }
        });
        runInBackground(task, "signup-first-user");
    }

    /* ---------- live username check ---------- */
    private void onUsernameTyped() {
        usernameAvailable = null;
        debounce.stop();
        String name = txtUsername.getText().trim();

        if (name.isEmpty()) {
            setStatus(lblUsernameStatus, "", "hint-label");
        } else if (!USERNAME_RULE.matcher(name).matches()) {
            setStatus(lblUsernameStatus, "✖ 4-20 characters: letters, numbers, _ or .", "status-bad");
        } else {
            setStatus(lblUsernameStatus, "Checking availability...", "status-wait");
            debounce.playFromStart();
        }
        refresh();
    }

    private void checkUsernameInDatabase() {
        final String name = txtUsername.getText().trim();
        if (!USERNAME_RULE.matcher(name).matches()) return;

        Task<Boolean> task = new Task<>() {
            @Override protected Boolean call() throws Exception { return userModel.usernameExists(name); }
        };
        task.setOnSucceeded(e -> {
            if (!name.equals(txtUsername.getText().trim())) return;   // user kept typing - ignore old answer
            usernameAvailable = !task.getValue();
            if (usernameAvailable) setStatus(lblUsernameStatus, "✔ Username is available", "status-ok");
            else                   setStatus(lblUsernameStatus, "✖ Username is already taken", "status-bad");
            refresh();
        });
        task.setOnFailed(e -> {
            task.getException().printStackTrace();
            setStatus(lblUsernameStatus, "✖ Cannot reach the database", "status-bad");
        });
        runInBackground(task, "signup-username-check");
    }

    /* ---------- live password feedback ---------- */
    private void updateStrength() {
        String p = txtPassword.getText();
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
        else if (c.equals(txtPassword.getText())) setStatus(lblConfirmStatus, "✔ Passwords match", "status-ok");
        else setStatus(lblConfirmStatus, "✖ Passwords do not match", "status-bad");
    }

    /** The Sign Up button stays disabled until every field is valid. */
    private void refresh() {
        hideError();
        boolean valid =
                txtFullName.getText().trim().length() >= 3
                        && USERNAME_RULE.matcher(txtUsername.getText().trim()).matches()
                        && Boolean.TRUE.equals(usernameAvailable)
                        && cmbRole.getValue() != null
                        && txtPassword.getText().length() >= MIN_PASSWORD
                        && txtPassword.getText().equals(txtConfirm.getText());
        btnSignUp.setDisable(!valid);
    }

    /* ---------- sign up ---------- */
    @FXML
    private void onSignUp(ActionEvent e) {
        final String fullName = txtFullName.getText().trim();
        final String username = txtUsername.getText().trim();
        final String password = txtPassword.getText();
        final String role = cmbRole.getValue();

        setBusy(true);
        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                userModel.register(fullName, username, password, role);
                return null;
            }
        };
        task.setOnSucceeded(ev -> goToLogin(username, "Account created! Please sign in."));
        task.setOnFailed(ev -> {
            setBusy(false);
            Throwable ex = task.getException();
            if (ex instanceof UserModel.UsernameTakenException) {
                usernameAvailable = false;
                setStatus(lblUsernameStatus, "✖ Username is already taken", "status-bad");
                showError("That username was just taken. Please choose another one.");
            } else {
                ex.printStackTrace();
                showError("Could not create the account. Check that MySQL is running.");
            }
        });
        runInBackground(task, "signup-register");
    }

    @FXML
    private void onBackToLogin(ActionEvent e) {
        goToLogin(null, null);
    }

    private void goToLogin(String username, String message) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/LoginPage.fxml"));
            Parent root = loader.load();
            if (username != null) {
                LoginController login = loader.getController();
                login.prefill(username, message);
            }
            Stage stage = (Stage) btnSignUp.getScene().getWindow();
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
        btnSignUp.setDisable(busy);
        btnSignUp.setText(busy ? "CREATING..." : "SIGN UP");
        txtFullName.setDisable(busy);
        txtUsername.setDisable(busy);
        txtPassword.setDisable(busy);
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

    private void runInBackground(Task<?> task, String name) {
        Thread t = new Thread(task, name);
        t.setDaemon(true);
        t.start();
    }
}
