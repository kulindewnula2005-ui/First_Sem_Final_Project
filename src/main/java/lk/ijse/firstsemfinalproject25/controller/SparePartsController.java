package lk.ijse.firstsemfinalproject25.controller;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
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
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import lk.ijse.firstsemfinalproject25.dto.SparePartsDTO;
import lk.ijse.firstsemfinalproject25.dto.tm.SparePartsTM;
import lk.ijse.firstsemfinalproject25.model.SparePartsModel;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class SparePartsController implements Initializable {

    private static final int PAGE_SIZE = 8;   // rows per page
    private static final String ALL_PARTS = "All parts";
    private static final String LOW_STOCK = "Low stock";

    @FXML private VBox root;
    @FXML private Button btnAddPart;
    @FXML private Button btnSearch;
    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cmbFilter;
    @FXML private Label lblLowStock;
    @FXML private TableView<SparePartsTM> tblPart;
    @FXML private TableColumn<SparePartsTM, String> colId;
    @FXML private TableColumn<SparePartsTM, String> colName;
    @FXML private TableColumn<SparePartsTM, SparePartsTM> colStock;
    @FXML private TableColumn<SparePartsTM, String> colReorder;
    @FXML private TableColumn<SparePartsTM, String> colPrice;
    @FXML private TableColumn<SparePartsTM, Void> colActions;
    @FXML private HBox paginationBox;
    @FXML private Label lblCount;

    private final SparePartsModel partModel = new SparePartsModel();

    private List<SparePartsTM> allRows = new ArrayList<>();
    private List<SparePartsTM> filteredRows = new ArrayList<>();
    private int currentPage = 1;
    private boolean jumpToLastPage = false;

    private final PauseTransition searchDelay = new PauseTransition(Duration.millis(250));

    /* ======================================================= */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        cmbFilter.setItems(FXCollections.observableArrayList(ALL_PARTS, LOW_STOCK));
        cmbFilter.setValue(ALL_PARTS);

        setupTable();

        // live search (in memory): waits 250 ms after the last key press
        searchDelay.setOnFinished(e -> applyFilter(true));
        txtSearch.textProperty().addListener((obs, old, now) -> searchDelay.playFromStart());
        cmbFilter.valueProperty().addListener((obs, old, now) -> applyFilter(true));

        loadData();
    }

    /* ---------- table ---------- */
    private void setupTable() {
        colId.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getPartId()));
        colName.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getPartName()));
        colStock.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        colReorder.setCellValueFactory(c -> new ReadOnlyStringWrapper(String.valueOf(c.getValue().getReorderLevel())));
        colPrice.setCellValueFactory(c -> new ReadOnlyStringWrapper(money(c.getValue().getUnitPrice())));

        // stock badge: green = ok, amber = low, red = out of stock
        colStock.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(SparePartsTM item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Label badge = new Label();
                badge.getStyleClass().add("badge");
                if (item.getStockQty() <= 0) {
                    badge.setText("0 · Out");
                    badge.getStyleClass().add("stock-out");
                } else if (item.getStockQty() <= item.getReorderLevel()) {
                    badge.setText(item.getStockQty() + " · Low");
                    badge.getStyleClass().add("stock-low");
                } else {
                    badge.setText(String.valueOf(item.getStockQty()));
                    badge.getStyleClass().add("stock-ok");
                }
                setGraphic(badge);
                setText(null);
            }
        });

        // add stock + edit + delete icons
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnStock = new Button("＋");
            private final Button btnEdit = new Button("✎");
            private final Button btnDelete = new Button("🗑");
            private final HBox box = new HBox(4, btnStock, btnEdit, btnDelete);

            {
                btnStock.getStyleClass().addAll("action-button", "stock-button");
                btnEdit.getStyleClass().addAll("action-button", "edit-button");
                btnDelete.getStyleClass().addAll("action-button", "delete-button");
                box.setAlignment(Pos.CENTER_LEFT);

                btnStock.setOnAction(e -> openAddStock(getTableView().getItems().get(getIndex())));
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
        tblPart.setRowFactory(tv -> {
            TableRow<SparePartsTM> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    openForm(row.getItem());
                }
            });
            return row;
        });

        tblPart.setPlaceholder(new Label("No spare parts found"));
    }

    private String money(double amount) {
        return String.format("%,.2f", amount);
    }

    private boolean isLow(SparePartsTM p) {
        return p.getStockQty() <= p.getReorderLevel();
    }

    /* ---------- load data (background thread) ---------- */
    private void loadData() {
        Task<List<SparePartsTM>> task = new Task<>() {
            @Override
            protected List<SparePartsTM> call() throws Exception {
                List<SparePartsTM> rows = new ArrayList<>();
                for (SparePartsDTO d : partModel.getAll()) {
                    rows.add(new SparePartsTM(d.getPartId(), d.getPartName(),
                            d.getUnitPrice(), d.getStockQty(), d.getReorderLevel()));
                }
                return rows;
            }
        };
        task.setOnSucceeded(e -> {
            allRows = task.getValue();
            updateLowStockWarning();
            if (jumpToLastPage) {
                currentPage = Integer.MAX_VALUE;   // showPage() clamps it to the last page
                jumpToLastPage = false;
            }
            applyFilter(false);
        });
        task.setOnFailed(e -> showError("Could not load spare parts", task.getException().getMessage()));

        Thread t = new Thread(task, "spare-parts-load");
        t.setDaemon(true);
        t.start();
    }

    /** "⚠ 3 parts low on stock" next to the page title. */
    private void updateLowStockWarning() {
        int low = 0;
        for (SparePartsTM p : allRows) {
            if (isLow(p)) {
                low++;
            }
        }
        boolean show = low > 0;
        lblLowStock.setText("⚠  " + low + (low == 1 ? " part is" : " parts are") + " low on stock");
        lblLowStock.setVisible(show);
        lblLowStock.setManaged(show);
    }

    /* ---------- search + stock filter (in memory) ---------- */
    private void applyFilter(boolean resetPage) {
        String kw = txtSearch.getText() == null ? "" : txtSearch.getText().trim().toLowerCase();
        boolean lowOnly = LOW_STOCK.equals(cmbFilter.getValue());

        filteredRows = new ArrayList<>();
        for (SparePartsTM r : allRows) {
            boolean textOk = kw.isEmpty()
                    || contains(r.getPartId(), kw)
                    || contains(r.getPartName(), kw);
            boolean stockOk = !lowOnly || isLow(r);
            if (textOk && stockOk) {
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
        tblPart.setItems(FXCollections.observableArrayList(filteredRows.subList(from, to)));

        lblCount.setText(filteredRows.isEmpty()
                ? "No spare parts found"
                : "Showing " + (from + 1) + "–" + to + " of " + filteredRows.size() + " parts");

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
    private void onAddPart(ActionEvent e) {
        openForm(null);
    }

    /* ---------- add / edit form (dialog) ---------- */
    private void openForm(SparePartsTM existing) {
        boolean editing = existing != null;

        String newId;
        try {
            newId = editing ? existing.getPartId() : partModel.getNextId();
        } catch (Exception ex) {
            showError("Could not create a part ID", ex.getMessage());
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(tblPart.getScene().getWindow());
        dialog.setTitle(editing ? "Edit Spare Part" : "Add Spare Part");

        DialogPane pane = dialog.getDialogPane();
        styleDialog(pane);
        pane.setPrefWidth(460);

        ButtonType saveType = new ButtonType(editing ? "Update" : "Save", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        // ----- form fields -----
        TextField txtId = new TextField(newId);
        txtId.setDisable(true);
        TextField txtName = new TextField(editing ? existing.getPartName() : "");
        TextField txtPrice = new TextField(editing ? String.valueOf(existing.getUnitPrice()) : "");
        TextField txtStock = new TextField(editing ? String.valueOf(existing.getStockQty()) : "0");
        TextField txtReorder = new TextField(editing ? String.valueOf(existing.getReorderLevel()) : "5");

        txtName.setPromptText("e.g. Engine Oil 10W-40");
        txtPrice.setPromptText("e.g. 2500.00");
        txtStock.setPromptText("e.g. 12");
        txtReorder.setPromptText("e.g. 5");

        Label hint = new Label("The stock turns amber (Low) when it falls to the reorder level or below.");
        hint.getStyleClass().add("form-hint");
        hint.setWrapText(true);

        Label title = new Label(editing ? "Edit Spare Part" : "Add Spare Part");
        title.getStyleClass().add("form-title");

        Label lblError = new Label();
        lblError.getStyleClass().add("form-error");
        lblError.setWrapText(true);
        lblError.setVisible(false);
        lblError.setManaged(false);

        VBox content = new VBox(10,
                title,
                row(field("Part ID", txtId), field("Part Name *", txtName)),
                row(field("Unit Price (Rs.) *", txtPrice), field("Stock Qty *", txtStock)),
                field("Reorder Level *", txtReorder),
                hint,
                lblError);
        content.setPadding(new Insets(6, 4, 4, 4));
        pane.setContent(content);

        Button btnSave = (Button) pane.lookupButton(saveType);
        btnSave.getStyleClass().add("form-save");

        // validation / saving happens here; the dialog stays open if something is wrong
        boolean[] saved = {false};
        btnSave.addEventFilter(ActionEvent.ACTION, ev -> {
            try {
                double price;
                try {
                    price = Double.parseDouble(txtPrice.getText().trim().replace(",", ""));
                } catch (NumberFormatException nfe) {
                    throw new Exception("Enter a valid unit price.");
                }
                int stock = parseWhole(txtStock.getText(), "Enter a valid stock quantity.");
                int reorder = parseWhole(txtReorder.getText(), "Enter a valid reorder level.");

                SparePartsDTO dto = new SparePartsDTO(
                        txtId.getText(), txtName.getText(),
                        Math.round(price * 100.0) / 100.0, stock, reorder);

                boolean ok = editing ? partModel.update(dto) : partModel.save(dto);
                if (!ok) {
                    throw new Exception("The spare part could not be saved.");
                }
                saved[0] = true;
            } catch (Exception ex) {
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
                jumpToLastPage = true;   // show the new part
            }
            loadData();
        }
    }

    private int parseWhole(String text, String errorMessage) throws Exception {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException nfe) {
            throw new Exception(errorMessage);
        }
    }

    /* ---------- add stock (the green + icon) ---------- */
    private void openAddStock(SparePartsTM row) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(tblPart.getScene().getWindow());
        dialog.setTitle("Add Stock");

        DialogPane pane = dialog.getDialogPane();
        styleDialog(pane);
        pane.setPrefWidth(400);

        ButtonType saveType = new ButtonType("Add Stock", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        Label title = new Label("Add Stock");
        title.getStyleClass().add("form-title");

        VBox summary = new VBox(6,
                infoRow("Part", row.getPartId() + " - " + row.getPartName()),
                infoRow("In stock", String.valueOf(row.getStockQty())),
                infoRow("Reorder at", String.valueOf(row.getReorderLevel())));

        TextField txtQty = new TextField();
        txtQty.setPromptText("Quantity received, e.g. 10");

        Label lblError = new Label();
        lblError.getStyleClass().add("form-error");
        lblError.setWrapText(true);
        lblError.setVisible(false);
        lblError.setManaged(false);

        VBox content = new VBox(10, title, summary, field("Quantity to add *", txtQty), lblError);
        content.setPadding(new Insets(6, 4, 4, 4));
        pane.setContent(content);

        Button btnSave = (Button) pane.lookupButton(saveType);
        btnSave.getStyleClass().add("form-save");

        boolean[] saved = {false};
        btnSave.addEventFilter(ActionEvent.ACTION, ev -> {
            try {
                int qty = parseWhole(txtQty.getText(), "Enter a valid quantity.");
                if (qty <= 0) {
                    throw new Exception("Quantity must be greater than zero.");
                }
                if (qty > 100000) {
                    throw new Exception("Quantity is too large.");
                }
                if (!partModel.addStock(row.getPartId(), qty)) {
                    throw new Exception("The stock could not be updated.");
                }
                saved[0] = true;
            } catch (Exception ex) {
                lblError.setText(ex.getMessage());
                lblError.setVisible(true);
                lblError.setManaged(true);
                ev.consume();
            }
        });

        Platform.runLater(txtQty::requestFocus);
        dialog.showAndWait();

        if (saved[0]) {
            loadData();   // stays on the same page
        }
    }

    private HBox infoRow(String name, String value) {
        Label key = new Label(name);
        key.getStyleClass().add("info-label");
        key.setMinWidth(80);
        Label val = new Label(value);
        val.getStyleClass().add("info-value");
        val.setWrapText(true);
        val.setMaxWidth(300);
        return new HBox(10, key, val);
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

    /* ---------- delete ---------- */
    private void confirmDelete(SparePartsTM row) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete " + row.getPartName() + " (" + row.getPartId() + ")?",
                ButtonType.YES, ButtonType.NO);
        alert.initOwner(tblPart.getScene().getWindow());
        alert.setTitle("Delete Spare Part");
        alert.setHeaderText(null);
        styleDialog(alert.getDialogPane());

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            try {
                partModel.delete(row.getPartId());
                loadData();
            } catch (Exception ex) {
                showError("Cannot delete this part", ex.getMessage());
            }
        }
    }

    /* ---------- helpers ---------- */
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        if (tblPart.getScene() != null) {
            alert.initOwner(tblPart.getScene().getWindow());
        }
        alert.setTitle(title);
        alert.setHeaderText(null);
        styleDialog(alert.getDialogPane());
        alert.showAndWait();
    }

    private void styleDialog(DialogPane pane) {
        for (String file : new String[]{"/styles/bike.css", "/styles/spareparts.css"}) {
            URL css = getClass().getResource(file);
            if (css != null) {
                pane.getStylesheets().add(css.toExternalForm());
            }
        }
        pane.getStyleClass().add("dark-dialog");
    }
}