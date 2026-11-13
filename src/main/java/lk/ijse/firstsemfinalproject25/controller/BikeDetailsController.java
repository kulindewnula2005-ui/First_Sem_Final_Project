package lk.ijse.firstsemfinalproject25.controller;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lk.ijse.firstsemfinalproject25.dto.BikeDTO;
import lk.ijse.firstsemfinalproject25.dto.CustomerDTO;
import lk.ijse.firstsemfinalproject25.dto.ServiceRequestDTO;
import lk.ijse.firstsemfinalproject25.model.CustomerModel;
import lk.ijse.firstsemfinalproject25.model.ServiceRequestModel;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class BikeDetailsController {

    /* ---------- header buttons ---------- */
    @FXML private Button btnBack;
    @FXML private Button btnHistory;

    /* ---------- photo ---------- */
    @FXML private StackPane photoBox;
    @FXML private Label lblNoPhoto;

    /* ---------- bike information ---------- */
    @FXML private Label lblPlate;
    @FXML private Label lblBrand;
    @FXML private Label lblModel;
    @FXML private Label lblYear;
    @FXML private Label lblEngine;
    @FXML private Label lblChassis;

    /* ---------- customer details ---------- */
    @FXML private Label lblCustName;
    @FXML private Label lblCustPhone;
    @FXML private Label lblCustAddress;

    /* ---------- service history (last 5) ---------- */
    @FXML private Label lblHistoryTitle;
    @FXML private TableView<ServiceRequestDTO> tblHistory;
    @FXML private TableColumn<ServiceRequestDTO, String> colDate;
    @FXML private TableColumn<ServiceRequestDTO, String> colService;
    @FXML private TableColumn<ServiceRequestDTO, String> colStatus;

    private final CustomerModel customerModel = new CustomerModel();
    private final ServiceRequestModel serviceModel = new ServiceRequestModel();

    private BikeDTO bike;
    private Runnable onBack;
    private List<ServiceRequestDTO> fullHistory = new ArrayList<>();

    private record DetailsData(CustomerDTO customer, List<ServiceRequestDTO> history) {}

    /* ======================================================= */
    /** Called by FXMLLoader after the FXML is loaded. */
    @FXML
    private void initialize() {
        setupHistoryColumns(colDate, colService, colStatus);
        tblHistory.setPlaceholder(new Label("No service history yet"));
    }

    /** Called by BikeController right after loading this page. */
    public void setBike(BikeDTO bike, Runnable onBack) {
        this.bike = bike;
        this.onBack = onBack;

        lblPlate.setText(show(bike.getRegNumber()));
        lblBrand.setText(show(bike.getBrand()));
        lblModel.setText(show(bike.getModel()));
        lblYear.setText(String.valueOf(bike.getYear()));
        lblEngine.setText(show(bike.getEngineNo()));
        lblChassis.setText(show(bike.getChassisNo()));

        setPhoto();
        loadDetails();
    }

    /* ---------- photo ---------- */
    /**
     * Looks for /images/bikes/<brand>.jpg (for example honda.jpg) first,
     * then /images/bike.jpg. If neither exists a 🏍 icon is shown.
     */
    private void setPhoto() {
        String brand = bike.getBrand() == null ? "" : bike.getBrand().toLowerCase().replaceAll("[^a-z0-9]", "");
        URL url = getClass().getResource("/images/bikes/" + brand + ".jpg");
        if (url == null) {
            url = getClass().getResource("/images/bike.jpg");
        }
        if (url != null) {
            photoBox.setStyle("-fx-background-image: url('" + url.toExternalForm() + "');"
                    + "-fx-background-size: cover;"
                    + "-fx-background-position: center center;"
                    + "-fx-background-repeat: no-repeat;");
            lblNoPhoto.setVisible(false);
        } else {
            lblNoPhoto.setVisible(true);
        }
    }

    /* ---------- customer + history (background thread) ---------- */
    private void loadDetails() {
        Task<DetailsData> task = new Task<>() {
            @Override
            protected DetailsData call() throws Exception {
                CustomerDTO customer = customerModel.search(bike.getCustomerId());
                List<ServiceRequestDTO> history = serviceModel.getHistoryByBike(bike.getBikeId());
                return new DetailsData(customer, history);
            }
        };
        task.setOnSucceeded(e -> {
            DetailsData data = task.getValue();

            CustomerDTO c = data.customer();
            lblCustName.setText(c == null ? "-" : show(c.getName()));
            lblCustPhone.setText(c == null ? "-" : show(c.getPhone()));
            lblCustAddress.setText(c == null ? "-" : show(c.getAddress()));

            fullHistory = data.history();
            List<ServiceRequestDTO> last5 = fullHistory.subList(0, Math.min(5, fullHistory.size()));
            tblHistory.setItems(FXCollections.observableArrayList(last5));
        });
        task.setOnFailed(e -> showError("Could not load details", task.getException().getMessage()));

        Thread t = new Thread(task, "bike-details-load");
        t.setDaemon(true);
        t.start();
    }

    /* ---------- history table columns (used by the page and by the dialog) ---------- */
    private void setupHistoryColumns(TableColumn<ServiceRequestDTO, String> date,
                                     TableColumn<ServiceRequestDTO, String> service,
                                     TableColumn<ServiceRequestDTO, String> status) {
        date.setCellValueFactory(c -> new ReadOnlyStringWrapper(String.valueOf(c.getValue().getDateReceived())));
        service.setCellValueFactory(c -> new ReadOnlyStringWrapper(show(c.getValue().getComplaint())));
        status.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getStatus()));

        status.setCellFactory(col -> new TableCell<>() {
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
    }

    /* ---------- buttons ---------- */
    @FXML
    private void onBack(ActionEvent e) {
        if (onBack != null) {
            onBack.run();
        }
    }

    /** "Service History" button: every service of this bike in a dialog. */
    @FXML
    private void onFullHistory(ActionEvent e) {
        TableColumn<ServiceRequestDTO, String> cDate = new TableColumn<>("Date");
        TableColumn<ServiceRequestDTO, String> cService = new TableColumn<>("Service");
        TableColumn<ServiceRequestDTO, String> cStatus = new TableColumn<>("Status");
        setupHistoryColumns(cDate, cService, cStatus);

        TableView<ServiceRequestDTO> table = new TableView<>();
        table.getColumns().add(cDate);
        table.getColumns().add(cService);
        table.getColumns().add(cStatus);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getStyleClass().addAll("data-table", "history-table");
        table.setPlaceholder(new Label("No service history yet"));
        table.setItems(FXCollections.observableArrayList(fullHistory));
        table.setPrefSize(620, 380);

        Label title = new Label("Service History - " + bike.getRegNumber()
                + "  (" + fullHistory.size() + " services)");
        title.getStyleClass().add("form-title");

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(tblHistory.getScene().getWindow());
        dialog.setTitle("Service History");
        DialogPane pane = dialog.getDialogPane();
        styleDialog(pane);
        pane.getButtonTypes().add(ButtonType.CLOSE);
        pane.setContent(new VBox(10, title, table));
        dialog.showAndWait();
    }

    /* ---------- helpers ---------- */
    private String show(String text) {
        return (text == null || text.trim().isEmpty()) ? "-" : text;
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        if (tblHistory.getScene() != null) {
            alert.initOwner(tblHistory.getScene().getWindow());
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