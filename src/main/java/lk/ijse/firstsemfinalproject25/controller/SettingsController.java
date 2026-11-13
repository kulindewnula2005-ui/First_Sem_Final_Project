package lk.ijse.firstsemfinalproject25.controller;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import lk.ijse.firstsemfinalproject25.dto.UserDTO;
import lk.ijse.firstsemfinalproject25.model.UserModel;
import lk.ijse.firstsemfinalproject25.util.AppSettings;
import lk.ijse.firstsemfinalproject25.util.CrudUtil;
import lk.ijse.firstsemfinalproject25.util.Session;
import lk.ijse.firstsemfinalproject25.util.ThemeManager;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;

public class SettingsController implements Initializable {

    private static final DateTimeFormatter SHOWN_STAMP =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter FOLDER_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** Tables copied by the backup, in the order of the ER diagram. */
    private static final List<String> BACKUP_TABLES = List.of(
            "user", "customer", "bike", "mechanic", "service_request",
            "spare_part", "service_parts", "bill", "payment");

    /* ---------- appearance ---------- */
    @FXML private VBox root;
    @FXML private ToggleGroup themeGroup;
    @FXML private ToggleButton tglDark;
    @FXML private ToggleButton tglLight;

    /* ---------- my account ---------- */
    @FXML private TextField txtFullName;
    @FXML private TextField txtUsername;
    @FXML private Label lblRole;
    @FXML private Button btnSaveProfile;
    @FXML private Label lblProfileMsg;
    @FXML private PasswordField pfCurrent;
    @FXML private PasswordField pfNew;
    @FXML private PasswordField pfConfirm;
    @FXML private Button btnChangePassword;
    @FXML private Label lblPasswordMsg;

    /* ---------- garage information ---------- */
    @FXML private TextField txtGarageName;
    @FXML private TextField txtGaragePhone;
    @FXML private TextField txtGarageAddress;
    @FXML private TextField txtGarageEmail;
    @FXML private Label lblGarageMsg;

    /* ---------- user accounts ---------- */
    @FXML private VBox cardUsers;
    @FXML private TableView<UserDTO> tblUsers;
    @FXML private TableColumn<UserDTO, String> colUId;
    @FXML private TableColumn<UserDTO, String> colUName;
    @FXML private TableColumn<UserDTO, String> colUUsername;
    @FXML private TableColumn<UserDTO, String> colURole;
    @FXML private TableColumn<UserDTO, Void> colUActions;
    @FXML private Label lblUsersMsg;

    /* ---------- backup ---------- */
    @FXML private Label lblLastBackup;
    @FXML private Button btnBackup;
    @FXML private ProgressBar progBackup;
    @FXML private Label lblBackupMsg;

    /* ---------- about ---------- */
    @FXML private Label lblJava;
    @FXML private Label lblFx;
    @FXML private Label lblDb;

    private final UserModel userModel = new UserModel();

    private UserDTO me;                        // the user who is signed in
    private List<UserDTO> users = new ArrayList<>();

    private record AccountData(UserDTO me, List<UserDTO> users) {}

    private record BackupResult(File folder, int tables, int rows) {}

    /* ======================================================= */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupThemeTiles();
        loadGarage();
        setupUserTable();
        showLastBackup();

        lblJava.setText(System.getProperty("java.version") + "  (" + System.getProperty("java.vendor") + ")");
        lblFx.setText(System.getProperty("javafx.version", "-"));

        loadAccount();
        testConnection();
    }

    /* ======================= APPEARANCE ======================= */
    private void setupThemeTiles() {
        ThemeManager.init();
        boolean light = ThemeManager.current() == ThemeManager.Theme.LIGHT;
        tglLight.setSelected(light);
        tglDark.setSelected(!light);

        // never allow "no theme selected"
        themeGroup.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null && old != null) {
                old.setSelected(true);
            }
        });
    }

    @FXML
    private void onTheme(ActionEvent e) {
        ThemeManager.setTheme(e.getSource() == tglLight
                ? ThemeManager.Theme.LIGHT : ThemeManager.Theme.DARK);
    }

    /* ======================= ACCOUNT DATA (background thread) ======================= */
    private void loadAccount() {
        Task<AccountData> task = new Task<>() {
            @Override
            protected AccountData call() throws Exception {
                UserDTO signedIn = Session.ensureUser();
                List<UserDTO> all = userModel.getAllUsers();
                UserDTO fresh = signedIn;
                for (UserDTO u : all) {
                    if (signedIn != null && u.getUserId().equals(signedIn.getUserId())) {
                        fresh = u;   // newest data from the database
                    }
                }
                return new AccountData(fresh, all);
            }
        };
        task.setOnSucceeded(e -> applyAccount(task.getValue()));
        task.setOnFailed(e -> showMsg(lblProfileMsg,
                "Could not load the accounts: " + task.getException().getMessage(), true));
        startThread(task, "settings-load");
    }

    private void applyAccount(AccountData d) {
        me = d.me();
        users = new ArrayList<>(d.users());

        boolean hasUser = me != null;
        txtFullName.setDisable(!hasUser);
        txtUsername.setDisable(!hasUser);
        btnSaveProfile.setDisable(!hasUser);
        pfCurrent.setDisable(!hasUser);
        pfNew.setDisable(!hasUser);
        pfConfirm.setDisable(!hasUser);
        btnChangePassword.setDisable(!hasUser);

        if (hasUser) {
            Session.setUser(me);   // keeps the dashboard name label up to date
            txtFullName.setText(me.getFullName());
            txtUsername.setText(me.getUsername());
            lblRole.setText(me.getRole());
        } else {
            lblRole.setText("-");
            showMsg(lblProfileMsg, "No user account exists yet. Add a user account in the database first.", true);
        }

        // only an Admin manages the other accounts
        boolean admin = hasUser && "Admin".equals(me.getRole());
        cardUsers.setVisible(admin);
        cardUsers.setManaged(admin);

        tblUsers.setItems(FXCollections.observableArrayList(users));
        tblUsers.refresh();
    }

    /* ======================= MY ACCOUNT ======================= */
    @FXML
    private void onSaveProfile(ActionEvent e) {
        hideMsg(lblProfileMsg);
        if (me == null) {
            return;
        }
        try {
            UserDTO dto = new UserDTO(me.getUserId(), txtUsername.getText(),
                    me.getPasswordHash(), me.getRole(), txtFullName.getText());
            if (!userModel.update(dto)) {
                throw new Exception("The profile could not be saved.");
            }
            me = new UserDTO(me.getUserId(), txtUsername.getText().trim(),
                    me.getPasswordHash(), me.getRole(), txtFullName.getText().trim());
            Session.setUser(me);
            showMsg(lblProfileMsg, "Profile updated.", false);
            loadAccount();
        } catch (Exception ex) {
            showMsg(lblProfileMsg, ex.getMessage(), true);
        }
    }

    @FXML
    private void onChangePassword(ActionEvent e) {
        hideMsg(lblPasswordMsg);
        if (me == null) {
            return;
        }
        try {
            String current = pfCurrent.getText();
            String next = pfNew.getText();
            String again = pfConfirm.getText();

            if (current == null || current.isEmpty()) {
                throw new Exception("Enter your current password.");
            }
            if (next == null || next.length() < 4) {
                throw new Exception("The new password must be at least 4 characters.");
            }
            if (!next.equals(again)) {
                throw new Exception("The new passwords do not match.");
            }
            if (next.equals(current)) {
                throw new Exception("The new password must be different from the current one.");
            }
            if (userModel.authenticate(me.getUsername(), current) == null) {
                throw new Exception("The current password is wrong.");
            }
            if (!userModel.changePassword(me.getUserId(), next)) {
                throw new Exception("The password could not be changed.");
            }

            pfCurrent.clear();
            pfNew.clear();
            pfConfirm.clear();
            showMsg(lblPasswordMsg, "Password updated.", false);
        } catch (Exception ex) {
            showMsg(lblPasswordMsg, ex.getMessage(), true);
        }
    }

    /* ======================= GARAGE INFORMATION ======================= */
    private void loadGarage() {
        txtGarageName.setText(AppSettings.get(AppSettings.GARAGE_NAME, "Bike Garage"));
        txtGarageAddress.setText(AppSettings.get(AppSettings.GARAGE_ADDRESS, ""));
        txtGaragePhone.setText(AppSettings.get(AppSettings.GARAGE_PHONE, ""));
        txtGarageEmail.setText(AppSettings.get(AppSettings.GARAGE_EMAIL, ""));
    }

    @FXML
    private void onSaveGarage(ActionEvent e) {
        hideMsg(lblGarageMsg);

        String name = clean(txtGarageName);
        String address = clean(txtGarageAddress);
        String phone = clean(txtGaragePhone);
        String email = clean(txtGarageEmail);

        if (name.isEmpty()) {
            showMsg(lblGarageMsg, "Enter the garage name.", true);
            return;
        }
        if (!phone.isEmpty() && !phone.matches("[0-9 +()\\-]{7,20}")) {
            showMsg(lblGarageMsg, "Enter a valid phone number.", true);
            return;
        }
        if (!email.isEmpty() && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            showMsg(lblGarageMsg, "Enter a valid email address.", true);
            return;
        }

        AppSettings.set(AppSettings.GARAGE_NAME, name);
        AppSettings.set(AppSettings.GARAGE_ADDRESS, address);
        AppSettings.set(AppSettings.GARAGE_PHONE, phone);
        AppSettings.set(AppSettings.GARAGE_EMAIL, email);
        try {
            AppSettings.save();
            showMsg(lblGarageMsg, "Garage details saved.", false);
        } catch (IOException ex) {
            showMsg(lblGarageMsg, "Could not save: " + ex.getMessage(), true);
        }
    }

    private String clean(TextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    /* ======================= USER ACCOUNTS ======================= */
    private void setupUserTable() {
        colUId.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getUserId()));
        colUName.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue().getFullName() + (isMe(c.getValue()) ? "   (you)" : "")));
        colUUsername.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getUsername()));
        colURole.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getRole()));

        // role badge
        colURole.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String role, boolean empty) {
                super.updateItem(role, empty);
                if (empty || role == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Label badge = new Label(role);
                badge.getStyleClass().add("badge");
                switch (role) {
                    case "Admin"        -> badge.getStyleClass().add("role-admin");
                    case "Receptionist" -> badge.getStyleClass().add("badge-progress");
                    case "Mechanic"     -> badge.getStyleClass().add("badge-pending");
                    default -> { }
                }
                setGraphic(badge);
                setText(null);
            }
        });

        // edit + reset password + delete icons
        colUActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnEdit = new Button("✎");
            private final Button btnKey = new Button("🔑");
            private final Button btnDelete = new Button("🗑");
            private final HBox box = new HBox(4, btnEdit, btnKey, btnDelete);

            {
                btnEdit.getStyleClass().addAll("action-button", "edit-button");
                btnKey.getStyleClass().addAll("action-button", "view-button");
                btnDelete.getStyleClass().addAll("action-button", "delete-button");
                box.setAlignment(Pos.CENTER_LEFT);

                btnEdit.setOnAction(e -> openUserForm(getTableView().getItems().get(getIndex())));
                btnKey.setOnAction(e -> openResetPassword(getTableView().getItems().get(getIndex())));
                btnDelete.setOnAction(e -> confirmDeleteUser(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        tblUsers.setPlaceholder(new Label("No users found"));
    }

    private boolean isMe(UserDTO user) {
        return me != null && me.getUserId().equals(user.getUserId());
    }

    private int adminCount() {
        int n = 0;
        for (UserDTO u : users) {
            if ("Admin".equals(u.getRole())) {
                n++;
            }
        }
        return n;
    }

    private boolean isLinkedToMechanic(String userId) throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT mechanic_id FROM mechanic WHERE user_id=?", userId);
        return rs.next();
    }

    @FXML
    private void onAddUser(ActionEvent e) {
        openUserForm(null);
    }

    /* ---------- add / edit a user ---------- */
    private void openUserForm(UserDTO existing) {
        hideMsg(lblUsersMsg);
        boolean editing = existing != null;
        boolean self = editing && isMe(existing);

        String newId;
        try {
            newId = editing ? existing.getUserId() : userModel.getNextId();
        } catch (Exception ex) {
            showMsg(lblUsersMsg, "Could not create a user ID: " + ex.getMessage(), true);
            return;
        }

        Dialog<ButtonType> dialog = newDialog(editing ? "Edit User" : "Add User", 480);
        DialogPane pane = dialog.getDialogPane();

        ButtonType saveType = new ButtonType(editing ? "Update" : "Save", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        TextField txtId = new TextField(newId);
        txtId.setDisable(true);
        TextField txtName = new TextField(editing ? existing.getFullName() : "");
        TextField txtUser = new TextField(editing ? existing.getUsername() : "");
        txtName.setPromptText("e.g. Kasun Perera");
        txtUser.setPromptText("e.g. kasun");

        ComboBox<String> cmbRole = new ComboBox<>(FXCollections.observableArrayList(UserModel.ROLES));
        cmbRole.setMaxWidth(Double.MAX_VALUE);
        cmbRole.getStyleClass().add("form-combo");
        cmbRole.setValue(editing ? existing.getRole() : "Receptionist");
        if (self) {
            cmbRole.setDisable(true);   // nobody can change their own role
        }

        PasswordField pf1 = new PasswordField();
        PasswordField pf2 = new PasswordField();
        pf1.setPromptText("at least 4 characters");
        pf2.setPromptText("repeat the password");

        Label title = new Label(editing ? "Edit User" : "Add User");
        title.getStyleClass().add("form-title");

        Label hint = new Label(editing
                ? (self ? "You cannot change your own role. Use the 🔑 icon to reset a password."
                : "Use the 🔑 icon in the table to reset this user's password.")
                : "The user signs in with this username and password. Mechanic accounts can be linked on the Mechanic page.");
        hint.getStyleClass().add("set-hint");
        hint.setWrapText(true);

        Label lblError = errorLabel();

        VBox content = new VBox(10, title,
                row(field("User ID", txtId), field("Role *", cmbRole)),
                field("Full name *", txtName),
                field("Username *", txtUser));
        if (!editing) {
            content.getChildren().add(row(field("Password *", pf1), field("Confirm password *", pf2)));
        }
        content.getChildren().addAll(hint, lblError);
        content.setPadding(new Insets(6, 4, 4, 4));
        pane.setContent(content);

        Button btnSave = (Button) pane.lookupButton(saveType);
        btnSave.getStyleClass().add("form-save");

        boolean[] saved = {false};
        btnSave.addEventFilter(ActionEvent.ACTION, ev -> {
            try {
                String role = cmbRole.getValue();
                if (role == null) {
                    throw new Exception("Select the role.");
                }

                if (editing) {
                    boolean roleChanged = !role.equals(existing.getRole());
                    if (roleChanged && "Admin".equals(existing.getRole()) && adminCount() <= 1) {
                        throw new Exception("At least one Admin account must remain.");
                    }
                    if (roleChanged && "Mechanic".equals(existing.getRole())
                            && isLinkedToMechanic(existing.getUserId())) {
                        throw new Exception("This account is linked to a mechanic. Unlink it on the Mechanic page first.");
                    }
                    UserDTO dto = new UserDTO(existing.getUserId(), txtUser.getText(),
                            existing.getPasswordHash(), role, txtName.getText());
                    if (!userModel.update(dto)) {
                        throw new Exception("The user could not be saved.");
                    }
                } else {
                    if (!pf1.getText().equals(pf2.getText())) {
                        throw new Exception("The passwords do not match.");
                    }
                    UserDTO dto = new UserDTO(newId, txtUser.getText(), null, role, txtName.getText());
                    if (!userModel.save(dto, pf1.getText())) {
                        throw new Exception("The user could not be saved.");
                    }
                }
                saved[0] = true;
            } catch (Exception ex) {
                showInline(lblError, ex.getMessage());
                ev.consume();   // keep the dialog open
            }
        });

        Platform.runLater(txtName::requestFocus);
        dialog.showAndWait();

        if (saved[0]) {
            showMsg(lblUsersMsg, editing ? "User updated." : "User added.", false);
            loadAccount();
        }
    }

    /* ---------- reset a password (Admin) ---------- */
    private void openResetPassword(UserDTO target) {
        hideMsg(lblUsersMsg);

        Dialog<ButtonType> dialog = newDialog("Reset Password", 420);
        DialogPane pane = dialog.getDialogPane();

        ButtonType saveType = new ButtonType("Reset Password", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        Label title = new Label("Reset Password");
        title.getStyleClass().add("form-title");
        Label who = new Label(target.getFullName() + "  (" + target.getUsername() + ")");
        who.getStyleClass().add("set-hint");

        PasswordField pf1 = new PasswordField();
        PasswordField pf2 = new PasswordField();
        pf1.setPromptText("at least 4 characters");
        pf2.setPromptText("repeat the password");
        Label lblError = errorLabel();

        VBox content = new VBox(10, title, who,
                field("New password *", pf1), field("Confirm password *", pf2), lblError);
        content.setPadding(new Insets(6, 4, 4, 4));
        pane.setContent(content);

        Button btnSave = (Button) pane.lookupButton(saveType);
        btnSave.getStyleClass().add("form-save");

        boolean[] saved = {false};
        btnSave.addEventFilter(ActionEvent.ACTION, ev -> {
            try {
                if (!pf1.getText().equals(pf2.getText())) {
                    throw new Exception("The passwords do not match.");
                }
                if (!userModel.changePassword(target.getUserId(), pf1.getText())) {
                    throw new Exception("The password could not be changed.");
                }
                saved[0] = true;
            } catch (Exception ex) {
                showInline(lblError, ex.getMessage());
                ev.consume();
            }
        });

        Platform.runLater(pf1::requestFocus);
        dialog.showAndWait();

        if (saved[0]) {
            showMsg(lblUsersMsg, "Password changed for " + target.getUsername() + ".", false);
        }
    }

    /* ---------- delete a user ---------- */
    private void confirmDeleteUser(UserDTO target) {
        hideMsg(lblUsersMsg);

        if (isMe(target)) {
            showMsg(lblUsersMsg, "You cannot delete the account you are signed in with.", true);
            return;
        }
        if ("Admin".equals(target.getRole()) && adminCount() <= 1) {
            showMsg(lblUsersMsg, "At least one Admin account must remain.", true);
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete the account " + target.getUsername() + " (" + target.getFullName() + ")?",
                ButtonType.YES, ButtonType.NO);
        alert.initOwner(root.getScene().getWindow());
        alert.setTitle("Delete User");
        alert.setHeaderText(null);
        styleDialog(alert.getDialogPane());

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            try {
                userModel.delete(target.getUserId());
                showMsg(lblUsersMsg, "User deleted.", false);
                loadAccount();
            } catch (Exception ex) {
                showMsg(lblUsersMsg, ex.getMessage(), true);
            }
        }
    }

    /* ======================= DATA BACKUP ======================= */
    private void showLastBackup() {
        lblLastBackup.setText(AppSettings.get(AppSettings.LAST_BACKUP, "Never"));
    }

    @FXML
    private void onBackup(ActionEvent e) {
        hideMsg(lblBackupMsg);

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose where to save the backup");
        File start = new File(AppSettings.get(AppSettings.BACKUP_FOLDER, System.getProperty("user.home")));
        if (start.isDirectory()) {
            chooser.setInitialDirectory(start);
        }
        File parent = chooser.showDialog(root.getScene().getWindow());
        if (parent == null) {
            return;   // cancelled
        }

        btnBackup.setDisable(true);
        progBackup.setVisible(true);
        progBackup.setManaged(true);

        Task<BackupResult> task = new Task<>() {
            @Override
            protected BackupResult call() throws Exception {
                File folder = new File(parent, "bike-garage-backup-" + FOLDER_STAMP.format(LocalDateTime.now()));
                if (!folder.mkdirs()) {
                    throw new IOException("Could not create the folder " + folder.getAbsolutePath());
                }
                int rows = 0;
                for (String table : BACKUP_TABLES) {
                    rows += exportTable(table, new File(folder, table + ".csv"));
                }
                return new BackupResult(folder, BACKUP_TABLES.size(), rows);
            }
        };
        task.setOnSucceeded(ev -> {
            BackupResult r = task.getValue();
            AppSettings.set(AppSettings.LAST_BACKUP, SHOWN_STAMP.format(LocalDateTime.now()));
            AppSettings.set(AppSettings.BACKUP_FOLDER, parent.getAbsolutePath());
            try {
                AppSettings.save();
            } catch (IOException ignore) {
                // the backup itself is fine
            }
            showLastBackup();
            endBackup();
            showMsg(lblBackupMsg, "Backup saved: " + r.tables() + " files, " + r.rows()
                    + " records  →  " + r.folder().getAbsolutePath(), false);
        });
        task.setOnFailed(ev -> {
            endBackup();
            showMsg(lblBackupMsg, "Backup failed: " + task.getException().getMessage(), true);
        });
        startThread(task, "settings-backup");
    }

    private void endBackup() {
        btnBackup.setDisable(false);
        progBackup.setVisible(false);
        progBackup.setManaged(false);
    }

    /** Writes one table to a CSV file and returns the number of rows. The password column is left out. */
    private int exportTable(String table, File file) throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT * FROM `" + table + "`");
        ResultSetMetaData md = rs.getMetaData();

        List<Integer> columns = new ArrayList<>();
        for (int i = 1; i <= md.getColumnCount(); i++) {
            if (!"password_hash".equalsIgnoreCase(md.getColumnLabel(i))) {
                columns.add(i);
            }
        }

        int rows = 0;
        try (BufferedWriter out = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            out.write('\uFEFF');   // byte-order mark, so Excel reads the file as UTF-8

            StringBuilder header = new StringBuilder();
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) {
                    header.append(',');
                }
                header.append(csv(md.getColumnLabel(columns.get(i))));
            }
            out.write(header.toString());
            out.write("\r\n");

            while (rs.next()) {
                StringBuilder line = new StringBuilder();
                for (int i = 0; i < columns.size(); i++) {
                    if (i > 0) {
                        line.append(',');
                    }
                    line.append(csv(rs.getString(columns.get(i))));
                }
                out.write(line.toString());
                out.write("\r\n");
                rows++;
            }
        }
        return rows;
    }

    private String csv(String value) {
        String v = value == null ? "" : value;
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    /* ======================= ABOUT ======================= */
    @FXML
    private void onTestConnection(ActionEvent e) {
        testConnection();
    }

    private void testConnection() {
        lblDb.setText("Checking...");
        setStatus(lblDb, null);

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                ResultSet rs = CrudUtil.execute("SELECT DATABASE(), VERSION()");
                rs.next();
                return rs.getString(1) + "   ·   MySQL " + rs.getString(2);
            }
        };
        task.setOnSucceeded(e -> {
            lblDb.setText("●  Connected   ·   " + task.getValue());
            setStatus(lblDb, true);
        });
        task.setOnFailed(e -> {
            lblDb.setText("●  Not connected: " + task.getException().getMessage());
            setStatus(lblDb, false);
        });
        startThread(task, "settings-db-test");
    }

    /** null = neutral, true = green, false = red. */
    private void setStatus(Label label, Boolean ok) {
        label.getStyleClass().removeAll("set-value", "set-ok", "set-bad");
        label.getStyleClass().add(ok == null ? "set-value" : (ok ? "set-ok" : "set-bad"));
    }

    /* ======================= helpers ======================= */
    private void startThread(Task<?> task, String name) {
        Thread t = new Thread(task, name);
        t.setDaemon(true);
        t.start();
    }

    private void showMsg(Label label, String text, boolean error) {
        label.getStyleClass().removeAll("form-error", "set-ok");
        label.getStyleClass().add(error ? "form-error" : "set-ok");
        label.setText(text);
        label.setVisible(true);
        label.setManaged(true);
    }

    private void hideMsg(Label label) {
        label.setVisible(false);
        label.setManaged(false);
    }

    private Dialog<ButtonType> newDialog(String title, double width) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(root.getScene().getWindow());
        dialog.setTitle(title);
        DialogPane pane = dialog.getDialogPane();
        styleDialog(pane);
        pane.setPrefWidth(width);
        return dialog;
    }

    private VBox field(String labelText, Node input) {
        Label label = new Label(labelText);
        label.getStyleClass().add("form-label");
        if (input instanceof TextField) {
            input.getStyleClass().add("form-field");   // also covers PasswordField
        }
        VBox box = new VBox(4, label, input);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private HBox row(VBox left, VBox right) {
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        left.setPrefWidth(200);
        right.setPrefWidth(200);
        return new HBox(10, left, right);
    }

    private Label errorLabel() {
        Label lbl = new Label();
        lbl.getStyleClass().add("form-error");
        lbl.setWrapText(true);
        lbl.setVisible(false);
        lbl.setManaged(false);
        return lbl;
    }

    private void showInline(Label lbl, String message) {
        lbl.setText(message);
        lbl.setVisible(true);
        lbl.setManaged(true);
    }

    private void styleDialog(DialogPane pane) {
        for (String file : new String[]{"/styles/bike.css", "/styles/settings.css"}) {
            URL css = getClass().getResource(file);
            if (css != null) {
                pane.getStylesheets().add(css.toExternalForm());
            }
        }
        pane.getStyleClass().add("dark-dialog");
    }
}