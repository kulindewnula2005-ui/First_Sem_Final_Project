package lk.ijse.firstsemfinalproject25.controller;

import javafx.animation.PauseTransition;
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
import javafx.scene.control.ListCell;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import lk.ijse.firstsemfinalproject25.dto.BikeDTO;
import lk.ijse.firstsemfinalproject25.dto.CustomerDTO;
import lk.ijse.firstsemfinalproject25.dto.MechanicDTO;
import lk.ijse.firstsemfinalproject25.dto.ServiceRequestDTO;
import lk.ijse.firstsemfinalproject25.dto.tm.ServiceRequestTM;
import lk.ijse.firstsemfinalproject25.model.BikeModel;
import lk.ijse.firstsemfinalproject25.model.CustomerModel;
import lk.ijse.firstsemfinalproject25.model.MechanicModel;
import lk.ijse.firstsemfinalproject25.model.ServiceRequestModel;
import lk.ijse.firstsemfinalproject25.util.SearchPopup;

import javafx.scene.control.Tooltip;
import lk.ijse.firstsemfinalproject25.util.ServicePartsDialog;

import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;

public class ServiceController implements Initializable {

    private static final int PAGE_SIZE = 8;   // rows per page
    private static final String NOT_ASSIGNED = "Not assigned";
    private static final String ALL_STATUS = "All status";

    /* ---------- new service request form ---------- */
    @FXML private VBox root;
    @FXML private TextField txtCustomer;
    @FXML private TextField txtBike;
    @FXML private Button btnAddCustomer;
    @FXML private Button btnAddBike;
    @FXML private TextArea txtComplaint;
    @FXML private ComboBox<String> cmbMechanic;
    @FXML private ComboBox<String> cmbType;
    @FXML private Label lblMessage;
    @FXML private Button btnReset;
    @FXML private Button btnSave;

    /* ---------- jobs table ---------- */
    @FXML private ComboBox<String> cmbFilter;
    @FXML private TextField txtSearch;
    @FXML private Button btnSearch;
    @FXML private TableView<ServiceRequestTM> tblService;
    @FXML private TableColumn<ServiceRequestTM, String> colId;
    @FXML private TableColumn<ServiceRequestTM, String> colPlate;
    @FXML private TableColumn<ServiceRequestTM, String> colCustomer;
    @FXML private TableColumn<ServiceRequestTM, String> colType;
    @FXML private TableColumn<ServiceRequestTM, String> colMechanic;
    @FXML private TableColumn<ServiceRequestTM, String> colStatus;
    @FXML private TableColumn<ServiceRequestTM, String> colDate;
    @FXML private TableColumn<ServiceRequestTM, Void> colActions;
    @FXML private HBox paginationBox;
    @FXML private Label lblCount;

    private final ServiceRequestModel serviceModel = new ServiceRequestModel();
    private final CustomerModel customerModel = new CustomerModel();
    private final BikeModel bikeModel = new BikeModel();
    private final MechanicModel mechanicModel = new MechanicModel();

    private SearchPopup<CustomerDTO> customerPicker;
    private SearchPopup<BikeDTO> bikePicker;

    private List<CustomerDTO> customers = new ArrayList<>();
    private List<BikeDTO> bikes = new ArrayList<>();
    private List<MechanicDTO> mechanics = new ArrayList<>();

    private List<ServiceRequestTM> allRows = new ArrayList<ServiceRequestTM>();
    private List<ServiceRequestTM> filteredRows = new ArrayList<ServiceRequestTM>();
    private int currentPage = 1;

    private final PauseTransition searchDelay = new PauseTransition(Duration.millis(250));

    private record PageData(List<CustomerDTO> customers, List<BikeDTO> bikes,
                            List<MechanicDTO> mechanics, List<ServiceRequestDTO> services) {}

    /* ======================================================= */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupPickers();
        setupCombos();
        setupTable();

        // live search (in memory): waits 250 ms after the last key press
        searchDelay.setOnFinished(e -> applyFilter(true));
        txtSearch.textProperty().addListener((obs, old, now) -> searchDelay.playFromStart());
        cmbFilter.valueProperty().addListener((obs, old, now) -> applyFilter(true));

        loadAll();
    }

    /* ======================= FORM ======================= */
    private void setupPickers() {
        customerPicker = new SearchPopup<>(txtCustomer,
                c -> c.getCustomerId() + " - " + c.getName(),
                c -> c.getCustomerId() + " " + c.getName() + " " + c.getPhone());

        bikePicker = new SearchPopup<>(txtBike,
                b -> b.getRegNumber() + " - " + b.getBrand() + " " + b.getModel(),
                b -> b.getBikeId() + " " + b.getRegNumber() + " " + BikeModel.normalizeReg(b.getRegNumber())
                        + " " + b.getBrand() + " " + b.getModel());

        // customer changed: only that customer's bikes are offered
        customerPicker.setOnChange(c -> {
            BikeDTO b = bikePicker.getSelected();
            if (b != null && (c == null || !b.getCustomerId().equals(c.getCustomerId()))) {
                bikePicker.clear();
            }
            bikePicker.setSource(bikesForSelectedCustomer());
        });

        // bike picked: its owner is filled in automatically
        bikePicker.setOnChange(b -> {
            if (b == null) {
                return;
            }
            for (CustomerDTO c : customers) {
                if (c.getCustomerId().equals(b.getCustomerId())) {
                    customerPicker.select(c, false);
                    break;
                }
            }
            bikePicker.setSource(bikesForSelectedCustomer());
        });
    }

    private List<BikeDTO> bikesForSelectedCustomer() {
        CustomerDTO c = customerPicker.getSelected();
        if (c == null) {
            return bikes;
        }
        List<BikeDTO> result = new ArrayList<>();
        for (BikeDTO b : bikes) {
            if (b.getCustomerId().equals(c.getCustomerId())) {
                result.add(b);
            }
        }
        return result;
    }

    private void setupCombos() {
        cmbType.setItems(FXCollections.observableArrayList(ServiceRequestModel.SERVICE_TYPES));
        showPrompt(cmbType);
        showPrompt(cmbMechanic);

        cmbFilter.setItems(FXCollections.observableArrayList(
                ALL_STATUS, ServiceRequestModel.PENDING,
                ServiceRequestModel.IN_PROGRESS, ServiceRequestModel.COMPLETED));
        cmbFilter.setValue(ALL_STATUS);
    }

    /** Makes a combo show its prompt text again after the value is cleared. */
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

    private List<String> mechanicItems() {
        List<String> items = new ArrayList<>();
        items.add(NOT_ASSIGNED);
        for (MechanicDTO m : mechanics) {
            items.add(m.getMechanicId() + " - " + m.getName());
        }
        return items;
    }

    private void refreshMechanics() {
        String old = cmbMechanic.getValue();
        List<String> items = mechanicItems();
        cmbMechanic.setItems(FXCollections.observableArrayList(items));
        if (old != null && items.contains(old)) {
            cmbMechanic.setValue(old);
        }
    }

    /** "M001 - Kasun" -> "M001", "Not assigned" -> null. */
    private String mechanicIdOf(String item) {
        if (item == null || NOT_ASSIGNED.equals(item) || !item.contains(" - ")) {
            return null;
        }
        return item.substring(0, item.indexOf(" - "));
    }

    /* ---------- red + buttons ---------- */
    @FXML
    private void onAddCustomer(ActionEvent e) {
        CustomerDTO saved = QuickAddDialogsService.addCustomer(root.getScene().getWindow());
        if (saved != null) {
            customers.add(saved);
            customerPicker.setSource(customers);
            customerPicker.select(saved, true);
            hideMessage();
        }
    }

    @FXML
    private void onAddBike(ActionEvent e) {
        CustomerDTO owner = customerPicker.getSelected();
        if (owner == null) {
            showMessage("Select the customer first, then add the bike.", true);
            return;
        }
        BikeDTO saved = QuickAddDialogsService.addBike(root.getScene().getWindow(), owner);
        if (saved != null) {
            bikes.add(saved);
            bikePicker.setSource(bikesForSelectedCustomer());
            bikePicker.select(saved, true);
            hideMessage();
        }
    }

    /* ---------- reset / save ---------- */
    @FXML
    private void onReset(ActionEvent e) {
        resetForm();
    }

    private void resetForm() {
        customerPicker.clear();
        bikePicker.clear();
        bikePicker.setSource(bikesForSelectedCustomer());
        txtComplaint.clear();
        cmbMechanic.setValue(null);
        cmbType.setValue(null);
        hideMessage();
    }

    @FXML
    private void onSave(ActionEvent e) {
        try {
            CustomerDTO customer = customerPicker.getSelected();
            if (customer == null) {
                throw new Exception("Select a customer from the list.");
            }
            BikeDTO bike = bikePicker.getSelected();
            if (bike == null) {
                throw new Exception("Select a bike from the list.");
            }
            if (!bike.getCustomerId().equals(customer.getCustomerId())) {
                throw new Exception("This bike does not belong to the selected customer.");
            }

            ServiceRequestDTO dto = new ServiceRequestDTO(
                    serviceModel.getNextId(),
                    bike.getBikeId(),
                    mechanicIdOf(cmbMechanic.getValue()),
                    txtComplaint.getText(),
                    cmbType.getValue(),
                    ServiceRequestModel.PENDING,
                    LocalDate.now(),
                    null);

            if (!serviceModel.save(dto)) {
                throw new Exception("The service request could not be saved.");
            }

            resetForm();
            showMessage("Service request " + dto.getServiceId() + " created.", false);
            currentPage = 1;
            loadAll();
        } catch (Exception ex) {
            showMessage(ex.getMessage(), true);
        }
    }

    private void showMessage(String text, boolean error) {
        lblMessage.getStyleClass().removeAll("form-error", "success-text");
        lblMessage.getStyleClass().add(error ? "form-error" : "success-text");
        lblMessage.setText(text);
        lblMessage.setVisible(true);
        lblMessage.setManaged(true);
    }

    private void hideMessage() {
        lblMessage.setVisible(false);
        lblMessage.setManaged(false);
    }

    /* ======================= TABLE ======================= */
    private void setupTable() {
        colId.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getServiceId()));
        colPlate.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getPlate()));
        colCustomer.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getCustomerName()));
        colType.setCellValueFactory(c -> new ReadOnlyStringWrapper(dash(c.getValue().getServiceType())));
        colMechanic.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getMechanicName()));
        colStatus.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getStatus()));
        colDate.setCellValueFactory(c -> new ReadOnlyStringWrapper(String.valueOf(c.getValue().getDateReceived())));
        // coloured status badge
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Label badge = new Label(value);
                badge.getStyleClass().add("badge");
                switch (value) {
                    case "In Progress" -> badge.getStyleClass().add("badge-progress");
                    case "Pending"     -> badge.getStyleClass().add("badge-pending");
                    case "Completed"   -> badge.getStyleClass().add("badge-completed");
                    default -> { }
                }
                setGraphic(badge);
                setText(null);
            }
        });

        // update + delete icons
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnParts = new Button("🔩");
            private final Button btnEdit = new Button("✎");
            private final Button btnDelete = new Button("🗑");
            private final HBox box = new HBox(4, btnParts, btnEdit, btnDelete);

            {
                btnParts.getStyleClass().addAll("action-button", "edit-button");
                btnParts.setTooltip(new Tooltip("Spare parts"));
                btnEdit.getStyleClass().addAll("action-button", "edit-button");
                btnDelete.getStyleClass().addAll("action-button", "delete-button");
                box.setAlignment(Pos.CENTER_LEFT);

                btnParts.setOnAction(e -> new ServicePartsDialog(
                        tblService.getScene().getWindow(),
                        getTableView().getItems().get(getIndex())).show());
                btnEdit.setOnAction(e -> openUpdate(getTableView().getItems().get(getIndex())));
                btnDelete.setOnAction(e -> confirmDelete(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        // double-click a row = update
        tblService.setRowFactory(tv -> {
            TableRow<ServiceRequestTM> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    openUpdate(row.getItem());
                }
            });
            return row;
        });

        tblService.setPlaceholder(new Label("No service jobs found"));
    }

    private String dash(String text) {
        return (text == null || text.trim().isEmpty()) ? "-" : text;
    }

    /* ---------- load everything (background thread) ---------- */
    private void loadAll() {
        Task<PageData> task = new Task<>() {
            @Override
            protected PageData call() throws Exception {
                return new PageData(
                        customerModel.getAll(),
                        bikeModel.getAll(),
                        mechanicModel.getAll(),
                        serviceModel.getAll());
            }
        };
        task.setOnSucceeded(e -> {
            PageData d = task.getValue();
            customers = new ArrayList<>(d.customers());
            bikes = new ArrayList<>(d.bikes());
            mechanics = new ArrayList<>(d.mechanics());

            customerPicker.setSource(customers);
            bikePicker.setSource(bikesForSelectedCustomer());
            refreshMechanics();

            allRows = buildRows(d);
            applyFilter(false);
        });
        task.setOnFailed(e -> showError("Could not load service jobs", task.getException().getMessage()));

        Thread t = new Thread(task, "service-load");
        t.setDaemon(true);
        t.start();
    }

    private List<ServiceRequestTM> buildRows(PageData d) {
        Map<String, CustomerDTO> customerMap = new HashMap<>();
        for (CustomerDTO c : d.customers()) {
            customerMap.put(c.getCustomerId(), c);
        }
        Map<String, BikeDTO> bikeMap = new HashMap<>();
        for (BikeDTO b : d.bikes()) {
            bikeMap.put(b.getBikeId(), b);
        }
        Map<String, String> mechanicNames = new HashMap<>();
        for (MechanicDTO m : d.mechanics()) {
            mechanicNames.put(m.getMechanicId(), m.getName());
        }

        List<ServiceRequestTM> rows = new ArrayList<>();
        for (ServiceRequestDTO s : d.services()) {
            BikeDTO b = bikeMap.get(s.getBikeId());
            CustomerDTO c = b == null ? null : customerMap.get(b.getCustomerId());
            rows.add(new ServiceRequestTM(
                    s.getServiceId(),
                    s.getBikeId(),
                    b == null ? "-" : b.getRegNumber(),
                    b == null ? "-" : b.getBrand() + " " + b.getModel(),
                    c == null ? "-" : c.getName(),
                    s.getComplaint(),
                    s.getServiceType(),
                    s.getMechanicId(),
                    s.getMechanicId() == null ? "-" : mechanicNames.getOrDefault(s.getMechanicId(), "-"),
                    s.getStatus(),
                    String.valueOf(s.getDateReceived())));
        }
        return rows;
    }

    /* ---------- search + status filter (in memory) ---------- */
    private void applyFilter(boolean resetPage) {
        String kw = txtSearch.getText() == null ? "" : txtSearch.getText().trim().toLowerCase();
        String kwReg = BikeModel.normalizeReg(kw);
        String status = cmbFilter.getValue();

        filteredRows = new ArrayList<ServiceRequestTM>();
        for (ServiceRequestTM r : allRows) {
            boolean statusOk = status == null || ALL_STATUS.equals(status) || status.equals(r.getStatus());
            boolean textOk = kw.isEmpty()
                    || contains(r.getServiceId(), kw)
                    || contains(r.getBikeName(), kw)
                    || contains(r.getCustomerName(), kw)
                    || contains(r.getServiceType(), kw)
                    || contains(r.getMechanicName(), kw)
                    || contains(r.getComplaint(), kw)
                    || (!kwReg.isEmpty() && BikeModel.normalizeReg(r.getPlate()).contains(kwReg));
            if (statusOk && textOk) {
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

    @FXML
    private void onSearch(ActionEvent e) {
        searchDelay.stop();
        applyFilter(true);
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
        tblService.setItems(FXCollections.observableArrayList(filteredRows.subList(from, to)));

        lblCount.setText(filteredRows.isEmpty()
                ? "No service jobs found"
                : "Showing " + (from + 1) + "–" + to + " of " + filteredRows.size() + " jobs");

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

    /* ======================= UPDATE DIALOG (mechanic + status) ======================= */
    private void openUpdate(ServiceRequestTM row) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(tblService.getScene().getWindow());
        dialog.setTitle("Update Service");

        DialogPane pane = dialog.getDialogPane();
        styleDialog(pane);
        pane.setPrefWidth(460);

        ButtonType saveType = new ButtonType("Update", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        Label title = new Label("Update Service " + row.getServiceId());
        title.getStyleClass().add("form-title");

        // read-only summary of the job
        VBox summary = new VBox(6,
                infoRow("Bike", row.getPlate() + " - " + row.getBikeName()),
                infoRow("Customer", row.getCustomerName()),
                infoRow("Service", dash(row.getServiceType())),
                infoRow("Complaint", dash(row.getComplaint())));

        // mechanic
        ComboBox<String> cmbMech = new ComboBox<>(FXCollections.observableArrayList(mechanicItems()));
        cmbMech.setMaxWidth(Double.MAX_VALUE);
        cmbMech.getStyleClass().add("form-combo");
        String current = NOT_ASSIGNED;
        if (row.getMechanicId() != null) {
            for (String item : cmbMech.getItems()) {
                if (item.startsWith(row.getMechanicId() + " - ")) {
                    current = item;
                }
            }
        }
        cmbMech.setValue(current);

        // status
        ComboBox<String> cmbStat = new ComboBox<>(FXCollections.observableArrayList(ServiceRequestModel.STATUSES));
        cmbStat.setMaxWidth(Double.MAX_VALUE);
        cmbStat.getStyleClass().add("form-combo");
        cmbStat.setValue(row.getStatus());

        Label lblError = new Label();
        lblError.getStyleClass().add("form-error");
        lblError.setWrapText(true);
        lblError.setVisible(false);
        lblError.setManaged(false);

        VBox content = new VBox(10, title, summary,
                field("Assigned Mechanic", cmbMech),
                field("Status", cmbStat),
                lblError);
        content.setPadding(new Insets(6, 4, 4, 4));
        pane.setContent(content);

        Button btnSaveDialog = (Button) pane.lookupButton(saveType);
        btnSaveDialog.getStyleClass().add("form-save");

        boolean[] saved = {false};
        btnSaveDialog.addEventFilter(ActionEvent.ACTION, ev -> {
            try {
                String newMechanic = mechanicIdOf(cmbMech.getValue());
                String newStatus = cmbStat.getValue();

                boolean mechanicChanged = !Objects.equals(newMechanic, row.getMechanicId());
                boolean statusChanged = !newStatus.equals(row.getStatus());

                if (mechanicChanged) {
                    if (newMechanic == null) {
                        throw new Exception("Select a mechanic. A job cannot go back to 'Not assigned'.");
                    }
                    serviceModel.assignMechanic(row.getServiceId(), newMechanic);
                }
                if (statusChanged) {
                    serviceModel.updateStatus(row.getServiceId(), newStatus);
                }
                saved[0] = mechanicChanged || statusChanged;
            } catch (Exception ex) {
                lblError.setText(ex.getMessage());
                lblError.setVisible(true);
                lblError.setManaged(true);
                ev.consume();   // keep the dialog open
            }
        });

        dialog.showAndWait();

        if (saved[0]) {
            loadAll();
        }
    }

    private HBox infoRow(String name, String value) {
        Label key = new Label(name);
        key.getStyleClass().add("info-label");
        key.setMinWidth(80);
        Label val = new Label(value);
        val.getStyleClass().add("info-value");
        val.setWrapText(true);
        val.setMaxWidth(320);
        return new HBox(10, key, val);
    }

    private VBox field(String labelText, Node input) {
        Label label = new Label(labelText);
        label.getStyleClass().add("form-label");
        VBox box = new VBox(4, label, input);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    /* ======================= DELETE ======================= */
    private void confirmDelete(ServiceRequestTM row) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete service request " + row.getServiceId() + " (" + row.getPlate() + ")?",
                ButtonType.YES, ButtonType.NO);
        alert.initOwner(tblService.getScene().getWindow());
        alert.setTitle("Delete Service Request");
        alert.setHeaderText(null);
        styleDialog(alert.getDialogPane());

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            try {
                serviceModel.delete(row.getServiceId());
                loadAll();
            } catch (Exception ex) {
                showError("Cannot delete this job", ex.getMessage());
            }
        }
    }

    /* ======================= helpers ======================= */
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        if (tblService.getScene() != null) {
            alert.initOwner(tblService.getScene().getWindow());
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