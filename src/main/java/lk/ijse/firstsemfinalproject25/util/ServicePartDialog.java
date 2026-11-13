package lk.ijse.firstsemfinalproject25.util;

import javafx.beans.property.ReadOnlyStringWrapper;
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
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import lk.ijse.firstsemfinalproject25.dto.ServicePartsDTO;
import lk.ijse.firstsemfinalproject25.dto.SparePartsDTO;
import lk.ijse.firstsemfinalproject25.dto.tm.ServicePartsTM;
import lk.ijse.firstsemfinalproject25.dto.tm.ServiceRequestTM;
import lk.ijse.firstsemfinalproject25.model.ServicePartsModel;
import lk.ijse.firstsemfinalproject25.model.SparePartsModel;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * "Spare Parts" window for one service job.
 *
 *   Add part        -> spare-part stock goes DOWN  (shown live while you type the quantity)
 *   Change quantity -> stock goes UP or DOWN by the difference
 *   Remove part     -> stock goes back UP
 *
 * All stock work is done by ServicePartsModel (one transaction per action).
 * A completed or billed job is read-only.
 */
public class ServicePartDialog {

    private final Window owner;
    private final ServiceRequestTM job;
    private boolean locked = false;

    private final ServicePartsModel servicePartsModel = new ServicePartsModel();
    private final SparePartsModel sparePartsModel = new SparePartsModel();

    private List<SparePartsDTO> parts = new ArrayList<>();

    private final ComboBox<String> cmbPart = new ComboBox<>();
    private final TextField txtQty = new TextField();
    private final Button btnAdd = new Button("Add Part");
    private final Label lblStock = new Label();
    private final Label lblMessage = new Label();
    private final Label lblTotal = new Label();
    private final TableView<ServicePartsTM> table = new TableView<>();


    public ServicePartDialog(Window owner, ServiceRequestTM job) {
        this.owner = owner;
        this.job = job;
        this.locked = locked;
    }

    /* ======================= build + show ======================= */
    public void show() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Spare Parts");

        DialogPane pane = dialog.getDialogPane();
        styleDialog(pane);
        pane.setPrefWidth(640);
        pane.getButtonTypes().add(ButtonType.CLOSE);

        Label title = new Label("Spare Parts - " + job.getServiceId());
        title.getStyleClass().add("form-title");

        Label sub = new Label(job.getPlate() + " - " + job.getBikeName() + "   |   " + job.getStatus());
        sub.getStyleClass().add("info-value");

        VBox content = new VBox(10, title, sub);
        content.setPadding(new Insets(6, 4, 4, 4));

        if (locked) {
            Label note = new Label("This job is completed - spare parts can no longer be changed.");
            note.getStyleClass().add("form-error");
            note.setWrapText(true);
            content.getChildren().add(note);
        } else {
            content.getChildren().addAll(buildAddRow(), lblStock);
        }

        lblMessage.setWrapText(true);
        lblMessage.setVisible(false);
        lblMessage.setManaged(false);

        buildTable();
        lblTotal.getStyleClass().add("form-label");

        content.getChildren().addAll(lblMessage, table, lblTotal);
        pane.setContent(content);

        reload();
        dialog.showAndWait();
    }

    private HBox buildAddRow() {
        cmbPart.setPromptText("Select spare part...");
        cmbPart.setMaxWidth(Double.MAX_VALUE);
        cmbPart.getStyleClass().add("form-combo");
        showPrompt(cmbPart);
        HBox.setHgrow(cmbPart, Priority.ALWAYS);

        txtQty.setPromptText("Qty");
        txtQty.setPrefWidth(80);
        txtQty.getStyleClass().add("form-field");
        // digits only, up to 4 characters
        txtQty.setTextFormatter(new TextFormatter<String>(
                change -> change.getControlNewText().matches("\\d{0,4}") ? change : null));

        btnAdd.getStyleClass().add("form-save");
        btnAdd.setDisable(true);

        lblStock.getStyleClass().add("info-value");

        // LIVE: stock info updates as soon as the part or the quantity changes
        cmbPart.valueProperty().addListener((o, a, b) -> updateStockInfo());
        txtQty.textProperty().addListener((o, a, b) -> updateStockInfo());

        btnAdd.setOnAction(e -> addPart());
        txtQty.setOnAction(e -> addPart());

        HBox row = new HBox(8, cmbPart, txtQty, btnAdd);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void buildTable() {
        TableColumn<ServicePartsTM, String> cPart = new TableColumn<>("Part");
        cPart.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue().getPartId() + " - " + nameOf(c.getValue().getPartId())));

        TableColumn<ServicePartsTM, String> cQty = new TableColumn<>("Qty");
        cQty.setCellValueFactory(c -> new ReadOnlyStringWrapper(String.valueOf(c.getValue().getQuantity())));

        TableColumn<ServicePartsTM, String> cPrice = new TableColumn<>("Unit Price");
        cPrice.setCellValueFactory(c -> new ReadOnlyStringWrapper(money(c.getValue().getUnitPrice())));

        TableColumn<ServicePartsTM, String> cTotal = new TableColumn<>("Total");
        cTotal.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                money(c.getValue().getQuantity() * c.getValue().getUnitPrice())));

        TableColumn<ServicePartsTM, Void> cAct = new TableColumn<>("Actions");
        cAct.setSortable(false);
        cAct.setCellFactory(col -> new TableCell<>() {
            private final Button btnEdit = new Button("✎");
            private final Button btnRemove = new Button("🗑");
            private final HBox box = new HBox(4, btnEdit, btnRemove);

            {
                btnEdit.getStyleClass().addAll("action-button", "edit-button");
                btnRemove.getStyleClass().addAll("action-button", "delete-button");
                box.setAlignment(Pos.CENTER_LEFT);
                btnEdit.setDisable(locked);
                btnRemove.setDisable(locked);

                btnEdit.setOnAction(e -> changeQuantity(getTableView().getItems().get(getIndex())));
                btnRemove.setOnAction(e -> removePart(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        table.getColumns().addAll(cPart, cQty, cPrice, cTotal, cAct);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getStyleClass().add("data-table");
        table.setPrefHeight(210);
        table.setPlaceholder(new Label("No spare parts used on this job yet"));
    }

    /* ======================= actions ======================= */
    private void addPart() {
        SparePartsDTO part = selectedPart();
        int qty = parseQty();
        if (part == null || qty <= 0 || btnAdd.isDisable()) {
            return;
        }
        try {
            servicePartsModel.save(new ServicePartsDTO(
                    job.getServiceId(), part.getPartId(), qty, 0));   // price is taken from the spare part
            txtQty.clear();
            showMessage("Added " + qty + " x " + part.getPartName() + ". Stock reduced.", false);
        } catch (Exception ex) {
            showMessage(ex.getMessage(), true);
        }
        reload();
    }

    private void changeQuantity(ServicePartsTM row) {
        TextInputDialog input = new TextInputDialog(String.valueOf(row.getQuantity()));
        input.initOwner(table.getScene().getWindow());
        input.setTitle("Change Quantity");
        input.setHeaderText(null);
        input.setContentText("New quantity for " + nameOf(row.getPartId()) + ":");
        styleDialog(input.getDialogPane());

        input.showAndWait().ifPresent(text -> {
            try {
                int qty = Integer.parseInt(text.trim());
                servicePartsModel.update(row.getServiceId(), row.getPartId(), qty);
                showMessage("Quantity updated. Stock adjusted.", false);
            } catch (NumberFormatException ex) {
                showMessage("Enter a whole number.", true);
            } catch (Exception ex) {
                showMessage(ex.getMessage(), true);
            }
            reload();
        });
    }

    private void removePart(ServicePartsTM row) {
        try {
            servicePartsModel.delete(row.getServiceId(), row.getPartId());
            showMessage(nameOf(row.getPartId()) + " removed. Stock returned.", false);
        } catch (Exception ex) {
            showMessage(ex.getMessage(), true);
        }
        reload();
    }

    /* ======================= refresh from the database ======================= */
    private void reload() {
        try {
            String selectedId = selectedPartId();

            parts = new ArrayList<>(sparePartsModel.getAll());
            List<String> items = new ArrayList<>();
            for (SparePartsDTO p : parts) {
                items.add(label(p));
            }
            cmbPart.setItems(FXCollections.observableArrayList(items));
            if (selectedId != null) {
                for (String item : items) {
                    if (item.startsWith(selectedId + " - ")) {
                        cmbPart.setValue(item);
                    }
                }
            }

            List<ServicePartsTM> rows = new ArrayList<>();
            for (ServicePartsDTO d : servicePartsModel.getByService(job.getServiceId())) {
                rows.add(new ServicePartsTM(d.getServiceId(), d.getPartId(), d.getQuantity(), d.getUnitPrice()));
            }
            table.setItems(FXCollections.observableArrayList(rows));

            lblTotal.setText("Parts total:  Rs. " + money(servicePartsModel.getPartsTotal(job.getServiceId())));
        } catch (Exception ex) {
            showMessage("Could not load spare parts: " + ex.getMessage(), true);
        }
        updateStockInfo();
    }

    /** LIVE stock line: "Available: 12  ->  after use: 9". Also enables / disables the Add button. */
    private void updateStockInfo() {
        if (locked) {
            return;
        }
        SparePartsDTO part = selectedPart();
        int qty = parseQty();

        if (part == null) {
            lblStock.setText("Select a spare part to see its stock.");
            lblStock.setStyle("");
            btnAdd.setDisable(true);
            return;
        }

        int available = part.getStockQty();
        int after = available - qty;

        if (qty <= 0) {
            lblStock.setText("Available: " + available + "   |   Unit price: Rs. " + money(part.getUnitPrice()));
            lblStock.setStyle("");
        } else if (after < 0) {
            lblStock.setText("Not enough stock - only " + available + " available.");
            lblStock.setStyle("-fx-text-fill: #ff6b6b;");
        } else {
            lblStock.setText("Available: " + available + "   ->   after use: " + after);
            lblStock.setStyle("-fx-text-fill: #4ade80;");
        }
        btnAdd.setDisable(qty <= 0 || after < 0);
    }

    /* ======================= helpers ======================= */
    private String label(SparePartsDTO p) {
        return p.getPartId() + " - " + p.getPartName() + "  (Stock: " + p.getStockQty() + ")";
    }

    private String selectedPartId() {
        String item = cmbPart.getValue();
        if (item == null || !item.contains(" - ")) {
            return null;
        }
        return item.substring(0, item.indexOf(" - "));
    }

    private SparePartsDTO selectedPart() {
        String id = selectedPartId();
        if (id == null) {
            return null;
        }
        for (SparePartsDTO p : parts) {
            if (id.equals(p.getPartId())) {
                return p;
            }
        }
        return null;
    }

    private String nameOf(String partId) {
        for (SparePartsDTO p : parts) {
            if (partId.equals(p.getPartId())) {
                return p.getPartName();
            }
        }
        return partId;
    }

    private int parseQty() {
        try {
            return Integer.parseInt(txtQty.getText().trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private String money(Object value) {
        return String.format("%,.2f", Double.parseDouble(String.valueOf(value)));
    }

    private void showMessage(String text, boolean error) {
        lblMessage.getStyleClass().removeAll("form-error", "success-text");
        lblMessage.getStyleClass().add(error ? "form-error" : "success-text");
        lblMessage.setText(text);
        lblMessage.setVisible(true);
        lblMessage.setManaged(true);
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

    private void styleDialog(DialogPane pane) {
        URL css = getClass().getResource("/styles/bike.css");
        if (css != null) {
            pane.getStylesheets().add(css.toExternalForm());
        }
        pane.getStyleClass().add("dark-dialog");
    }
}