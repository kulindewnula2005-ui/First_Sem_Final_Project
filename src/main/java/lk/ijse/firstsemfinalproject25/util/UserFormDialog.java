package lk.ijse.firstsemfinalproject25.util;

import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import lk.ijse.firstsemfinalproject25.dto.UserDTO;
import lk.ijse.firstsemfinalproject25.model.UserModel;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Super Admin: add a new user (existing == null) or edit one.
 * The new user gets only a username + password - no reset PIN (a PIN is made later, only when needed).
 */
public class UserFormDialog {

    private static final Pattern USERNAME_RULE = Pattern.compile("^[A-Za-z0-9_.]{4,20}$");
    private static final int MIN_PASSWORD = 6;

    private final Window owner;
    private final UserDTO existing;
    private final UserModel userModel = new UserModel();

    public UserFormDialog(Window owner, UserDTO existing) {
        this.owner = owner;
        this.existing = existing;
    }

    /** @return true when a user was saved */
    public boolean show() {
        final boolean adding = existing == null;

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(adding ? "Add User" : "Edit User");

        DialogPane pane = dialog.getDialogPane();
        styleDialog(pane);
        pane.setPrefWidth(420);

        ButtonType saveType = new ButtonType(adding ? "Add User" : "Save", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        Label title = new Label(adding ? "Add New User" : "Edit " + existing.getUserId());
        title.getStyleClass().add("form-title");

        TextField txtName = new TextField(adding ? "" : existing.getFullName());
        txtName.setPromptText("e.g. Kasun Perera");
        txtName.getStyleClass().add("form-field");

        TextField txtUser = new TextField(adding ? "" : existing.getUsername());
        txtUser.setPromptText("4-20 letters, numbers, _ or .");
        txtUser.getStyleClass().add("form-field");

        // Super Admin is never offered - there is only one
        List<String> roles = new ArrayList<>(UserModel.ROLES);
        roles.remove(UserModel.SUPER_ADMIN);
        ComboBox<String> cmbRole = new ComboBox<>(FXCollections.observableArrayList(roles));
        cmbRole.setMaxWidth(Double.MAX_VALUE);
        cmbRole.getStyleClass().add("form-combo");
        if (!adding && UserModel.SUPER_ADMIN.equals(existing.getRole())) {
            cmbRole.setItems(FXCollections.observableArrayList(UserModel.SUPER_ADMIN));
            cmbRole.setValue(UserModel.SUPER_ADMIN);
            cmbRole.setDisable(true);
        } else {
            cmbRole.setValue(adding ? "Receptionist" : existing.getRole());
        }

        PasswordField txtPass = new PasswordField();
        txtPass.setPromptText("At least " + MIN_PASSWORD + " characters");
        txtPass.getStyleClass().add("form-field");
        PasswordField txtConfirm = new PasswordField();
        txtConfirm.setPromptText("Re-enter the password");
        txtConfirm.getStyleClass().add("form-field");

        Label lblError = new Label();
        lblError.getStyleClass().add("form-error");
        lblError.setWrapText(true);
        lblError.setVisible(false);
        lblError.setManaged(false);

        VBox content = new VBox(8, title,
                field("Full Name", txtName),
                field("Username", txtUser),
                field("Role", cmbRole));
        if (adding) {
            content.getChildren().addAll(field("Password", txtPass), field("Confirm Password", txtConfirm));
        }
        content.getChildren().add(lblError);
        content.setPadding(new Insets(6, 4, 4, 4));
        pane.setContent(content);

        Button btnSave = (Button) pane.lookupButton(saveType);
        btnSave.getStyleClass().add("form-save");

        boolean[] saved = {false};
        btnSave.addEventFilter(ActionEvent.ACTION, ev -> {
            try {
                String name = txtName.getText().trim();
                String username = txtUser.getText().trim();
                String role = cmbRole.getValue();

                if (name.length() < 3) {
                    throw new Exception("Enter the full name (at least 3 characters).");
                }
                if (!USERNAME_RULE.matcher(username).matches()) {
                    throw new Exception("Username must be 4-20 characters: letters, numbers, _ or .");
                }
                if (role == null) {
                    throw new Exception("Select a role.");
                }

                if (adding) {
                    if (txtPass.getText().length() < MIN_PASSWORD) {
                        throw new Exception("Password must be at least " + MIN_PASSWORD + " characters.");
                    }
                    if (!txtPass.getText().equals(txtConfirm.getText())) {
                        throw new Exception("The two passwords do not match.");
                    }
                    userModel.save(new UserDTO(userModel.getNextId(), username, null, role, name),
                            txtPass.getText());
                } else {
                    userModel.update(new UserDTO(existing.getUserId(), username,
                            existing.getPasswordHash(), role, name));
                }
                saved[0] = true;
            } catch (Exception ex) {
                lblError.setText(ex.getMessage());
                lblError.setVisible(true);
                lblError.setManaged(true);
                ev.consume();                  // keep the dialog open
            }
        });

        dialog.showAndWait();
        return saved[0];
    }

    private VBox field(String labelText, Node input) {
        Label label = new Label(labelText);
        label.getStyleClass().add("form-label");
        return new VBox(4, label, input);
    }

    private void styleDialog(DialogPane pane) {
        URL css = getClass().getResource("/styles/bike.css");
        if (css != null) {
            pane.getStylesheets().add(css.toExternalForm());
        }
        pane.getStyleClass().add("dark-dialog");
    }
}
