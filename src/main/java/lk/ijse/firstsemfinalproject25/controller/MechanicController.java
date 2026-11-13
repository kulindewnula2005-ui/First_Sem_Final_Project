package lk.ijse.firstsemfinalproject25.controller;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import lk.ijse.firstsemfinalproject25.dto.MechanicDTO;
import lk.ijse.firstsemfinalproject25.dto.UserDTO;
import lk.ijse.firstsemfinalproject25.dto.tm.MechanicTM;
import lk.ijse.firstsemfinalproject25.model.MechanicModel;
import lk.ijse.firstsemfinalproject25.model.UserModel;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;

public class MechanicController implements Initializable {

    private static final int PAGE_SIZE = 8;   // rows per page
    private static final String NO_ACCOUNT = "No login account";

    @FXML private VBox root;
    @FXML private Button btnAddMechanic;
    @FXML private Button btnSearch;
    @FXML private TextField txtSearch;
    @FXML private TableView<MechanicTM> tblMechanic;
    @FXML private TableColumn<MechanicTM, String> colId;
    @FXML private TableColumn<MechanicTM, String> colName;
    @FXML private TableColumn<MechanicTM, String> colSpec;
    @FXML private TableColumn<MechanicTM, String> colPhone;
    @FXML private TableColumn<MechanicTM, String> colUser;
    @FXML private TableColumn<MechanicTM, String> colJobs;
    @FXML private TableColumn<MechanicTM, Void> colActions;
    @FXML private HBox paginationBox;
    @FXML private Label lblCount;

    private final MechanicModel mechanicModel = new MechanicModel();
    private final UserModel userModel = new UserModel();

    private List<MechanicDTO> mechanics = new ArrayList<>();
    private List<UserDTO> users = new ArrayList<>();

    private List<MechanicTM> allRows = new ArrayList<>();
    private List<MechanicTM> filteredRows = new ArrayList<>();
    private int currentPage = 1;
    private boolean jumpToLastPage = false;

    private final PauseTransition searchDelay = new PauseTransition(Duration.millis(250));

    private record PageData(List<MechanicDTO> mechanics, List<UserDTO> users, Map<String, Integer> jobCounts) {}

    /* ======================================================= */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupTable();

        // live search (in memory): waits 250 ms after the last key press
        searchDelay.setOnFinished(e -> applyFilter(true));
        txtSearch.textProperty().addListener((obs, old, now) -> searchDelay.playFromStart());

        loadData();
    }

    /* ---------- table ---------- */
    private void setupTable() {
        colId.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getMechanicId()));
        colName.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getName()));
        colSpec.setCellValueFactory(c -> new ReadOnlyStringWrapper(dash(c.getValue().getSpecialization())));
        colPhone.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getPhone()));
        colUser.setCellValueFactory(c -> new ReadOnlyStringWrapper(dash(c.getValue().getUsername())));
        colJobs.setCellValueFactory(c -> new ReadOnlyStringWrapper(String.valueOf(c.getValue().getActiveJobs())));

        // edit + delete icons
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnEdit = new Button("✎");
            private final Button btnDelete = new Button("🗑");
            private final HBox box = new HBox(6, btnEdit, btnDelete);

            {
                btnEdit.getStyleClass().addAll("action-button", "edit-button");
                btnDelete.getStyleClass().addAll("action-button", "delete-button");
                box.setAlignment(Pos.CENTER_LEFT);

                btnEdit.setOnAction(e -> openForm(getTableView().getItems().get(getIndex())));
                btnDelete.setOnAction(e -> confirmDelete(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        // double-click a row = edit
        tblMechanic.setRowFactory(tv -> {
            TableRow<MechanicTM> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    openForm(row.getItem());
                }
            });
            return row;
        });

        tblMechanic.setPlaceholder(new Label("No mechanics found"));
    }

    /** null / blank -> "-" (for table cells). */
    private String dash(String text) {
        return (text == null || text.trim().isEmpty()) ? "-" : text;
    }

    /* ---------- load data (background thread) ---------- */
    private void loadData() {
        Task<PageData> task = new Task<>() {
            @Override
            protected PageData call() throws Exception {
                return new PageData(
                        mechanicModel.getAll(),
                        userModel.getAllUsers(),
                        mechanicModel.getActiveJobCounts());
            }
        };
        task.setOnSucceeded(e -> {
            PageData d = task.getValue();
            mechanics = new ArrayList<>(d.mechanics());
            users = new ArrayList<>(d.users());
            allRows = buildRows(d);

            if (jumpToLastPage) {
                currentPage = Integer.MAX_VALUE;   // showPage() clamps it to the last page
                jumpToLastPage = false;
            }
            applyFilter(false);
        });
        task.setOnFailed(e -> showError("Could not load mechanics", task.getException().getMessage()));

        Thread t = new Thread(task, "mechanic-load");
        t.setDaemon(true);
        t.start();
    }

    private List<MechanicTM> buildRows(PageData d) {
        Map<String, String> usernames = new HashMap<>();
        for (UserDTO u : d.users()) {
            usernames.put(u.getUserId(), u.getUsername());
        }
        List<MechanicTM> rows = new ArrayList<>();
        for (MechanicDTO m : d.mechanics()) {
            rows.add(new MechanicTM(
                    m.getMechanicId(),
                    m.getUserId(),
                    m.getName(),
                    m.getSpecialization(),
                    m.getPhone(),
                    m.getUserId() == null ? null : usernames.get(m.getUserId()),
                    String.valueOf(d.jobCounts().getOrDefault(m.getMechanicId(), 0)) // Converted to String
            ));
        }
        return rows;
    }

    /* ---------- search filter (in memory) ---------- */
    private void applyFilter(boolean resetPage) {
        String kw = txtSearch.getText() == null ? "" : txtSearch.getText().trim().toLowerCase();

        filteredRows = new ArrayList<>();
        for (MechanicTM r : allRows) {
            boolean match = kw.isEmpty()
                    || contains(r.getMechanicId(), kw)
                    || contains(r.getName(), kw)
                    || contains(r.getSpecialization(), kw)
                    || contains(r.getPhone(), kw)
                    || contains(r.getUsername(), kw);
            if (match) {
                filteredRows.add(r);
            }
        }
        if (resetPage) {
            currentPage = 1;
        }
        showPage();
    }

    private boolean contains(String text, String keywordLower) {
        return text != null && text.toLowerCase().contains(keywordLower);
    }

    /* ---------- paging ---------- */
    private int totalPages() {
        return Math.max(1, (int) Math.ceil(filteredRows.size() / (double) PAGE_SIZE));
    }

    private void showPage() {
        int pages = totalPages();
        if (currentPage > pages) currentPage = pages;
        if (currentPage < 1) currentPage = 1;

        int from = (currentPage - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, filteredRows.size());
        tblMechanic.setItems(FXCollections.observableArrayList(filteredRows.subList(from, to)));

        lblCount.setText(filteredRows.isEmpty()
                ? "No mechanics found"
                : "Showing " + (from + 1) + "–" + to + " of " + filteredRows.size() + " mechanics");

        buildPagination(pages);
    }

    private void buildPagination(int pages) {
        paginationBox.getChildren().clear();

        paginationBox.getChildren().add(
                pageButton("‹", currentPage > 1, () -> goToPage(currentPage - 1), false));

        int end = Math.min(pages, Math.max(1, currentPage - 2) + 4);
        int start = Math.max(1, end - 4);
        for (int p = start; p <= end; p++) {
            final int page = p;
            paginationBox.getChildren().add(
                    pageButton(String.valueOf(p), true, () -> goToPage(page), p == currentPage));
        }

        paginationBox.getChildren().add(
                pageButton("›", currentPage < pages, () -> goToPage(currentPage + 1), false));
    }

    private Button pageButton(String text, boolean enabled, Runnable action, boolean active) {
        Button b = new Button(text);
        b.getStyleClass().add("page-button");
        if (active) b.getStyleClass().add("page-button-active");
        b.setDisable(!enabled);
        b.setOnAction(e -> action.run());
        return b;
    }

    private void goToPage(int page) {
        currentPage = page;
        showPage();
    }

    /* ---------- button actions ---------- */
    @FXML
    private void onSearch(ActionEvent e) {
        searchDelay.stop();
        applyFilter(true);
    }

    @FXML
    private void onAddMechanic(ActionEvent e) {
        openForm(null);
    }

    /* ---------- add / edit form (dialog) ---------- */
    private void openForm(MechanicTM existing) {
        boolean editing = existing != null;

        String newId;
        try {
            newId = editing ? existing.getMechanicId() : mechanicModel.getNextId();
        } catch (Exception ex) {
            showError("Could not create a mechanic ID", ex.getMessage());
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(tblMechanic.getScene().getWindow());
        dialog.setTitle(editing ? "Edit Mechanic" : "Add Mechanic");

        DialogPane pane = dialog.getDialogPane();
        styleDialog(pane);
        pane.setPrefWidth(480);

        ButtonType saveType = new ButtonType(editing ? "Update" : "Save", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        // ----- basic details -----
        TextField txtId = new TextField(newId);
        txtId.setDisable(true);
        TextField txtName = new TextField(editing ? existing.getName() : "");
        TextField txtPhone = new TextField(editing ? existing.getPhone() : "");
        TextField txtSpec = new TextField(editing && existing.getSpecialization() != null ? existing.getSpecialization() : "");
        txtName.setPromptText("e.g. Kasun Perera");
        txtPhone.setPromptText("e.g. 0771234567");
        txtSpec.setPromptText("e.g. Engine, Electrical");

        // ----- login account: link an existing Mechanic account ... -----
        Set<String> linkedElsewhere = new HashSet<>();
        for (MechanicDTO m : mechanics) {
            boolean isThis = editing && m.getMechanicId().equals(existing.getMechanicId());
            if (m.getUserId() != null && !isThis) {
                linkedElsewhere.add(m.getUserId());
            }
        }
        ObservableList<String> accountItems = FXCollections.observableArrayList(NO_ACCOUNT);
        for (UserDTO u : users) {
            if ("Mechanic".equals(u.getRole()) && !linkedElsewhere.contains(u.getUserId())) {
                accountItems.add(u.getUserId() + " - " + u.getUsername());
            }
        }
        ComboBox<String> cmbAccount = new ComboBox<>(accountItems);
        cmbAccount.setMaxWidth(Double.MAX_VALUE);
        cmbAccount.getStyleClass().add("form-combo");
        cmbAccount.setValue(NO_ACCOUNT);
        if (editing && existing.getUserId() != null) {
            for (String item : accountItems) {
                if (item.startsWith(existing.getUserId() + " - ")) {
                    cmbAccount.setValue(item);
                }
            }
        }

        // ----- ... or create a new one right here -----
        CheckBox chkNew = new CheckBox("Create a new login account for this mechanic");
        chkNew.getStyleClass().add("form-check");
        TextField txtUsername = new TextField();
        PasswordField txtPassword = new PasswordField();
        txtUsername.setPromptText("username");
        txtPassword.setPromptText("password (min 4 characters)");
        HBox newAccountRow = row(field("Username *", txtUsername), field("Password *", txtPassword));
        newAccountRow.visibleProperty().bind(chkNew.selectedProperty());
        newAccountRow.managedProperty().bind(chkNew.selectedProperty());
        cmbAccount.disableProperty().bind(chkNew.selectedProperty());
        chkNew.selectedProperty().addListener((obs, old, now) -> Platform.runLater(() -> {
            if (pane.getScene() != null && pane.getScene().getWindow() != null) {
                pane.getScene().getWindow().sizeToScene();
            }
        }));

        Label accountHeading = new Label("LOGIN ACCOUNT (OPTIONAL)");
        accountHeading.getStyleClass().add("section-label");
        Label hint = new Label("A mechanic with a login account can sign in and update their own jobs.");
        hint.getStyleClass().add("form-hint");
        hint.setWrapText(true);

        Label title = new Label(editing ? "Edit Mechanic" : "Add Mechanic");
        title.getStyleClass().add("form-title");

        Label lblError = new Label();
        lblError.getStyleClass().add("form-error");
        lblError.setWrapText(true);
        lblError.setVisible(false);
        lblError.setManaged(false);

        VBox content = new VBox(10,
                title,
                row(field("Mechanic ID", txtId), field("Name *", txtName)),
                row(field("Phone *", txtPhone), field("Specialization", txtSpec)),
                accountHeading,
                field("Link an existing account", cmbAccount),
                chkNew,
                newAccountRow,
                hint,
                lblError);
        content.setPadding(new Insets(6, 4, 4, 4));
        pane.setContent(content);

        Button btnSave = (Button) pane.lookupButton(saveType);
        btnSave.getStyleClass().add("form-save");

        // validation / saving happens here; the dialog stays open if something is wrong
        boolean[] saved = {false};
        btnSave.addEventFilter(ActionEvent.ACTION, ev -> {
            String createdUserId = null;
            try {
                String userId;
                if (chkNew.isSelected()) {
                    UserDTO user = new UserDTO(userModel.getNextId(), txtUsername.getText(), null,
                            "Mechanic", txtName.getText());
                    if (!userModel.save(user, txtPassword.getText())) {
                        throw new Exception("The login account could not be created.");
                    }
                    createdUserId = user.getUserId();
                    userId = createdUserId;
                } else {
                    userId = accountIdOf(cmbAccount.getValue());
                }

                MechanicDTO dto = new MechanicDTO(
                        txtId.getText(), userId, txtName.getText(),
                        blankToNull(txtSpec.getText()), txtPhone.getText());

                boolean ok = editing ? mechanicModel.update(dto) : mechanicModel.save(dto);
                if (!ok) {
                    throw new Exception("The mechanic could not be saved.");
                }
                saved[0] = true;
            } catch (Exception ex) {
                // the mechanic was not saved: undo the login account created a moment ago
                if (createdUserId != null) {
                    try {
                        userModel.delete(createdUserId);
                    } catch (Exception ignore) {
                        // nothing more we can do
                    }
                }
                lblError.setText(ex.getMessage());
                lblError.setVisible(true);
                lblError.setManaged(true);
                ev.consume();   // keep the dialog open
            }
        });

        Platform.runLater(txtName::requestFocus);
        dialog.showAndWait();

        if (saved[0]) {
            if (!editing) {
                jumpToLastPage = true;   // show the new mechanic
            }
            loadData();
        }
    }

    /** "U002 - kasun" -> "U002", "No login account" -> null. */
    private String accountIdOf(String item) {
        if (item == null || NO_ACCOUNT.equals(item) || !item.contains(" - ")) {
            return null;
        }
        return item.substring(0, item.indexOf(" - "));
    }

    private VBox field(String labelText, Node input) {
        Label label = new Label(labelText);
        label.getStyleClass().add("form-label");
        if (input instanceof TextField) {
            input.getStyleClass().add("form-field");
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

    private String blankToNull(String text) {
        return (text == null || text.trim().isEmpty()) ? null : text.trim();
    }

    /* ---------- delete ---------- */
    private void confirmDelete(MechanicTM row) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete " + row.getName() + " (" + row.getMechanicId() + ")?",
                ButtonType.YES, ButtonType.NO);
        alert.initOwner(tblMechanic.getScene().getWindow());
        alert.setTitle("Delete Mechanic");
        alert.setHeaderText(null);
        styleDialog(alert.getDialogPane());

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            try {
                mechanicModel.delete(row.getMechanicId());
                loadData();
            } catch (Exception ex) {
                showError("Cannot delete this mechanic", ex.getMessage());
            }
        }
    }

    /* ---------- helpers ---------- */
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        if (tblMechanic.getScene() != null) {
            alert.initOwner(tblMechanic.getScene().getWindow());
        }
        alert.setTitle(title);
        alert.setHeaderText(null);
        styleDialog(alert.getDialogPane());
        alert.showAndWait();
    }

    private void styleDialog(DialogPane pane) {
        for (String file : new String[]{"/styles/bike.css", "/styles/mechanic.css"}) {
            URL css = getClass().getResource(file);
            if (css != null) {
                pane.getStylesheets().add(css.toExternalForm());
            }
        }
        pane.getStyleClass().add("dark-dialog");
    }
}