package lk.ijse.firstsemfinalproject25.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import lk.ijse.firstsemfinalproject25.dto.UserDTO;
import lk.ijse.firstsemfinalproject25.model.DashboardModel;
import lk.ijse.firstsemfinalproject25.model.DashboardModel.DashboardData;
import lk.ijse.firstsemfinalproject25.model.DashboardModel.JobRow;
import lk.ijse.firstsemfinalproject25.util.AppNavigator;
import lk.ijse.firstsemfinalproject25.util.Session;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

public class DashboardController implements Initializable {

    public Button btnScan;
    public Button btnNewService;
    public Button btnAddCustomer;
    public Button btnAddBike;
    public ToggleButton btnDsh;
    public ToggleButton btnCustomer;
    public ToggleButton btnBike;
    public ToggleButton btnService;
    public ToggleButton btnMechanic;
    public ToggleButton btnSpareParts;
    public ToggleButton btnPayments;
    public ToggleButton btnReports;
    public ToggleButton btnSettings;
    public Button btnBell;
    /* ---------- layout ---------- */
    @FXML private StackPane contentArea;
    @FXML private VBox dashboardPane;
    @FXML private ToggleGroup navGroup;
    @FXML private ImageView logoView;
    @FXML private ImageView bannerImage;

    /* ---------- header / user ---------- */
    @FXML private Label lblDateTime;
    @FXML private Label lblUser;
    @FXML private Label lblRole;
    @FXML private Label lblWelcomeName;

    /* ---------- stat cards ---------- */
    @FXML private Label lblTotalCustomers;
    @FXML private Label lblCustomersToday;
    @FXML private Label lblTotalBikes;
    @FXML private Label lblBikesToday;
    @FXML private Label lblPending;
    @FXML private Label lblInProgress;
    @FXML private Label lblCompleted;

    /* ---------- table ---------- */
    @FXML private TableView<JobRow> jobsTable;
    @FXML private TableColumn<JobRow, Integer> colNo;
    @FXML private TableColumn<JobRow, String> colPlate;
    @FXML private TableColumn<JobRow, String> colModel;
    @FXML private TableColumn<JobRow, String> colStatus;
    @FXML private TableColumn<JobRow, String> colDate;

    private final DashboardModel dashboardModel = new DashboardModel();

    private Timeline clockTimeline;
    private Timeline refreshTimeline;
    private boolean refreshing = false;

    private static final DateTimeFormatter CLOCK_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy   |   hh:mm:ss a");

    /* ======================================================= */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // lets other pages (for example the scan page) ask the dashboard to open a page
        AppNavigator.setHandler(this::selectNav);

        loadImages();
        setupNavigation();
        setupTable();
        setupUser();

        startClock();          // real-time date & time (every second)
        startAutoRefresh();    // real-time data (every 5 seconds)
    }

    /* ---------- images (safe: no crash if a file is missing) ---------- */
    private void loadImages() {
        setImage(logoView, "/images/logo.png");        // the FXML already sets logo.jpeg
        setImage(bannerImage, "/images/banner.jpg");

        // the banner picture follows the banner box and never makes the page wider
        if (bannerImage.getParent() instanceof Region parent) {
            bannerImage.fitWidthProperty().bind(parent.widthProperty());
            bannerImage.fitHeightProperty().bind(parent.heightProperty());
        }
    }

    private void setImage(ImageView view, String path) {
        URL res = getClass().getResource(path);
        if (res != null) {
            view.setImage(new Image(res.toExternalForm()));
        }
    }

    /* ---------- navigation ---------- */
    private void setupNavigation() {
        // never allow "no page selected"
        navGroup.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null && old != null) {
                old.setSelected(true);
            }
        });
    }

    @FXML
    private void onNav(ActionEvent e) {
        ToggleButton btn = (ToggleButton) e.getSource();
        openPage(btn.getId());
    }

    /**
     * Loads /view/<page>.fxml into the content area (the dashboard itself stays in memory).
     * When a page cannot be opened, the reason is shown on the screen instead of failing silently.
     */
    private void openPage(String page) {
        if ("dashboard".equals(page)) {
            contentArea.getChildren().setAll(dashboardPane);
            return;
        }

        URL url = getClass().getResource("/view/" + page + ".fxml");
        if (url == null) {
            showPageMessage(page.toUpperCase() + " page coming soon\n"
                    + "(the file /view/" + page + ".fxml is not in the built project)", false);
            return;
        }

        try {
            Node node = FXMLLoader.load(url);
            contentArea.getChildren().setAll(node);
        } catch (Throwable ex) {            // Throwable also catches a missing library (NoClassDefFoundError)
            ex.printStackTrace();
            Throwable cause = ex;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            showPageMessage("Could not open the " + page + " page:\n"
                    + cause.getClass().getSimpleName() + ": " + cause.getMessage(), true);
        }
    }

    private void showPageMessage(String text, boolean error) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(720);
        label.setStyle("-fx-text-fill: " + (error ? "#ff6b6b" : "white") + "; -fx-font-size: 16px;");
        StackPane.setAlignment(label, Pos.CENTER);
        contentArea.getChildren().setAll(label);
    }

    /** Highlights a sidebar button (when the page has one) and opens the page. */
    private void selectNav(String id) {
        for (Toggle t : navGroup.getToggles()) {
            if (id.equals(((ToggleButton) t).getId())) {
                t.setSelected(true);
                break;
            }
        }
        openPage(id);
    }

    /* ---------- table ---------- */
    private void setupTable() {
        colNo.setCellValueFactory(c ->
                new ReadOnlyObjectWrapper<>(jobsTable.getItems().indexOf(c.getValue()) + 1));
        colPlate.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().plate()));
        colModel.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().model()));
        colStatus.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().status()));
        colDate.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().date()));

        // coloured status badge
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Label badge = new Label(status);
                badge.getStyleClass().add("badge");
                switch (status) {
                    case "In Progress" -> badge.getStyleClass().add("badge-progress");
                    case "Pending"     -> badge.getStyleClass().add("badge-pending");
                    case "Completed"   -> badge.getStyleClass().add("badge-completed");
                    default -> { }
                }
                setGraphic(badge);
                setText(null);
            }
        });
        jobsTable.setPlaceholder(new Label("No service jobs yet"));
    }

    /* ---------- signed-in user ---------- */
    private void setupUser() {
        // whenever the user changes (for example a profile edit on the Settings page) the labels follow
        Session.addListener(u -> {
            if (u != null) {
                setUser(u.getFullName(), u.getRole());
            }
        });
        setUser("Admin", "Administrator");   // shown until the real user is loaded

        Task<UserDTO> task = new Task<>() {
            @Override
            protected UserDTO call() throws Exception {
                return Session.ensureUser();
            }
        };
        task.setOnSucceeded(e -> {
            UserDTO user = task.getValue();
            if (user != null) {
                setUser(user.getFullName(), user.getRole());
            }
        });
        Thread t = new Thread(task, "dashboard-user");
        t.setDaemon(true);
        t.start();
    }

    public void setUser(String name, String role) {
        lblUser.setText(name);
        lblRole.setText(role);
        lblWelcomeName.setText(name);
    }

    /* ---------- REAL-TIME: clock ---------- */
    private void startClock() {
        updateClock();
        clockTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateClock()));
        clockTimeline.setCycleCount(Timeline.INDEFINITE);
        clockTimeline.play();
    }

    private void updateClock() {
        lblDateTime.setText(LocalDateTime.now().format(CLOCK_FORMAT));
    }

    /* ---------- REAL-TIME: stats + table ---------- */
    private void startAutoRefresh() {
        refreshData();
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(5), e -> refreshData()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    /** Runs the database work on a background thread so the screen never freezes. */
    private void refreshData() {
        if (refreshing) {
            return;   // the previous refresh is still running
        }
        refreshing = true;

        Task<DashboardData> task = new Task<>() {
            @Override
            protected DashboardData call() throws Exception {
                return dashboardModel.load();
            }
        };
        task.setOnSucceeded(e -> {
            applyData(task.getValue());
            refreshing = false;
        });
        task.setOnFailed(e -> {
            task.getException().printStackTrace();
            refreshing = false;
        });

        Thread t = new Thread(task, "dashboard-refresh");
        t.setDaemon(true);
        t.start();
    }

    private void applyData(DashboardData d) {
        lblTotalCustomers.setText(String.valueOf(d.totalCustomers()));
        lblCustomersToday.setText("↑ " + d.customersToday() + " visited today");
        lblTotalBikes.setText(String.valueOf(d.totalBikes()));
        lblBikesToday.setText("↑ " + d.bikesToday() + " serviced today");
        lblPending.setText(String.valueOf(d.pending()));
        lblInProgress.setText(String.valueOf(d.inProgress()));
        lblCompleted.setText(String.valueOf(d.completedToday()));
        jobsTable.setItems(FXCollections.observableArrayList(d.recentJobs()));
    }

    /* ---------- button actions ---------- */
    @FXML private void onViewDetails(ActionEvent e)       { selectNav("service"); }
    @FXML private void onBell(ActionEvent e)              { /* TODO: show notifications */ }
    @FXML private void onLogout(ActionEvent e)            { /* TODO: go back to the login screen */ }

    @FXML
    private void onScanPlate(ActionEvent e) {
        System.out.println("Scan button clicked");   // you can delete this line once it works
        openPage("scan");
    }

    @FXML private void onNewServiceRequest(ActionEvent e) { selectNav("service"); }
    @FXML private void onAddCustomer(ActionEvent e)       { selectNav("customer"); }
    @FXML private void onAddBike(ActionEvent e)           { selectNav("bike"); }

    /** Called when the window closes so the timers stop. */
    public void stop() {
        if (clockTimeline != null) {
            clockTimeline.stop();
        }
        if (refreshTimeline != null) {
            refreshTimeline.stop();
        }
    }
}