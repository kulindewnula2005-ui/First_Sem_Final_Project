package lk.ijse.firstsemfinalproject25.controller;

import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import lk.ijse.firstsemfinalproject25.dto.BikeDTO;
import lk.ijse.firstsemfinalproject25.dto.CustomerDTO;
import lk.ijse.firstsemfinalproject25.dto.ServiceRequestDTO;
import lk.ijse.firstsemfinalproject25.model.BikeModel;
import lk.ijse.firstsemfinalproject25.model.CustomerModel;
import lk.ijse.firstsemfinalproject25.model.ServiceRequestModel;
import lk.ijse.firstsemfinalproject25.util.AppNavigator;
import lk.ijse.firstsemfinalproject25.util.AppSettings;
import lk.ijse.firstsemfinalproject25.util.CameraService;
import lk.ijse.firstsemfinalproject25.util.PlateMatcher;
import lk.ijse.firstsemfinalproject25.util.PlateOcr;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScanController implements Initializable {

    /* ---------- camera card ---------- */
    @FXML private VBox root;
    @FXML private StackPane previewPane;
    @FXML private ImageView preview;
    @FXML private Label lblPlaceholder;
    @FXML private Label lblStatus;
    @FXML private ComboBox<String> cmbCamera;
    @FXML private Button btnCamera;
    @FXML private CheckBox chkAuto;
    @FXML private Button btnRead;
    @FXML private Button btnUpload;

    /* ---------- right side ---------- */
    @FXML private VBox boxOcrWarn;
    @FXML private TextField txtPlate;
    @FXML private Button btnSearch;
    @FXML private Button btnAgain;
    @FXML private VBox resultBox;

    private final BikeModel bikeModel = new BikeModel();
    private final CustomerModel customerModel = new CustomerModel();
    private final ServiceRequestModel serviceModel = new ServiceRequestModel();
    private final CameraService camera = new CameraService();

    // every camera start / stop runs one after the other on this thread
    private final ExecutorService camExec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "scan-camera");
        t.setDaemon(true);
        return t;
    });

    private AnimationTimer painter;
    private Timeline scanTimer;
    private WritableImage fxImage;
    private BufferedImage lastShown;
    private BufferedImage photoImage;      // not null while an uploaded photo is on screen

    private boolean shown;                 // this page is on screen
    private boolean camWanted = true;      // false after "Stop camera"
    private boolean camStarted;
    private int cameraIndex = 0;
    private boolean suppressCamChange;

    private boolean ocrFound;
    private boolean busy;                  // a plate is being read right now
    private boolean locked;                // a result is on screen; auto-scan waits for "Scan again"
    private String lastKey;                // used to ask for two equal readings in a row

    private record Attempt(PlateOcr.Variant variant, int psm) { }

    private record Outcome(String text, List<String> candidates, List<PlateMatcher.Match> matches) { }

    private record FoundData(CustomerDTO customer, List<ServiceRequestDTO> history) { }

    private record SearchResult(BikeDTO exact, List<PlateMatcher.Match> similar) { }

    /* ======================================================= */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        preview.setManaged(false);
        previewPane.widthProperty().addListener((obs, old, now) -> fitPreview());
        previewPane.heightProperty().addListener((obs, old, now) -> fitPreview());

        Rectangle clip = new Rectangle();
        clip.setArcWidth(16);
        clip.setArcHeight(16);
        clip.widthProperty().bind(previewPane.widthProperty());
        clip.heightProperty().bind(previewPane.heightProperty());
        previewPane.setClip(clip);

        painter = new AnimationTimer() {
            @Override
            public void handle(long now) {
                paintFrame();
            }
        };
        scanTimer = new Timeline(new KeyFrame(Duration.millis(600), e -> tick()));
        scanTimer.setCycleCount(Timeline.INDEFINITE);

        // plates are always capital letters
        txtPlate.textProperty().addListener((obs, old, now) -> {
            if (now != null && !now.equals(now.toUpperCase())) {
                txtPlate.setText(now.toUpperCase());
            }
        });

        cmbCamera.valueProperty().addListener((obs, old, now) -> {
            if (suppressCamChange || now == null) {
                return;
            }
            cameraIndex = Math.max(0, cmbCamera.getItems().indexOf(now));
            camWanted = true;
            loadCamerasAndStart();
        });

        setStatus("Camera off", "neutral");
        showIdle();

        // the camera is on only while this page is on screen
        root.sceneProperty().addListener((obs, old, scene) -> {
            if (scene != null) {
                onShown();
            } else {
                onHidden();
            }
        });
    }

    private void onShown() {
        shown = true;
        refreshOcrState();
        painter.start();
        scanTimer.play();
        if (camWanted) {
            loadCamerasAndStart();
        } else {
            showPlaceholder("The camera is off.");
        }
    }

    private void onHidden() {
        shown = false;
        painter.stop();
        scanTimer.stop();
        camStarted = false;
        lastShown = null;
        camExec.submit(camera::stop);
    }

    /* ======================= CAMERA ======================= */
    private void loadCamerasAndStart() {
        showPlaceholder("Starting the camera...");
        setStatus("Starting camera...", "neutral");
        btnCamera.setText("⏹   Stop camera");
        camStarted = false;

        final int wanted = cameraIndex;
        camExec.submit(() -> {
            List<String> names;
            try {
                names = camera.findCameras();
            } catch (Exception ex) {
                String message = ex.getMessage();
                Platform.runLater(() -> cameraFailed(message));
                return;
            }
            Platform.runLater(() -> fillCameraCombo(names));

            try {
                camera.start(wanted);
                Platform.runLater(this::cameraStarted);
            } catch (Exception ex) {
                String message = ex.getMessage();
                Platform.runLater(() -> cameraFailed(message));
            }
        });
    }

    private void fillCameraCombo(List<String> names) {
        suppressCamChange = true;
        cmbCamera.setItems(FXCollections.observableArrayList(names));
        cameraIndex = Math.max(0, Math.min(cameraIndex, names.size() - 1));
        cmbCamera.getSelectionModel().select(cameraIndex);
        suppressCamChange = false;
    }

    private void cameraStarted() {
        if (!shown) {
            return;   // the page was closed while the camera was starting
        }
        camStarted = true;
        lastShown = null;
        setStatus(locked ? "Result shown" : "Scanning...", locked ? "ok" : "neutral");
    }

    private void cameraFailed(String message) {
        camStarted = false;
        btnCamera.setText("▶   Start camera");
        setStatus("No camera", "bad");
        showPlaceholder("Camera not available:\n" + (message == null ? "unknown error" : message)
                + "\n\nYou can still upload a photo or type the plate number.");
    }

    @FXML
    private void onCamera(ActionEvent e) {
        if (camStarted || camWanted) {
            camWanted = false;
            camStarted = false;
            camExec.submit(camera::stop);
            btnCamera.setText("▶   Start camera");
            setStatus("Camera off", "neutral");
            showPlaceholder("The camera is off.");
        } else {
            camWanted = true;
            photoImage = null;
            loadCamerasAndStart();
        }
    }

    /* ======================= PREVIEW ======================= */
    private void paintFrame() {
        if (photoImage != null) {
            return;   // an uploaded photo is on screen
        }
        BufferedImage img = camera.shownFrame();
        if (img == null || img == lastShown) {
            return;
        }
        lastShown = img;
        draw(img);
        if (lblPlaceholder.isVisible()) {
            showPlaceholder(null);
        }
    }

    private void draw(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        if (fxImage == null || (int) fxImage.getWidth() != w || (int) fxImage.getHeight() != h) {
            fxImage = new WritableImage(w, h);
            preview.setImage(fxImage);
            fitPreview();
        }
        int[] pixels = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        fxImage.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
    }

    /** Centres the picture in its box, keeping the shape. */
    private void fitPreview() {
        Image image = preview.getImage();
        double pw = previewPane.getWidth();
        double ph = previewPane.getHeight();
        if (image == null || pw <= 0 || ph <= 0) {
            return;
        }
        double scale = Math.min(pw / image.getWidth(), ph / image.getHeight());
        double iw = image.getWidth() * scale;
        double ih = image.getHeight() * scale;
        preview.setFitWidth(iw);
        preview.setFitHeight(ih);
        preview.setLayoutX((pw - iw) / 2);
        preview.setLayoutY((ph - ih) / 2);
    }

    /** null hides the message and shows the picture again. */
    private void showPlaceholder(String text) {
        boolean show = text != null;
        lblPlaceholder.setVisible(show);
        lblPlaceholder.setManaged(show);
        if (show) {
            lblPlaceholder.setText(text);
        }
        preview.setVisible(!show);
    }

    /* ======================= THE SCAN LOOP ======================= */
    private void tick() {
        if (camStarted && !camera.isRunning()) {
            camStarted = false;
            String error = camera.getError();
            cameraFailed(error == null ? "The camera stopped." : error);
            return;
        }
        if (!camStarted || !chkAuto.isSelected() || locked || busy || photoImage != null || !ocrFound) {
            return;
        }
        readFrame(false);
    }

    private void readFrame(boolean force) {
        BufferedImage frame = camera.copyOfFrame();
        if (frame == null) {
            if (force) {
                setStatus("No picture yet", "warn");
            }
            return;
        }
        recognize(CameraService.cropGuide(frame), false, force);
    }

    @FXML
    private void onRead(ActionEvent e) {
        refreshOcrState();
        if (!ocrFound) {
            showMessage("Tesseract OCR is not installed, so the plate cannot be read from the picture. "
                    + "You can still type the plate number and press Search.", true);
            return;
        }
        if (busy) {
            return;
        }
        if (!camStarted) {
            setStatus("Start the camera first", "warn");
            return;
        }
        photoImage = null;
        lastShown = null;
        locked = false;
        lastKey = null;
        readFrame(true);
    }

    /** Reads one picture on a background thread, then shows what it found. */
    private void recognize(BufferedImage image, boolean photo, boolean force) {
        busy = true;
        camera.setGuideState(1);
        setStatus("Reading the plate...", "warn");
        final int width = photo ? Math.min(2400, Math.max(1400, image.getWidth())) : 900;

        Task<Outcome> task = new Task<>() {
            @Override
            protected Outcome call() throws Exception {
                return readPlate(image, photo, force, width);
            }
        };
        task.setOnSucceeded(e -> {
            busy = false;
            handleOutcome(task.getValue(), force);
        });
        task.setOnFailed(e -> {
            busy = false;
            camera.setGuideState(0);
            Throwable error = task.getException();
            ocrProblem(error == null ? "Unknown error." : error.getMessage());
        });
        startThread(task, "scan-read");
    }

    /** Background work: clean the picture, read it, compare with the registered bikes. */
    private Outcome readPlate(BufferedImage image, boolean photo, boolean force, int width) throws Exception {
        List<BikeDTO> bikes = bikeModel.getAll();

        List<Attempt> attempts = new ArrayList<>();
        if (photo) {
            attempts.add(new Attempt(PlateOcr.Variant.GRAY, 6));
            attempts.add(new Attempt(PlateOcr.Variant.GRAY, 11));
            attempts.add(new Attempt(PlateOcr.Variant.BINARY, 11));
            attempts.add(new Attempt(PlateOcr.Variant.INVERTED, 6));
        } else {
            attempts.add(new Attempt(PlateOcr.Variant.GRAY, 7));
            attempts.add(new Attempt(PlateOcr.Variant.BINARY, 6));
            if (force) {
                attempts.add(new Attempt(PlateOcr.Variant.INVERTED, 7));
            }
        }

        Outcome best = new Outcome("", List.of(), List.of());
        for (Attempt attempt : attempts) {
            BufferedImage prepared = PlateOcr.prepare(image, attempt.variant(), width);
            String text = PlateOcr.read(prepared, attempt.psm());
            List<String> candidates = PlateMatcher.candidates(text);
            Outcome outcome = new Outcome(text, candidates, PlateMatcher.rank(candidates, bikes));

            if (!outcome.matches().isEmpty() && outcome.matches().get(0).distance() == 0) {
                return outcome;   // an exact match: no need to try more
            }
            if (score(outcome) > score(best)) {
                best = outcome;
            }
        }
        return best;
    }

    private int score(Outcome o) {
        if (!o.matches().isEmpty()) {
            return 1000 - o.matches().get(0).distance() * 100;
        }
        if (!o.candidates().isEmpty()) {
            return 10 + PlateMatcher.quality(o.candidates().get(0));
        }
        return 0;
    }

    private void handleOutcome(Outcome o, boolean force) {
        camera.setGuideState(0);
        List<PlateMatcher.Match> m = o.matches();

        // 1. exact match
        if (!m.isEmpty() && m.get(0).distance() == 0) {
            lastKey = null;
            accept(m.get(0).bike(), m.get(0).plate(), false);
            return;
        }

        // 2. one clearly closest bike: only trusted after two equal readings in a row
        boolean unique = !m.isEmpty() && (m.size() == 1 || m.get(0).distance() < m.get(1).distance());
        if (unique) {
            String key = "P:" + m.get(0).bike().getBikeId();
            if (force || key.equals(lastKey)) {
                lastKey = null;
                accept(m.get(0).bike(), m.get(0).plate(), true);
            } else {
                lastKey = key;
                setStatus("Checking...", "warn");
            }
            return;
        }

        // 3. a plate-like text that is not registered (or several similar plates)
        if (!o.candidates().isEmpty()) {
            String candidate = o.candidates().get(0);
            if (force || PlateMatcher.quality(candidate) >= 2) {
                String key = "N:" + candidate;
                if (force || key.equals(lastKey)) {
                    lastKey = null;
                    showNotFound(PlateMatcher.pretty(candidate), m);
                } else {
                    lastKey = key;
                    setStatus("Checking...", "warn");
                }
                return;
            }
        }

        // 4. nothing that looks like a plate
        lastKey = null;
        if (force) {
            setStatus("No plate found", "warn");
            showMessage("No number plate could be read. Move closer, add light, keep the plate inside the "
                    + "frame, or type the number above.", false);
        } else {
            setStatus("Scanning...", "neutral");
        }
    }

    private void ocrProblem(String message) {
        refreshOcrState();
        chkAuto.setSelected(false);   // do not repeat the same error every second
        setStatus("Reading failed", "bad");
        if (ocrFound) {
            showMessage("The plate could not be read: " + message + "\nAuto-scan was switched off.", true);
        }
    }

    /* ======================= UPLOAD A PHOTO ======================= */
    @FXML
    private void onUpload(ActionEvent e) {
        refreshOcrState();
        if (!ocrFound) {
            showMessage("Tesseract OCR is not installed, so a photo cannot be read. "
                    + "You can still type the plate number and press Search.", true);
            return;
        }
        if (busy) {
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a photo of the number plate");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Pictures (*.jpg, *.jpeg, *.png, *.bmp, *.gif)", "*.jpg", "*.jpeg", "*.png", "*.bmp", "*.gif"));
        File file = chooser.showOpenDialog(root.getScene().getWindow());
        if (file == null) {
            return;
        }

        setStatus("Loading the photo...", "neutral");
        Task<BufferedImage> load = new Task<>() {
            @Override
            protected BufferedImage call() throws Exception {
                BufferedImage image = ImageIO.read(file);
                if (image == null) {
                    throw new IOException("This file is not a supported picture.");
                }
                return image;
            }
        };
        load.setOnSucceeded(ev -> {
            BufferedImage image = load.getValue();
            locked = false;
            lastKey = null;
            photoImage = CameraService.toArgb(image, 1600);
            showPlaceholder(null);
            draw(photoImage);
            recognize(image, true, true);
        });
        load.setOnFailed(ev -> {
            setStatus("Photo not loaded", "bad");
            showMessage("Could not open the photo: " + load.getException().getMessage(), true);
        });
        startThread(load, "scan-load");
    }

    /* ======================= TYPED SEARCH ======================= */
    @FXML
    private void onSearch(ActionEvent e) {
        String text = txtPlate.getText() == null ? "" : txtPlate.getText().trim();
        if (BikeModel.normalizeReg(text).isEmpty()) {
            showMessage("Type a number plate first.", true);
            return;
        }

        locked = true;
        lastKey = null;
        camera.setGuideState(0);
        setStatus("Searching...", "neutral");
        resultBox.getChildren().setAll(text("Searching...", "res-hint"));

        Task<SearchResult> task = new Task<>() {
            @Override
            protected SearchResult call() throws Exception {
                BikeDTO exact = bikeModel.searchByRegNumber(text);
                if (exact != null) {
                    return new SearchResult(exact, List.of());
                }

                String normalized = BikeModel.normalizeReg(text);
                List<PlateMatcher.Match> similar =
                        new ArrayList<>(PlateMatcher.rank(List.of(normalized), bikeModel.getAll()));

                // plates that simply contain what was typed
                if (normalized.length() >= 3) {
                    Set<String> already = new HashSet<>();
                    for (PlateMatcher.Match m : similar) {
                        already.add(m.bike().getBikeId());
                    }
                    for (BikeDTO b : bikeModel.searchPartialReg(normalized)) {
                        if (already.add(b.getBikeId())) {
                            similar.add(new PlateMatcher.Match(b, normalized, 1));
                        }
                    }
                }
                return new SearchResult(null, similar);
            }
        };
        task.setOnSucceeded(ev -> {
            SearchResult r = task.getValue();
            if (r.exact() != null) {
                accept(r.exact(), text, false);
            } else {
                showNotFound(text.toUpperCase(), r.similar());
            }
        });
        task.setOnFailed(ev -> {
            setStatus("Search failed", "bad");
            showMessage("The search failed: " + task.getException().getMessage(), true);
        });
        startThread(task, "scan-search");
    }

    @FXML
    private void onAgain(ActionEvent e) {
        locked = false;
        lastKey = null;
        photoImage = null;
        lastShown = null;
        camera.setGuideState(0);
        txtPlate.clear();
        showIdle();
        setStatus(camStarted ? "Scanning..." : "Camera off", "neutral");
        if (camStarted) {
            showPlaceholder(null);
        }
    }

    /* ======================= TESSERACT ======================= */
    private void refreshOcrState() {
        ocrFound = PlateOcr.findTesseract() != null;
        boxOcrWarn.setVisible(!ocrFound);
        boxOcrWarn.setManaged(!ocrFound);
    }

    @FXML
    private void onCheckOcr(ActionEvent e) {
        refreshOcrState();
        if (ocrFound) {
            chkAuto.setSelected(true);
            setStatus(camStarted ? "Scanning..." : "Camera off", "neutral");
        }
    }

    @FXML
    private void onLocateOcr(ActionEvent e) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select tesseract.exe");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("tesseract.exe", "tesseract.exe", "tesseract"));
        File file = chooser.showOpenDialog(root.getScene().getWindow());
        if (file == null) {
            return;
        }
        AppSettings.set(PlateOcr.SETTING_PATH, file.getAbsolutePath());
        try {
            AppSettings.save();
        } catch (IOException ex) {
            showMessage("Could not remember the location: " + ex.getMessage(), true);
        }
        onCheckOcr(e);
    }

    /* ======================= RESULT: FOUND ======================= */
    private void accept(BikeDTO bike, String read, boolean possible) {
        locked = true;
        camera.setGuideState(possible ? 3 : 2);
        txtPlate.setText(bike.getRegNumber());
        setStatus(possible ? "Possible match" : "Plate found", possible ? "warn" : "ok");
        showFound(bike, read, possible);
    }

    private void showFound(BikeDTO bike, String read, boolean possible) {
        resultBox.getChildren().setAll(text("Loading the details...", "res-hint"));

        Task<FoundData> task = new Task<>() {
            @Override
            protected FoundData call() throws Exception {
                return new FoundData(
                        customerModel.search(bike.getCustomerId()),
                        serviceModel.getHistoryByBike(bike.getBikeId()));
            }
        };
        task.setOnSucceeded(e -> buildFound(bike, read, possible, task.getValue()));
        task.setOnFailed(e -> showMessage("Could not load the details: " + task.getException().getMessage(), true));
        startThread(task, "scan-found");
    }

    private void buildFound(BikeDTO bike, String read, boolean possible, FoundData d) {
        List<Node> nodes = new ArrayList<>();

        nodes.add(chip(possible ? "⚠   Possible match - check the plate" : "✔   Registered bike",
                possible ? "warn" : "ok"));

        Label plate = new Label(bike.getRegNumber());
        plate.getStyleClass().add("plate-big");
        nodes.add(plate);

        if (possible) {
            nodes.add(text("Read from the picture: " + PlateMatcher.pretty(read)
                    + "   |   Registered: " + bike.getRegNumber(), "res-hint"));
        }

        nodes.add(text("BIKE", "res-section"));
        nodes.add(infoRow("Brand", dash(bike.getBrand())));
        nodes.add(infoRow("Model", dash(bike.getModel())));
        nodes.add(infoRow("Year", String.valueOf(bike.getYear())));
        nodes.add(infoRow("Engine No", dash(bike.getEngineNo())));
        nodes.add(infoRow("Chassis No", dash(bike.getChassisNo())));

        nodes.add(text("OWNER", "res-section"));
        CustomerDTO c = d.customer();
        nodes.add(infoRow("Name", c == null ? "-" : dash(c.getName())));
        nodes.add(infoRow("Phone", c == null ? "-" : dash(c.getPhone())));
        nodes.add(infoRow("Address", c == null ? "-" : dash(c.getAddress())));

        for (ServiceRequestDTO s : d.history()) {
            if (!ServiceRequestModel.COMPLETED.equals(s.getStatus())) {
                nodes.add(chip("⚠   Open job " + s.getServiceId() + " - " + s.getStatus(), "warn"));
                break;
            }
        }

        nodes.add(text("LAST SERVICES", "res-section"));
        if (d.history().isEmpty()) {
            nodes.add(text("No service history yet.", "res-hint"));
        } else {
            for (int i = 0; i < Math.min(3, d.history().size()); i++) {
                nodes.add(historyRow(d.history().get(i)));
            }
        }

        Button btnDetails = new Button("🏍   View Bike Details");
        btnDetails.getStyleClass().add("add-button");
        btnDetails.setMaxWidth(Double.MAX_VALUE);
        btnDetails.setOnAction(ev -> openDetails(bike));

        Button btnService = new Button("🔧   New Service Request");
        btnService.getStyleClass().add("outline-button");
        btnService.setMaxWidth(Double.MAX_VALUE);
        btnService.setOnAction(ev -> AppNavigator.goToService(bike.getBikeId()));

        nodes.add(btnDetails);
        nodes.add(btnService);

        resultBox.getChildren().setAll(nodes);
    }

    private HBox historyRow(ServiceRequestDTO s) {
        Label date = text(String.valueOf(s.getDateReceived()), "info-label");
        date.setMinWidth(90);
        date.setWrapText(false);

        String what = (s.getServiceType() != null && !s.getServiceType().trim().isEmpty())
                ? s.getServiceType() : dash(s.getComplaint());
        Label service = text(what, "info-value");
        HBox.setHgrow(service, Priority.ALWAYS);

        Label badge = new Label(s.getStatus());
        badge.getStyleClass().add("badge");
        switch (s.getStatus()) {
            case "In Progress" -> badge.getStyleClass().add("badge-progress");
            case "Pending" -> badge.getStyleClass().add("badge-pending");
            case "Completed" -> badge.getStyleClass().add("badge-completed");
            default -> { }
        }
        HBox row = new HBox(10, date, service, badge);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return row;
    }

    /** Opens the Bike Details page in place of this page; its back arrow returns here. */
    private void openDetails(BikeDTO bike) {
        try {
            if (!(root.getParent() instanceof Pane host)) {
                throw new Exception("The page is not inside the dashboard.");
            }
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/bikeDetails.fxml"));
            Parent details = loader.load();
            BikeDetailsController controller = loader.getController();
            controller.setBike(bike, () -> host.getChildren().setAll(root));
            host.getChildren().setAll(details);
        } catch (Exception ex) {
            showMessage("Could not open the bike details: " + ex.getMessage(), true);
        }
    }

    /* ======================= RESULT: NOT FOUND ======================= */
    private void showNotFound(String plateText, List<PlateMatcher.Match> similar) {
        locked = true;
        camera.setGuideState(3);
        setStatus("Not registered", "warn");

        List<Node> nodes = new ArrayList<>();
        nodes.add(chip("⚠   Not registered", "warn"));

        Label plate = new Label(plateText);
        plate.getStyleClass().add("plate-big");
        nodes.add(plate);
        nodes.add(text("No bike with this number plate is in the database.", "res-hint"));

        if (!similar.isEmpty()) {
            nodes.add(text("DID YOU MEAN", "res-section"));
            for (int i = 0; i < Math.min(5, similar.size()); i++) {
                BikeDTO b = similar.get(i).bike();
                Button option = new Button(b.getRegNumber() + "   ·   " + b.getBrand() + " " + b.getModel());
                option.getStyleClass().add("sugg-button");
                option.setMaxWidth(Double.MAX_VALUE);
                option.setOnAction(ev -> accept(b, b.getRegNumber(), false));
                nodes.add(option);
            }
        }

        Button register = new Button("＋   Register this bike");
        register.getStyleClass().add("add-button");
        register.setMaxWidth(Double.MAX_VALUE);
        register.setOnAction(ev -> openRegister(plateText));
        nodes.add(register);

        resultBox.getChildren().setAll(nodes);
    }

    /* ======================= REGISTER A NEW BIKE ======================= */
    private void openRegister(String plate) {
        List<CustomerDTO> customers;
        String newId;
        try {
            customers = new ArrayList<>(customerModel.getAll());
            newId = bikeModel.getNextId();
        } catch (Exception ex) {
            showMessage("Could not open the form: " + ex.getMessage(), true);
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(root.getScene().getWindow());
        dialog.setTitle("Register Bike");

        DialogPane pane = dialog.getDialogPane();
        styleDialog(pane);
        pane.setPrefWidth(500);

        ButtonType saveType = new ButtonType("Register", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        // owner: pick a customer, or add a new one with the red +
        ObservableList<String> ownerItems = FXCollections.observableArrayList();
        for (CustomerDTO c : customers) {
            ownerItems.add(c.getCustomerId() + " - " + c.getName());
        }
        ComboBox<String> cmbOwner = new ComboBox<>(ownerItems);
        cmbOwner.setPromptText("Select owner");
        cmbOwner.setMaxWidth(Double.MAX_VALUE);
        cmbOwner.getStyleClass().add("form-combo");

        Button btnNewCustomer = new Button("＋");
        btnNewCustomer.getStyleClass().add("plus-small");
        btnNewCustomer.setOnAction(ev -> {
            CustomerDTO saved = QuickAddDialogsService.addCustomer(pane.getScene().getWindow());
            if (saved != null) {
                String item = saved.getCustomerId() + " - " + saved.getName();
                ownerItems.add(item);
                cmbOwner.setValue(item);
            }
        });
        HBox ownerBox = new HBox(8, cmbOwner, btnNewCustomer);
        HBox.setHgrow(cmbOwner, Priority.ALWAYS);

        TextField txtId = new TextField(newId);
        txtId.setDisable(true);
        TextField txtReg = new TextField(plate);
        TextField txtBrand = new TextField();
        TextField txtModel = new TextField();
        TextField txtYear = new TextField();
        TextField txtEngine = new TextField();
        TextField txtChassis = new TextField();
        txtBrand.setPromptText("e.g. Honda");
        txtModel.setPromptText("e.g. CBR 150R");
        txtYear.setPromptText("e.g. 2021");
        txtEngine.setPromptText("optional");
        txtChassis.setPromptText("optional");

        Label title = new Label("Register Bike");
        title.getStyleClass().add("form-title");
        Label lblError = new Label();
        lblError.getStyleClass().add("form-error");
        lblError.setWrapText(true);
        lblError.setVisible(false);
        lblError.setManaged(false);

        VBox content = new VBox(10,
                title,
                row(field("Bike ID", txtId), field("Owner *", ownerBox)),
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
                String owner = cmbOwner.getValue();
                if (owner == null) {
                    throw new Exception("Select the owner (customer), or add one with the red + button.");
                }
                String customerId = owner.substring(0, owner.indexOf(" - "));

                int year;
                try {
                    year = Integer.parseInt(txtYear.getText().trim());
                } catch (NumberFormatException nfe) {
                    throw new Exception("Enter a valid year.");
                }

                BikeDTO dto = new BikeDTO(txtId.getText(), customerId,
                        txtReg.getText(), txtBrand.getText(), txtModel.getText(), year,
                        blankToNull(txtEngine.getText()), blankToNull(txtChassis.getText()));
                if (!bikeModel.save(dto)) {
                    throw new Exception("The bike could not be saved.");
                }
                saved[0] = bikeModel.search(dto.getBikeId());
            } catch (Exception ex) {
                lblError.setText(ex.getMessage());
                lblError.setVisible(true);
                lblError.setManaged(true);
                ev.consume();   // keep the dialog open
            }
        });

        Platform.runLater(txtBrand::requestFocus);
        dialog.showAndWait();

        if (saved[0] != null) {
            accept(saved[0], saved[0].getRegNumber(), false);
        }
    }

    /* ======================= RESULT: MESSAGES ======================= */
    private void showIdle() {
        Label icon = new Label("📷");
        icon.getStyleClass().add("res-big-icon");
        resultBox.getChildren().setAll(
                icon,
                text("Ready to scan", "card-title"),
                text("Hold a number plate inside the frame. When it is read, the bike and its owner appear here.",
                        "res-hint"),
                text("No camera? Use Upload photo, or type the plate number above and press Search.", "res-hint"));
    }

    private void showMessage(String message, boolean error) {
        resultBox.getChildren().setAll(text(message, error ? "res-error" : "res-hint"));
    }

    private void setStatus(String textValue, String kind) {
        lblStatus.setText(textValue);
        lblStatus.getStyleClass().removeAll("chip-neutral", "chip-ok", "chip-warn", "chip-bad");
        lblStatus.getStyleClass().add("chip-" + kind);
    }

    /* ======================= helpers ======================= */
    private Label text(String value, String... styles) {
        Label label = new Label(value);
        label.getStyleClass().addAll(styles);
        label.setWrapText(true);
        return label;
    }

    private Label chip(String value, String kind) {
        Label label = new Label(value);
        label.getStyleClass().addAll("scan-chip", "chip-" + kind);
        return label;
    }

    private HBox infoRow(String name, String value) {
        Label key = text(name, "info-label");
        key.setMinWidth(90);
        key.setWrapText(false);
        Label val = text(value, "info-value");
        val.setMaxWidth(250);
        return new HBox(10, key, val);
    }

    private String dash(String value) {
        return (value == null || value.trim().isEmpty()) ? "-" : value;
    }

    private String blankToNull(String value) {
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
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

    private void startThread(Task<?> task, String name) {
        Thread t = new Thread(task, name);
        t.setDaemon(true);
        t.start();
    }

    private void styleDialog(DialogPane pane) {
        for (String file : new String[]{ "/styles/scan.css"}) {
            URL css = getClass().getResource(file);
            if (css != null) {
                pane.getStylesheets().add(css.toExternalForm());
            }
        }
        pane.getStyleClass().add("dark-dialog");
    }
}