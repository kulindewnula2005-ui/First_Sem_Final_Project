package lk.ijse.firstsemfinalproject25.controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import lk.ijse.firstsemfinalproject25.dto.BikeDTO;
import lk.ijse.firstsemfinalproject25.dto.CustomerDTO;
import lk.ijse.firstsemfinalproject25.model.BikeModel;
import lk.ijse.firstsemfinalproject25.model.CustomerModel;

import java.net.URL;

/** Small "add" dialogs used by the Service page (the red + buttons). */
public class QuickAddDialogsService  {

    private QuickAddDialogsService() { }

    /* ======================= ADD CUSTOMER ======================= */
    /** Returns the saved customer, or null if the person cancelled. */
    public static CustomerDTO addCustomer(Window owner) {
        CustomerModel model = new CustomerModel();

        String newId;
        try {
            newId = model.getNextId();
        } catch (Exception ex) {
            showError(owner, "Could not create a customer ID", ex.getMessage());
            return null;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Add Customer");
        DialogPane pane = dialog.getDialogPane();
        style(pane);
        pane.setPrefWidth(420);

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        TextField txtId = new TextField(newId);
        txtId.setDisable(true);
        TextField txtName = new TextField();
        TextField txtPhone = new TextField();
        TextField txtEmail = new TextField();
        TextField txtAddress = new TextField();
        txtName.setPromptText("e.g. Nimal Perera");
        txtPhone.setPromptText("e.g. 0771234567");
        txtEmail.setPromptText("optional");
        txtAddress.setPromptText("e.g. Colombo");

        Label title = new Label("Add Customer");
        title.getStyleClass().add("form-title");
        Label lblError = errorLabel();

        VBox content = new VBox(10, title,
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

        CustomerDTO[] saved = {null};
        btnSave.addEventFilter(ActionEvent.ACTION, ev -> {
            try {
                CustomerDTO dto = new CustomerDTO(
                        txtId.getText(), txtName.getText(),
                        blankToNull(txtEmail.getText()), txtPhone.getText(),
                        blankToNull(txtAddress.getText()));
                if (!model.save(dto)) {
                    throw new Exception("The customer could not be saved.");
                }
                saved[0] = dto;
            } catch (Exception ex) {
                showInline(lblError, ex.getMessage());
                ev.consume();   // keep the dialog open
            }
        });

        Platform.runLater(txtName::requestFocus);
        dialog.showAndWait();
        return saved[0];
    }

    /* ======================= ADD BIKE ======================= */
    /** Adds a bike for the given owner. Returns the saved bike, or null if cancelled. */
    public static BikeDTO addBike(Window owner, CustomerDTO customer) {
        BikeModel model = new BikeModel();

        String newId;
        try {
            newId = model.getNextId();
        } catch (Exception ex) {
            showError(owner, "Could not create a bike ID", ex.getMessage());
            return null;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Add Bike");
        DialogPane pane = dialog.getDialogPane();
        style(pane);
        pane.setPrefWidth(480);

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        TextField txtId = new TextField(newId);
        txtId.setDisable(true);
        TextField txtOwner = new TextField(customer.getCustomerId() + " - " + customer.getName());
        txtOwner.setDisable(true);
        TextField txtReg = new TextField();
        TextField txtBrand = new TextField();
        TextField txtModel = new TextField();
        TextField txtYear = new TextField();
        TextField txtEngine = new TextField();
        TextField txtChassis = new TextField();
        txtReg.setPromptText("e.g. WP AB 4567");
        txtBrand.setPromptText("e.g. Honda");
        txtModel.setPromptText("e.g. CBR 150R");
        txtYear.setPromptText("e.g. 2021");
        txtEngine.setPromptText("optional");
        txtChassis.setPromptText("optional");

        Label title = new Label("Add Bike");
        title.getStyleClass().add("form-title");
        Label lblError = errorLabel();

        VBox content = new VBox(10, title,
                row(field("Bike ID", txtId), field("Owner", txtOwner)),
                field("Number Plate *", txtReg),
                row(field("Brand *", txtBrand), field("Model *", txtModel)),
                row(field("Year *", txtYear), field("Engine No", txtEngine)),
                field("Chassis No", txtChassis),
                lblError);
        content.setPadding(new Insets(6, 4, 4, 4));
        pane.setContent(content);

        Button btnSave = (Button) pane.lookupButton(saveType);
        btnSave.getStyleClass().add("form-save");

        BikeDTO[] saved = {null};
        btnSave.addEventFilter(ActionEvent.ACTION, ev -> {
            try {
                int year;
                try {
                    year = Integer.parseInt(txtYear.getText().trim());
                } catch (NumberFormatException nfe) {
                    throw new Exception("Enter a valid year.");
                }
                BikeDTO dto = new BikeDTO(
                        txtId.getText(), customer.getCustomerId(),
                        txtReg.getText(), txtBrand.getText(), txtModel.getText(), year,
                        blankToNull(txtEngine.getText()), blankToNull(txtChassis.getText()));
                if (!model.save(dto)) {
                    throw new Exception("The bike could not be saved.");
                }
                // the model stores the plate in upper case; keep the same value here
                dto.setRegNumber(dto.getRegNumber().trim().toUpperCase());
                dto.setBrand(dto.getBrand().trim());
                dto.setModel(dto.getModel().trim());
                saved[0] = dto;
            } catch (Exception ex) {
                showInline(lblError, ex.getMessage());
                ev.consume();
            }
        });

        Platform.runLater(txtReg::requestFocus);
        dialog.showAndWait();
        return saved[0];
    }

    /* ======================= helpers ======================= */
    private static VBox field(String labelText, Node input) {
        Label label = new Label(labelText);
        label.getStyleClass().add("form-label");
        if (input instanceof TextField) {
            input.getStyleClass().add("form-field");
        }
        VBox box = new VBox(4, label, input);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private static HBox row(VBox left, VBox right) {
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        left.setPrefWidth(200);
        right.setPrefWidth(200);
        return new HBox(10, left, right);
    }

    private static Label errorLabel() {
        Label lbl = new Label();
        lbl.getStyleClass().add("form-error");
        lbl.setWrapText(true);
        lbl.setVisible(false);
        lbl.setManaged(false);
        return lbl;
    }

    private static void showInline(Label lbl, String message) {
        lbl.setText(message);
        lbl.setVisible(true);
        lbl.setManaged(true);
    }

    private static String blankToNull(String text) {
        return (text == null || text.trim().isEmpty()) ? null : text.trim();
    }

    private static void showError(Window owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(null);
        style(alert.getDialogPane());
        alert.showAndWait();
    }

    private static void style(DialogPane pane) {
        URL css = QuickAddDialogsService.class.getResource("/styles/bike.css");
        if (css != null) {
            pane.getStylesheets().add(css.toExternalForm());
        }
        pane.getStyleClass().add("dark-dialog");
    }
}