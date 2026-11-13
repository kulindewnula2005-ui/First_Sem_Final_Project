package lk.ijse.firstsemfinalproject25.util;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;
import lk.ijse.firstsemfinalproject25.dto.UserDTO;
import lk.ijse.firstsemfinalproject25.model.PasswordResetModel;
import lk.ijse.firstsemfinalproject25.model.UserModel;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * ADMIN tool: pick a user, press "Generate PIN", share the PIN privately
 * (phone / in person). A live countdown shows how long the PIN is still valid.
 *
 * Open it from your user-management page - and only show that button to the Admin role:
 *     new ResetPinDialog(root.getScene().getWindow()).show();
 */
public class ResetPinDialog {

    private final Window owner;
    private final UserModel userModel = new UserModel();
    private final PasswordResetModel resetModel = new PasswordResetModel();

    private final ComboBox<String> cmbUser = new ComboBox<>();
    private final Label lblPin = new Label("------");
    private final Label lblExpiry = new Label();
    private final Label lblMessage = new Label();
    private final Button btnGenerate = new Button("Generate PIN");
    private final Button btnCopy = new Button("Copy PIN");

    private Timeline countdown;
    private int secondsLeft;
    private String currentPin;

    public ResetPinDialog(Window owner) {
        this.owner = owner;
    }

    public void show() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Reset PIN");

        DialogPane pane = dialog.getDialogPane();
        styleDialog(pane);
        pane.setPrefWidth(440);
        pane.getButtonTypes().add(ButtonType.CLOSE);

        Label title = new Label("Password Reset PIN");
        title.getStyleClass().add("form-title");

        Label help = new Label("Choose the user, generate a PIN and share it with them privately. "
                + "The user enters it on the Forgot Password page.");
        help.getStyleClass().add("info-value");
        help.setWrapText(true);

        cmbUser.setPromptText("Select user...");
        cmbUser.setMaxWidth(Double.MAX_VALUE);
        cmbUser.getStyleClass().add("form-combo");
        showPrompt(cmbUser);
        HBox.setHgrow(cmbUser, Priority.ALWAYS);

        btnGenerate.getStyleClass().add("form-save");
        btnGenerate.setDisable(true);
        cmbUser.valueProperty().addListener((o, a, b) -> btnGenerate.setDisable(b == null));
        btnGenerate.setOnAction(e -> generate());

        HBox pickRow = new HBox(8, cmbUser, btnGenerate);
        pickRow.setAlignment(Pos.CENTER_LEFT);

        lblPin.setStyle("-fx-font-size: 36px; -fx-font-weight: bold; -fx-text-fill: #E8141C;");
        lblExpiry.getStyleClass().add("info-value");

        btnCopy.getStyleClass().add("form-save");
        btnCopy.setDisable(true);
        btnCopy.setOnAction(e -> copyPin());

        VBox pinBox = new VBox(4, lblPin, lblExpiry, btnCopy);
        pinBox.setAlignment(Pos.CENTER);
        pinBox.setPadding(new Insets(12, 0, 4, 0));

        lblMessage.setWrapText(true);
        lblMessage.setVisible(false);
        lblMessage.setManaged(false);

        VBox content = new VBox(10, title, help, pickRow, pinBox, lblMessage);
        content.setPadding(new Insets(6, 4, 4, 4));
        pane.setContent(content);

        loadUsers();

        dialog.setOnHidden(e -> stopCountdown());
        dialog.showAndWait();
    }

    /* ---------- actions ---------- */
    private void loadUsers() {
        try {
            List<String> items = new ArrayList<>();
            for (UserDTO u : userModel.getAllUsers()) {
                items.add(u.getUserId() + " - " + u.getUsername() + " (" + u.getRole() + ")");
            }
            cmbUser.setItems(FXCollections.observableArrayList(items));
        } catch (Exception ex) {
            showMessage("Could not load users: " + ex.getMessage(), true);
        }
    }

    private void generate() {
        String item = cmbUser.getValue();
        if (item == null) {
            return;
        }
        String userId = item.substring(0, item.indexOf(" - "));
        try {
            currentPin = resetModel.createPin(userId);
            lblPin.setText(currentPin);
            btnCopy.setDisable(false);
            startCountdown(PasswordResetModel.VALID_MINUTES * 60);
            showMessage("PIN created. Share it privately - it works once.", false);
        } catch (Exception ex) {
            showMessage(ex.getMessage(), true);
        }
    }

    private void copyPin() {
        if (currentPin == null) {
            return;
        }
        ClipboardContent cc = new ClipboardContent();
        cc.putString(currentPin);
        Clipboard.getSystemClipboard().setContent(cc);
        showMessage("PIN copied.", false);
    }

    /* ---------- live countdown ---------- */
    private void startCountdown(int seconds) {
        stopCountdown();
        secondsLeft = seconds;
        updateExpiry();
        countdown = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            secondsLeft--;
            updateExpiry();
            if (secondsLeft <= 0) {
                stopCountdown();
            }
        }));
        countdown.setCycleCount(Timeline.INDEFINITE);
        countdown.play();
    }

    private void stopCountdown() {
        if (countdown != null) {
            countdown.stop();
        }
    }

    private void updateExpiry() {
        if (secondsLeft <= 0) {
            lblExpiry.setText("PIN expired - generate a new one.");
            lblExpiry.setStyle("-fx-text-fill: #ff6b6b;");
            lblPin.setStyle("-fx-font-size: 36px; -fx-font-weight: bold; -fx-text-fill: #5c5c63;");
            btnCopy.setDisable(true);
            return;
        }
        lblExpiry.setText(String.format("Expires in %02d:%02d", secondsLeft / 60, secondsLeft % 60));
        lblExpiry.setStyle("");
        lblPin.setStyle("-fx-font-size: 36px; -fx-font-weight: bold; -fx-text-fill: #E8141C;");
    }

    /* ---------- helpers ---------- */
    private void showMessage(String text, boolean error) {
        lblMessage.getStyleClass().removeAll("form-error", "success-text");
        lblMessage.getStyleClass().add(error ? "form-error" : "success-text");
        lblMessage.setText(text);
        lblMessage.setVisible(true);
        lblMessage.setManaged(true);
    }

    private void showPrompt(ComboBox<String> combo) {
        combo.setButtonCell(new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(combo.getPromptText());
                    setStyle("-fx-text-fill: #7a7a82;");
                } else {
                    setText(item);
                    setStyle("");
                }
            }
        });
    }

    private void styleDialog(DialogPane pane) {
        URL css = getClass().getResource("/styles/bike.css");
        if (css != null) {
            pane.getStylesheets().add(css.toExternalForm());
        }
        pane.getStyleClass().add("dark-dialog");
    }
}