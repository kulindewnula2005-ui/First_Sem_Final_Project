package lk.ijse.firstsemfinalproject25.controller;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import lk.ijse.firstsemfinalproject25.dto.CustomerDTO;
import lk.ijse.firstsemfinalproject25.model.CustomerModel;
import lk.ijse.firstsemfinalproject25.dto.tm.CustomerTM;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class CustomerController implements Initializable {

    private static final int PAGE_SIZE = 8;   // rows per page (change if you like)

    @FXML private Button btnAddCustomer;
    @FXML private Button btnSearch;
    @FXML private TextField txtSearch;
    @FXML private TableView<CustomerTM> tblCustomer;
    @FXML private TableColumn<CustomerTM, String> colId;
    @FXML private TableColumn<CustomerTM, String> colName;
    @FXML private TableColumn<CustomerTM, String> colPhone;
    @FXML private TableColumn<CustomerTM, String> colAddress;
    @FXML private TableColumn<CustomerTM, Void> colActions;
    @FXML private HBox paginationBox;
    @FXML private Label lblCount;

    private final CustomerModel customerModel = new CustomerModel();

    private List<CustomerTM> allRows = new ArrayList<>();
    private int currentPage = 1;
    private boolean jumpToLastPage = false;

    private final PauseTransition searchDelay = new PauseTransition(Duration.millis(250));

    /* ======================================================= */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupTable();

        // live search: waits 250 ms after the last key press
        searchDelay.setOnFinished(e -> {
            currentPage = 1;
            loadData();
        });
        txtSearch.textProperty().addListener((obs, old, now) -> searchDelay.playFromStart());

        loadData();
    }

    /* ---------- table ---------- */
    private void setupTable() {
        colId.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getCustomerId()));
        colName.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getName()));
        colPhone.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getPhone()));
        colAddress.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue().getAddress() == null ? "" : c.getValue().getAddress()));

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

        tblCustomer.setPlaceholder(new Label("No customers found"));
    }

    /* ---------- load data (background thread) ---------- */
    private void loadData() {
        String keyword = txtSearch.getText() == null ? "" : txtSearch.getText().trim();

        Task<List<CustomerTM>> task = new Task<>() {
            @Override
            protected List<CustomerTM> call() throws Exception {
                List<CustomerDTO> dtos = keyword.isEmpty()
                        ? customerModel.getAll()
                        : customerModel.searchByKeyword(keyword);
                List<CustomerTM> rows = new ArrayList<>();
                for (CustomerDTO d : dtos) {
                    rows.add(new CustomerTM(d.getCustomerId(), d.getName(),
                            d.getEmail(), d.getPhone(), d.getAddress()));
                }
                return rows;
            }
        };
        task.setOnSucceeded(e -> {
            allRows = task.getValue();
            if (jumpToLastPage) {
                currentPage = totalPages();
                jumpToLastPage = false;
            }
            showPage();
        });
        task.setOnFailed(e -> showError("Could not load customers", task.getException().getMessage()));

        Thread t = new Thread(task, "customer-load");
        t.setDaemon(true);
        t.start();
    }

    /* ---------- paging ---------- */
    private int totalPages() {
        return Math.max(1, (int) Math.ceil(allRows.size() / (double) PAGE_SIZE));
    }

    private void showPage() {
        int pages = totalPages();
        if (currentPage > pages) currentPage = pages;
        if (currentPage < 1) currentPage = 1;

        int from = (currentPage - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, allRows.size());
        tblCustomer.setItems(FXCollections.observableArrayList(allRows.subList(from, to)));

        lblCount.setText(allRows.isEmpty()
                ? "No customers found"
                : "Showing " + (from + 1) + "–" + to + " of " + allRows.size() + " customers");

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
        currentPage = 1;
        loadData();
    }

    @FXML
    private void onAddCustomer(ActionEvent e) {
        openForm(null);
    }

    /* ---------- add / edit form (dialog) ---------- */
    private void openForm(CustomerTM existing) {
        boolean editing = existing != null;

        String newId;
        try {
            newId = editing ? existing.getCustomerId() : customerModel.getNextId();
        } catch (Exception ex) {
            showError("Could not create a customer ID", ex.getMessage());
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(tblCustomer.getScene().getWindow());
        dialog.setTitle(editing ? "Edit Customer" : "Add Customer");

        DialogPane pane = dialog.getDialogPane();
        styleDialog(pane);
        pane.setPrefWidth(420);

        ButtonType saveType = new ButtonType(editing ? "Update" : "Save", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        // ----- form fields -----
        TextField txtId = new TextField(newId);
        txtId.setDisable(true);
        TextField txtName = new TextField(editing ? existing.getName() : "");
        TextField txtPhone = new TextField(editing ? existing.getPhone() : "");
        TextField txtEmail = new TextField(editing && existing.getEmail() != null ? existing.getEmail() : "");
        TextField txtAddress = new TextField(editing && existing.getAddress() != null ? existing.getAddress() : "");

        txtName.setPromptText("e.g. Nimal Perera");
        txtPhone.setPromptText("e.g. 0771234567");
        txtEmail.setPromptText("optional");
        txtAddress.setPromptText("e.g. Colombo");

        Label title = new Label(editing ? "Edit Customer" : "Add Customer");
        title.getStyleClass().add("form-title");

        Label lblError = new Label();
        lblError.getStyleClass().add("form-error");
        lblError.setWrapText(true);
        lblError.setVisible(false);
        lblError.setManaged(false);

        VBox content = new VBox(10,
                title,
                field("Customer ID", txtId),
                field("Name *", txtName),
                field("Phone *", txtPhone),
                field("Email", txtEmail),
                field("Address", txtAddress),
                lblError);
        content.setPadding(new Insets(6, 4, 4, 4));
        pane.setContent(content);

        Button btnSave = (Button) pane.lookupButton(saveType);
        btnSave.getStyleClass().add("form-save");

        // validation / saving happens here; the dialog stays open if something is wrong
        boolean[] saved = {false};
        btnSave.addEventFilter(ActionEvent.ACTION, ev -> {
            try {
                CustomerDTO dto = new CustomerDTO(
                        txtId.getText(),
                        txtName.getText(),
                        blankToNull(txtEmail.getText()),
                        txtPhone.getText(),
                        blankToNull(txtAddress.getText()));

                boolean ok = editing ? customerModel.update(dto) : customerModel.save(dto);
                if (!ok) {
                    throw new Exception("The customer could not be saved.");
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
                jumpToLastPage = true;   // show the new customer
            }
            loadData();
        }
    }

    private VBox field(String labelText, TextField input) {
        Label label = new Label(labelText);
        label.getStyleClass().add("form-label");
        input.getStyleClass().add("form-field");
        return new VBox(4, label, input);
    }

    private String blankToNull(String text) {
        return (text == null || text.trim().isEmpty()) ? null : text.trim();
    }

    /* ---------- delete ---------- */
    private void confirmDelete(CustomerTM row) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete " + row.getName() + " (" + row.getCustomerId() + ")?",
                ButtonType.YES, ButtonType.NO);
        alert.initOwner(tblCustomer.getScene().getWindow());
        alert.setTitle("Delete Customer");
        alert.setHeaderText(null);
        styleDialog(alert.getDialogPane());

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            try {
                customerModel.delete(row.getCustomerId());
                loadData();
            } catch (Exception ex) {
                showError("Cannot delete this customer", ex.getMessage());
            }
        }
    }

    /* ---------- helpers ---------- */
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        if (tblCustomer.getScene() != null) {
            alert.initOwner(tblCustomer.getScene().getWindow());
        }
        alert.setTitle(title);
        alert.setHeaderText(null);
        styleDialog(alert.getDialogPane());
        alert.showAndWait();
    }

    private void styleDialog(DialogPane pane) {
        URL css = getClass().getResource("/styles/customer.css");
        if (css != null) {
            pane.getStylesheets().add(css.toExternalForm());
        }
        pane.getStyleClass().add("dark-dialog");
    }
}