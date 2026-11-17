package lk.ijse.firstsemfinalproject25.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.concurrent.Task;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import lk.ijse.firstsemfinalproject25.dto.UserDTO;
import lk.ijse.firstsemfinalproject25.model.PasswordResetModel;
import lk.ijse.firstsemfinalproject25.model.UserModel;
import lk.ijse.firstsemfinalproject25.util.PinRequestsDialog;
import lk.ijse.firstsemfinalproject25.util.ResetPinDialog;
import lk.ijse.firstsemfinalproject25.util.Session;
import lk.ijse.firstsemfinalproject25.util.UserFormDialog;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * Super Admin dashboard: every page, live user numbers, user profile cards,
 * add / edit / delete users and the Reset PIN tool.
 */
public class AdminDashboardController implements Initializable {

    /* ---------- layout ---------- */
    @FXML private StackPane contentArea;
    @FXML private VBox homePane;
    @FXML private ToggleGroup navGroup;
    @FXML private ImageView logoView, bannerImage;

    /* ---------- header / user ---------- */
    @FXML private Label lblDateTime, lblUser, lblRole, lblWelcomeName;

    /* ---------- live numbers ---------- */
    @FXML private Label lblTotalUsers, lblAdmins, lblReceptionists, lblMechanics, lblActivePins;

    /* ---------- user cards ---------- */
    @FXML private Label lblUserCount;
    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cmbRoleFilter;
    @FXML private FlowPane cardsPane;

    private static final String ALL_ROLES = "All roles";
    private static final DateTimeFormatter CLOCK_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy   |   hh:mm:ss a");

    /** TODO: change these file names to match your own page files in resources/view. */
    private static final Map<String, String> PAGES = Map.of(
            "customer",   "/view/customer.fxml",
            "bike",       "/view/bike.fxml",
            "service",    "/view/service.fxml",
            "mechanic",   "/view/mechanic.fxml",
            "spare_part", "/view/spareParts.fxml",
            "billing",    "/view/billing.fxml",
            "reports",    "/view/reports.fxml",
            "settings",   "/view/settings.fxml");

    private final UserModel userModel = new UserModel();
    private final PasswordResetModel resetModel = new PasswordResetModel();

    private Timeline clockTimeline;
    private Timeline refreshTimeline;

    private List<UserDTO> users = new ArrayList<>();
    private Set<String> pinUserIds = new HashSet<>();
    private String lastSignature = "";

    private record AdminData(List<UserDTO> users, Map<String, Integer> roleCounts,
                             Set<String> pinUserIds, int activePins) {}

    /* ======================================================= */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // safety net: only the Super Admin may see this page
        if (!Session.isSuperAdmin()) {
            Label denied = new Label("Access denied - Super Admin only.");
            denied.setStyle("-fx-text-fill: #ff6b6b; -fx-font-size: 20px;");
            contentArea.getChildren().setAll(denied);
            return;
        }

        setImage(logoView, "/images/logo.png");
        setImage(bannerImage, "/images/banner.jpg");

        navGroup.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null && old != null) old.setSelected(true);
        });

        List<String> filters = new ArrayList<>();
        filters.add(ALL_ROLES);
        filters.addAll(UserModel.ROLES);
        cmbRoleFilter.setItems(FXCollections.observableArrayList(filters));
        cmbRoleFilter.setValue(ALL_ROLES);

        // live filtering (in memory)
        txtSearch.textProperty().addListener((o, a, b) -> rebuildCards());
        cmbRoleFilter.valueProperty().addListener((o, a, b) -> rebuildCards());

        startClock();
        startAutoRefresh();
    }

    private void setImage(ImageView view, String path) {
        URL res = getClass().getResource(path);
        if (res != null) view.setImage(new Image(res.toExternalForm()));
    }

    public void setUser(String name, String role) {
        lblUser.setText(name);
        lblRole.setText(role);
        lblWelcomeName.setText(name);
    }

    /* ======================= NAVIGATION ======================= */
    @FXML
    private void onNav(ActionEvent e) {
        openPage(((ToggleButton) e.getSource()).getId());
    }

    private void openPage(String page) {
        if ("dashboard".equals(page)) {
            contentArea.getChildren().setAll(homePane);
            loadData();
            return;
        }
        String path = PAGES.get(page);
        URL url = path == null ? null : getClass().getResource(path);
        if (url == null) {
            Label soon = new Label(page.toUpperCase() + " page coming soon");
            soon.setStyle("-fx-text-fill: white; -fx-font-size: 20px;");
            StackPane.setAlignment(soon, Pos.CENTER);
            contentArea.getChildren().setAll(soon);
            return;
        }
        try {
            Node node = FXMLLoader.load(url);
            contentArea.getChildren().setAll(node);
        } catch (IOException ex) {
            ex.printStackTrace();
            showError("Could not open the page", ex.getMessage());
        }
    }

    /* ======================= REAL-TIME ======================= */
    private void startClock() {
        updateClock();
        clockTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateClock()));
        clockTimeline.setCycleCount(Timeline.INDEFINITE);
        clockTimeline.play();
    }

    private void updateClock() {
        lblDateTime.setText(LocalDateTime.now().format(CLOCK_FORMAT));
    }

    private void startAutoRefresh() {
        loadData();
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(5), e -> loadData()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    /** Reads the database on a background thread so the screen never freezes. */
    private void loadData() {
        Task<AdminData> task = new Task<>() {
            @Override
            protected AdminData call() throws Exception {
                return new AdminData(
                        new ArrayList<>(userModel.getAllUsers()),
                        userModel.countByRole(),
                        resetModel.getUserIdsWithActivePin(),
                        resetModel.countActive());
            }
        };
        task.setOnSucceeded(e -> applyData(task.getValue()));
        task.setOnFailed(e -> task.getException().printStackTrace());
        Thread t = new Thread(task, "admin-refresh");
        t.setDaemon(true);
        t.start();
    }

    private void applyData(AdminData d) {
        Map<String, Integer> c = d.roleCounts();
        lblTotalUsers.setText(String.valueOf(d.users().size()));
        lblReceptionists.setText(String.valueOf(c.getOrDefault("Receptionist", 0)));
        lblMechanics.setText(String.valueOf(c.getOrDefault("Mechanic", 0)));
        lblActivePins.setText(String.valueOf(d.activePins()));

        users = d.users();
        pinUserIds = d.pinUserIds();

        // only rebuild the cards when something really changed (no flicker every 5 seconds)
        String signature = signature(d);
        if (!signature.equals(lastSignature)) {
            lastSignature = signature;
            rebuildCards();
        }
    }

    private String signature(AdminData d) {
        StringBuilder sb = new StringBuilder();
        for (UserDTO u : d.users()) {
            sb.append(u.getUserId()).append('|').append(u.getUsername()).append('|')
                    .append(u.getFullName()).append('|').append(u.getRole()).append('|')
                    .append(d.pinUserIds().contains(u.getUserId()) ? 1 : 0).append(';');
        }
        return sb.toString();
    }

    /* ======================= USER PROFILE CARDS ======================= */
    private void rebuildCards() {
        String kw = txtSearch.getText() == null ? "" : txtSearch.getText().trim().toLowerCase();
        String role = cmbRoleFilter.getValue();

        cardsPane.getChildren().clear();
        int shown = 0;
        for (UserDTO u : users) {
            boolean roleOk = role == null || ALL_ROLES.equals(role) || role.equals(u.getRole());
            boolean textOk = kw.isEmpty()
                    || u.getFullName().toLowerCase().contains(kw)
                    || u.getUsername().toLowerCase().contains(kw)
                    || u.getUserId().toLowerCase().contains(kw)
                    || u.getRole().toLowerCase().contains(kw);
            if (roleOk && textOk) {
                cardsPane.getChildren().add(buildCard(u, pinUserIds.contains(u.getUserId())));
                shown++;
            }
        }
        if (shown == 0) {
            Label empty = new Label("No users found");
            empty.getStyleClass().add("empty-text");
            cardsPane.getChildren().add(empty);
        }
        lblUserCount.setText(shown + " of " + users.size() + " users");
    }

    private VBox buildCard(UserDTO u, boolean pinActive) {
        String key = roleKey(u.getRole());
        boolean isSuper = UserModel.SUPER_ADMIN.equals(u.getRole());
        boolean isMe = Session.getUser() != null && u.getUserId().equals(Session.getUser().getUserId());

        Label avatar = new Label(initials(u.getFullName()));
        avatar.getStyleClass().addAll("avatar-circle", "avatar-" + key);

        Label name = new Label(u.getFullName());
        name.getStyleClass().add("card-name");
        name.setWrapText(true);
        name.setMaxWidth(180);
        name.setTextAlignment(TextAlignment.CENTER);
        name.setAlignment(Pos.CENTER);

        Label username = new Label("@" + u.getUsername());
        username.getStyleClass().add("card-username");

        Label role = new Label(u.getRole());
        role.getStyleClass().addAll("role-badge", "role-" + key);

        Label id = new Label(u.getUserId());
        id.getStyleClass().add("card-id");

        VBox card = new VBox(6, avatar, name, username, role);
        card.setAlignment(Pos.TOP_CENTER);
        card.getStyleClass().add("user-card");

        if (isMe) {
            card.getStyleClass().add("user-card-me");
            Label you = new Label("★ You");
            you.getStyleClass().add("you-chip");
            card.getChildren().add(you);
        }
        if (pinActive) {
            Label chip = new Label("🔑 Reset PIN active");
            chip.getStyleClass().add("pin-chip");
            card.getChildren().add(chip);
        }
        card.getChildren().add(id);

        Button btnPin = miniButton("🔑", "Generate reset PIN", false);
        Button btnEdit = miniButton("✎", "Edit user", false);
        Button btnDelete = miniButton("🗑", "Delete user", true);
        btnPin.setDisable(isSuper);                 // the Super Admin is never reset with a PIN
        btnDelete.setDisable(isSuper || isMe);      // the Super Admin account can't be deleted

        btnPin.setOnAction(e -> {
            new ResetPinDialog(window(), u.getUserId()).show();
            loadData();
        });
        btnEdit.setOnAction(e -> {
            if (new UserFormDialog(window(), u).show()) {
                loadData();
            }
        });
        btnDelete.setOnAction(e -> confirmDelete(u));

        HBox actions = new HBox(6, btnPin, btnEdit, btnDelete);
        actions.setAlignment(Pos.CENTER);
        card.getChildren().add(actions);
        return card;
    }

    private Button miniButton(String text, String tip, boolean danger) {
        Button b = new Button(text);
        b.getStyleClass().add("mini-button");
        if (danger) b.getStyleClass().add("mini-danger");
        b.setTooltip(new Tooltip(tip));
        return b;
    }

    private String roleKey(String role) {
        return switch (role) {
            case "Super Admin"  -> "super";
            case "Receptionist" -> "receptionist";
            case "Mechanic"     -> "mechanic";
            default             -> "other";
        };
    }

    private String initials(String fullName) {
        String[] parts = fullName.trim().split("\\s+");
        String first = parts[0].isEmpty() ? "?" : parts[0].substring(0, 1);
        String second = parts.length > 1 ? parts[1].substring(0, 1) : "";
        return (first + second).toUpperCase();
    }

    /* ======================= BUTTONS ======================= */
    @FXML
    private void onAddUser(ActionEvent e) {
        if (new UserFormDialog(window(), null).show()) {
            loadData();
        }
    }

    @FXML
    private void onResetPinAll(ActionEvent e) {
        new ResetPinDialog(window()).show();
        loadData();
    }

    private void confirmDelete(UserDTO u) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete " + u.getFullName() + " (@" + u.getUsername() + ")?",
                ButtonType.YES, ButtonType.NO);
        alert.initOwner(window());
        alert.setTitle("Delete User");
        alert.setHeaderText(null);
        styleDialog(alert.getDialogPane());

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            try {
                userModel.delete(u.getUserId());
                loadData();
            } catch (Exception ex) {
                showError("Cannot delete this user", ex.getMessage());
            }
        }
    }

    @FXML
    private void onLogout(ActionEvent e) {
        stop();
        Session.logout();
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/view/login.fxml"));
            Stage stage = (Stage) contentArea.getScene().getWindow();
            stage.setOnHidden(null);
            stage.setMinWidth(0);
            stage.setMinHeight(0);
            stage.setScene(new Scene(root));
            stage.setTitle("Bike Garage - Login");
            stage.setResizable(false);
            stage.sizeToScene();
            stage.centerOnScreen();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /** Call when the window closes so the timers stop. */
    public void stop() {
        if (clockTimeline != null) clockTimeline.stop();
        if (refreshTimeline != null) refreshTimeline.stop();
    }

    /* ======================= helpers ======================= */
    private Window window() {
        return contentArea.getScene().getWindow();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.initOwner(window());
        alert.setTitle(title);
        alert.setHeaderText(null);
        styleDialog(alert.getDialogPane());
        alert.showAndWait();
    }

    private void styleDialog(DialogPane pane) {
        URL css = getClass().getResource("/styles/admin.css");
        if (css != null) {
            pane.getStylesheets().add(css.toExternalForm());
        }
        pane.getStyleClass().add("dark-dialog");
    }

    @FXML
    void btnPinRequestsOnAction(ActionEvent event) {
        // Get the current window to act as the dialog's owner
        Window owner = ((Node) event.getSource()).getScene().getWindow();

        // Open the live PIN Requests inbox
        new PinRequestsDialog(owner).show();
    }
}
