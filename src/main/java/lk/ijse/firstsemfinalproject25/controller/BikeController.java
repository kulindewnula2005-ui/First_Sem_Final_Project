package lk.ijse.firstsemfinalproject25.controller;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import lk.ijse.firstsemfinalproject25.dto.BikeDTO;
import lk.ijse.firstsemfinalproject25.dto.CustomerDTO;
import lk.ijse.firstsemfinalproject25.dto.tm.BikeTM;
import lk.ijse.firstsemfinalproject25.model.BikeModel;
import lk.ijse.firstsemfinalproject25.model.CustomerModel;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

public class BikeController implements Initializable {

    private static final int PAGE_SIZE = 8;   // rows per page

    @FXML private VBox root;
    @FXML private Button btnAddBike;
    @FXML private Button btnSearch;
    @FXML private TextField txtSearch;
    @FXML private TableView<BikeTM> tblBike;
    @FXML private TableColumn<BikeTM, String> colId;
    @FXML private TableColumn<BikeTM, String> colPlate;
    @FXML private TableColumn<BikeTM, String> colBrand;
    @FXML private TableColumn<BikeTM, String> colModel;
    @FXML private TableColumn<BikeTM, String> colYear;
    @FXML private TableColumn<BikeTM, String> colEngine;
    @FXML private TableColumn<BikeTM, String> colChassis;
    @FXML private TableColumn<BikeTM, String> colOwner;
    @FXML private TableColumn<BikeTM, Void> colActions;
    @FXML private HBox paginationBox;
    @FXML private Label lblCount;

    private final BikeModel bikeModel = new BikeModel();
    private final CustomerModel customerModel = new CustomerModel();

    private List<BikeTM> allRows = new ArrayList<>();
    private List<BikeTM> filteredRows = new ArrayList<>();
    private int currentPage = 1;
    private boolean jumpToLastPage = false;

    private final PauseTransition searchDelay = new PauseTransition(Duration.millis(250));

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
        colId.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getBikeId()));
        colPlate.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getRegNumber()));
        colBrand.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getBrand()));
        colModel.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getModel()));
        colYear.setCellValueFactory(c -> new ReadOnlyStringWrapper(String.valueOf(c.getValue().getYear())));
        colEngine.setCellValueFactory(c -> new ReadOnlyStringWrapper(dash(c.getValue().getEngineNo())));
        colChassis.setCellValueFactory(c -> new ReadOnlyStringWrapper(dash(c.getValue().getChassisNo())));
        colOwner.setCellValueFactory(c -> new ReadOnlyStringWrapper(dash(c.getValue().getCustomerId())));

        // view + edit + delete icons
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnView = new Button("👁");
            private final Button btnEdit = new Button("✎");
            private final Button btnDelete = new Button("🗑");
            private final HBox box = new HBox(4, btnView, btnEdit, btnDelete);

            {
                btnView.getStyleClass().addAll("action-button", "view-button");
                btnEdit.getStyleClass().addAll("action-button", "edit-button");
                btnDelete.getStyleClass().addAll("action-button", "delete-button");
                box.setAlignment(Pos.CENTER_LEFT);

                btnView.setOnAction(e -> openDetails(getTableView().getItems().get(getIndex())));
                btnEdit.setOnAction(e -> openForm(getTableView().getItems().get(getIndex())));
                btnDelete.setOnAction(e -> confirmDelete(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        // double-click a row = open details
        tblBike.setRowFactory(tv -> {
            TableRow<BikeTM> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    openDetails(row.getItem());
                }
            });
            return row;
        });

        tblBike.setPlaceholder(new Label("No bikes found"));
    }

    /** null / blank -> "-" (for table cells). */
    private String dash(String text) {
        return (text == null || text.trim().isEmpty()) ? "-" : text;
    }

    /* ---------- load data (background thread) ---------- */
    private void loadData() {
        Task<List<BikeTM>> task = new Task<>() {
            @Override
            protected List<BikeTM> call() throws Exception {
                Map<String, String> owners = new HashMap<>();
                for (CustomerDTO c : customerModel.getAll()) {
                    owners.put(c.getCustomerId(), c.getName());
                }
                List<BikeTM> rows = new ArrayList<>();
                for (BikeDTO m : bikeModel.getAll()) {
                    rows.add(new BikeTM(
                            m.getBikeId(), m.getCustomerId(),
                            owners.getOrDefault(m.getCustomerId(), "-"),
                            m.getRegNumber(), m.getBrand(), m.getModel(), m.getYear(),
                            m.getEngineNo(), m.getChassisNo()));
                }
                return rows;
            }
        };
        task.setOnSucceeded(e -> {
            allRows = task.getValue();
            if (jumpToLastPage) {
                currentPage = Integer.MAX_VALUE;   // showPage() clamps it to the last page
                jumpToLastPage = false;
            }
            applyFilter(false);
        });
        task.setOnFailed(e -> showError("Could not load bikes", task.getException().getMessage()));

        Thread t = new Thread(task, "bike-load");
        t.setDaemon(true);
        t.start();
    }

    /* ---------- search filter (in memory) ---------- */
    private void applyFilter(boolean resetPage) {
        String kw = txtSearch.getText() == null ? "" : txtSearch.getText().trim().toLowerCase();
        String kwReg = BikeModel.normalizeReg(kw);

        filteredRows = new ArrayList<>();
        for (BikeTM r : allRows) {
            boolean match = kw.isEmpty()
                    || contains(r.getBikeId(), kw)
                    || contains(r.getBrand(), kw)
                    || contains(r.getModel(), kw)
                    || contains(r.getCustomerId(), kw)
                    || contains(r.getEngineNo(), kw)
                    || contains(r.getChassisNo(), kw)
                    || String.valueOf(r.getYear()).contains(kw)
                    || (!kwReg.isEmpty() && BikeModel.normalizeReg(r.getRegNumber()).contains(kwReg));
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
        tblBike.setItems(FXCollections.observableArrayList(filteredRows.subList(from, to)));

        lblCount.setText(filteredRows.isEmpty()
                ? "No bikes found"
                : "Showing " + (from + 1) + "–" + to + " of " + filteredRows.size() + " bikes");

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
    private void onAddBike(ActionEvent e) {
        openForm(null);
    }

    /* ---------- bike details (replaces this page inside the dashboard) ---------- */
    private void openDetails(BikeTM row) {
        try {
            BikeDTO bike = bikeModel.search(row.getBikeId());
            if (bike == null) {
                throw new Exception("This bike no longer exists.");
            }
            if (!(root.getParent() instanceof Pane host)) {
                throw new Exception("The page is not inside the dashboard.");
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/bikeDetails.fxml"));
            Parent detailsRoot = loader.load();
            BikeDetailsController controller = loader.getController();

            // "back" puts this list page back and refreshes it
            controller.setBike(bike, () -> {
                host.getChildren().setAll(root);
                loadData();
            });
            host.getChildren().setAll(detailsRoot);
        } catch (Exception ex) {
            showError("Could not open bike details", ex.getMessage());
        }
    }

    /* ---------- add / edit form (dialog) ---------- */
    private void openForm(BikeTM existing) {
        boolean editing = existing != null;

        List<CustomerDTO> customers;
        String newId;
        try {
            customers = customerModel.getAll();
            newId = editing ? existing.getBikeId() : bikeModel.getNextId();
        } catch (Exception ex) {
            showError("Could not open the form", ex.getMessage());
            return;
        }
        if (customers.isEmpty()) {
            showError("No customers yet", "Add a customer first, then register the bike.");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(tblBike.getScene().getWindow());
        dialog.setTitle(editing ? "Edit Bike" : "Add Bike");

        DialogPane pane = dialog.getDialogPane();
        styleDialog(pane);
        pane.setPrefWidth(480);

        ButtonType saveType = new ButtonType(editing ? "Update" : "Save", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        // ----- owner drop-down: "C001 - Nimal Perera" -----
        ObservableList<String> ownerItems = FXCollections.observableArrayList();
        for (CustomerDTO c : customers) {
            ownerItems.add(c.getCustomerId() + " - " + c.getName());
        }
        ComboBox<String> cmbOwner = new ComboBox<>(ownerItems);
        cmbOwner.setPromptText("Select owner");
        cmbOwner.setMaxWidth(Double.MAX_VALUE);
        cmbOwner.getStyleClass().add("form-combo");
        if (editing) {
            for (String item : ownerItems) {
                if (item.startsWith(existing.getCustomerId() + " - ")) {
                    cmbOwner.getSelectionModel().select(item);
                }
            }
        }

        // ----- text fields -----
        TextField txtId = new TextField(newId);
        txtId.setDisable(true);
        TextField txtReg = new TextField(editing ? existing.getRegNumber() : "");
        TextField txtBrand = new TextField(editing ? existing.getBrand() : "");
        TextField txtModel = new TextField(editing ? existing.getModel() : "");
        TextField txtYear = new TextField(editing ? String.valueOf(existing.getYear()) : "");
        TextField txtEngine = new TextField(editing && existing.getEngineNo() != null ? existing.getEngineNo() : "");
        TextField txtChassis = new TextField(editing && existing.getChassisNo() != null ? existing.getChassisNo() : "");

        txtReg.setPromptText("e.g. WP AB 4567");
        txtBrand.setPromptText("e.g. Honda");
        txtModel.setPromptText("e.g. CBR 150R");
        txtYear.setPromptText("e.g. 2021");
        txtEngine.setPromptText("optional");
        txtChassis.setPromptText("optional");

        Label title = new Label(editing ? "Edit Bike" : "Add Bike");
        title.getStyleClass().add("form-title");

        Label lblError = new Label();
        lblError.getStyleClass().add("form-error");
        lblError.setWrapText(true);
        lblError.setVisible(false);
        lblError.setManaged(false);

        VBox content = new VBox(10,
                title,
                row(field("Bike ID", txtId), field("Owner *", cmbOwner)),
                field("Number Plate *", txtReg),
                row(field("Brand *", txtBrand), field("Model *", txtModel)),
                row(field("Year *", txtYear), field("Engine No", txtEngine)),
                field("Chassis No", txtChassis),
                lblError);
        content.setPadding(new Insets(6, 4, 4, 4));
        pane.setContent(content);

        Button btnSave = (Button) pane.lookupButton(saveType);
        btnSave.getStyleClass().add("form-save");

        // validation / saving happens here; the dialog stays open if something is wrong
        boolean[] saved = {false};
        btnSave.addEventFilter(ActionEvent.ACTION, ev -> {
            try {
                String owner = cmbOwner.getValue();
                if (owner == null) {
                    throw new Exception("Select the owner (customer).");
                }
                String customerId = owner.substring(0, owner.indexOf(" - "));

                int year;
                try {
                    year = Integer.parseInt(txtYear.getText().trim());
                } catch (NumberFormatException nfe) {
                    throw new Exception("Enter a valid year.");
                }

                BikeDTO dto = new BikeDTO(
                        txtId.getText(), customerId,
                        txtReg.getText(), txtBrand.getText(), txtModel.getText(), year,
                        blankToNull(txtEngine.getText()), blankToNull(txtChassis.getText()));

                boolean ok = editing ? bikeModel.update(dto) : bikeModel.save(dto);
                if (!ok) {
                    throw new Exception("The bike could not be saved.");
                }
                saved[0] = true;
            } catch (Exception ex) {
                lblError.setText(ex.getMessage());
                lblError.setVisible(true);
                lblError.setManaged(true);
                ev.consume();   // keep the dialog open
            }
        });

        Platform.runLater(txtReg::requestFocus);
        dialog.showAndWait();

        if (saved[0]) {
            if (!editing) {
                jumpToLastPage = true;   // show the new bike
            }
            loadData();
        }
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
    private void confirmDelete(BikeTM row) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete " + row.getBrand() + " " + row.getModel() + " (" + row.getRegNumber() + ")?",
                ButtonType.YES, ButtonType.NO);
        alert.initOwner(tblBike.getScene().getWindow());
        alert.setTitle("Delete Bike");
        alert.setHeaderText(null);
        styleDialog(alert.getDialogPane());

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            try {
                bikeModel.delete(row.getBikeId());
                loadData();
            } catch (Exception ex) {
                showError("Cannot delete this bike", ex.getMessage());
            }
        }
    }

    /* ---------- helpers ---------- */
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        if (tblBike.getScene() != null) {
            alert.initOwner(tblBike.getScene().getWindow());
        }
        alert.setTitle(title);
        alert.setHeaderText(null);
        styleDialog(alert.getDialogPane());
        alert.showAndWait();
    }

    private void styleDialog(DialogPane pane) {
        URL css = getClass().getResource("/styles/bike.css");
        if (css != null) {
            pane.getStylesheets().add(css.toExternalForm());
        }
        pane.getStyleClass().add("dark-dialog");
    }
}