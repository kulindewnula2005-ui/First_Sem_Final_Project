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
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import lk.ijse.firstsemfinalproject25.dto.BikeDTO;
import lk.ijse.firstsemfinalproject25.dto.BillDTO;
import lk.ijse.firstsemfinalproject25.dto.CustomerDTO;
import lk.ijse.firstsemfinalproject25.dto.PaymentDTO;
import lk.ijse.firstsemfinalproject25.dto.ServicePartsDTO;
import lk.ijse.firstsemfinalproject25.dto.ServiceRequestDTO;
import lk.ijse.firstsemfinalproject25.dto.tm.BillTM;
import lk.ijse.firstsemfinalproject25.dto.tm.PaymentTM;
import lk.ijse.firstsemfinalproject25.model.BikeModel;
import lk.ijse.firstsemfinalproject25.model.BillModel;
import lk.ijse.firstsemfinalproject25.model.CustomerModel;
import lk.ijse.firstsemfinalproject25.model.PaymentModel;
import lk.ijse.firstsemfinalproject25.model.ServicePartsModel;
import lk.ijse.firstsemfinalproject25.model.ServiceRequestModel;
import lk.ijse.firstsemfinalproject25.model.SparePartsModel;

import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

public class BillingController implements Initializable {

    private static final int PAGE_SIZE = 8;   // rows per page
    private static final double EPS = 0.005;
    private static final String ALL_STATUS = "All status";
    private static final String ALL_METHODS = "All methods";
    private static final List<String> STATUS_ITEMS = List.of(ALL_STATUS, "Unpaid", "Partial", "Paid");
    private static final List<String> METHODS = List.of("Cash", "Card", "Bank Transfer");

    /* ---------- header, summary, tabs ---------- */
    @FXML private VBox root;
    @FXML private Button btnGenerateBill;
    @FXML private Button btnRecordPayment;
    @FXML private Label lblBilled;
    @FXML private Label lblBilledSub;
    @FXML private Label lblReceived;
    @FXML private Label lblReceivedSub;
    @FXML private Label lblOutstanding;
    @FXML private Label lblOutstandingSub;
    @FXML private Label lblToday;
    @FXML private Label lblTodaySub;
    @FXML private ToggleGroup tabGroup;
    @FXML private ToggleButton tglBills;
    @FXML private ToggleButton tglPayments;
    @FXML private TextField txtSearch;
    @FXML private Button btnSearch;
    @FXML private ComboBox<String> cmbFilter;

    /* ---------- bills table ---------- */
    @FXML private TableView<BillRow> tblBill;
    @FXML private TableColumn<BillRow, String> colBillId;
    @FXML private TableColumn<BillRow, String> colBillService;
    @FXML private TableColumn<BillRow, String> colBillPlate;
    @FXML private TableColumn<BillRow, String> colBillCustomer;
    @FXML private TableColumn<BillRow, String> colBillDate;
    @FXML private TableColumn<BillRow, String> colBillTotal;
    @FXML private TableColumn<BillRow, String> colBillPaid;
    @FXML private TableColumn<BillRow, String> colBillBalance;
    @FXML private TableColumn<BillRow, String> colBillStatus;
    @FXML private TableColumn<BillRow, Void> colBillActions;

    /* ---------- payments table ---------- */
    @FXML private TableView<PaymentRow> tblPayment;
    @FXML private TableColumn<PaymentRow, String> colPayId;
    @FXML private TableColumn<PaymentRow, String> colPayBill;
    @FXML private TableColumn<PaymentRow, String> colPayPlate;
    @FXML private TableColumn<PaymentRow, String> colPayCustomer;
    @FXML private TableColumn<PaymentRow, String> colPayMethod;
    @FXML private TableColumn<PaymentRow, String> colPayDate;
    @FXML private TableColumn<PaymentRow, String> colPayAmount;
    @FXML private TableColumn<PaymentRow, Void> colPayActions;

    /* ---------- footer ---------- */
    @FXML private HBox paginationBox;
    @FXML private Label lblCount;

    private final BillModel billModel = new BillModel();
    private final PaymentModel paymentModel = new PaymentModel();
    private final ServiceRequestModel serviceModel = new ServiceRequestModel();
    private final ServicePartsModel servicePartsModel = new ServicePartsModel();
    private final SparePartsModel sparePartsModel = new SparePartsModel();
    private final BikeModel bikeModel = new BikeModel();
    private final CustomerModel customerModel = new CustomerModel();

    /** One row of the Bills table: the bill (BillTM) plus values worked out for display. */
    private record BillRow(BillTM bill, String plate, String customer, String service,
                           double paid, double balance, String status) {}

    /** One row of the Payments table: the payment (PaymentTM) plus the bike and customer it belongs to. */
    private record PaymentRow(PaymentTM payment, String plate, String customer) {}

    private record LoadedData(List<BillDTO> bills, List<PaymentDTO> payments,
                              List<ServiceRequestDTO> services, List<BikeDTO> bikes,
                              List<CustomerDTO> customers) {}

    // everything loaded from the database (kept so dialogs and filters need no extra queries)
    private List<BillRow> billRows = new ArrayList<>();
    private List<PaymentRow> paymentRows = new ArrayList<>();
    private final Map<String, BillRow> billById = new HashMap<>();
    private final Map<String, ServiceRequestDTO> serviceMap = new HashMap<>();
    private final Map<String, BikeDTO> bikeMap = new HashMap<>();
    private final Map<String, CustomerDTO> customerMap = new HashMap<>();

    private Pager<BillRow> billPager;
    private Pager<PaymentRow> paymentPager;
    private Pager<?> activePager;
    private boolean showingBills = true;
    private boolean switching = false;

    private final PauseTransition searchDelay = new PauseTransition(Duration.millis(250));

    /* ======================================================= */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        lblTodaySub.setText(LocalDate.now().toString());

        setupBillTable();
        setupPaymentTable();
        billPager = new Pager<>(tblBill, "bills");
        paymentPager = new Pager<>(tblPayment, "payments");

        // never allow "no tab selected"
        tabGroup.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null && old != null) {
                old.setSelected(true);
            }
        });

        // live search (in memory): waits 250 ms after the last key press
        searchDelay.setOnFinished(e -> applyFilter(true));
        txtSearch.textProperty().addListener((obs, old, now) -> {
            if (!switching) {
                searchDelay.playFromStart();
            }
        });
        cmbFilter.valueProperty().addListener((obs, old, now) -> {
            if (!switching) {
                applyFilter(true);
            }
        });

        switchTab(true);
        loadAll();
    }

    /* ======================= TABS ======================= */
    @FXML
    private void onTab(ActionEvent e) {
        switchTab(e.getSource() == tglBills);
    }

    private void switchTab(boolean bills) {
        showingBills = bills;
        switching = true;

        tblBill.setVisible(bills);
        tblBill.setManaged(bills);
        tblPayment.setVisible(!bills);
        tblPayment.setManaged(!bills);

        txtSearch.clear();
        txtSearch.setPromptText(bills
                ? "Search bill by ID, job, number plate, customer or status..."
                : "Search payment by ID, bill, number plate, customer or method...");

        List<String> filterItems = new ArrayList<>();
        if (bills) {
            filterItems.addAll(STATUS_ITEMS);
        } else {
            filterItems.add(ALL_METHODS);
            filterItems.addAll(METHODS);
        }
        cmbFilter.setItems(FXCollections.observableArrayList(filterItems));
        cmbFilter.setValue(bills ? ALL_STATUS : ALL_METHODS);

        switching = false;
        searchDelay.stop();
        activePager = bills ? billPager : paymentPager;
        applyFilter(true);
    }

    /* ======================= BILLS TABLE ======================= */
    private void setupBillTable() {
        colBillId.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().bill().getBillId()));
        colBillService.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().bill().getServiceId()));
        colBillPlate.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().plate()));
        colBillCustomer.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().customer()));
        colBillDate.setCellValueFactory(c -> new ReadOnlyStringWrapper(String.valueOf(c.getValue().bill().getBillDate())));
        colBillTotal.setCellValueFactory(c -> new ReadOnlyStringWrapper(money(c.getValue().bill().getTotalAmount())));
        colBillPaid.setCellValueFactory(c -> new ReadOnlyStringWrapper(money(c.getValue().paid())));
        colBillBalance.setCellValueFactory(c -> new ReadOnlyStringWrapper(money(c.getValue().balance())));
        colBillStatus.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().status()));

        // coloured status badge
        colBillStatus.setCellFactory(col -> new TableCell<BillRow, String>() {
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
                    case "Paid"    -> badge.getStyleClass().add("bill-paid");
                    case "Partial" -> badge.getStyleClass().add("bill-partial");
                    default        -> badge.getStyleClass().add("bill-unpaid");
                }
                setGraphic(badge);
                setText(null);
            }
        });

        // view + pay + edit + delete icons
        colBillActions.setCellFactory(col -> new TableCell<BillRow, Void>() {
            private final Button btnView = new Button("👁");
            private final Button btnPay = new Button("💳");
            private final Button btnEdit = new Button("✎");
            private final Button btnDelete = new Button("🗑");
            private final HBox box = new HBox(2, btnView, btnPay, btnEdit, btnDelete);

            {
                btnView.getStyleClass().addAll("action-button", "view-button");
                btnPay.getStyleClass().addAll("action-button", "pay-button");
                btnEdit.getStyleClass().addAll("action-button", "edit-button");
                btnDelete.getStyleClass().addAll("action-button", "delete-button");
                box.setAlignment(Pos.CENTER_LEFT);

                btnView.setOnAction(e -> openView(row()));
                btnPay.setOnAction(e -> openPaymentForm(null, row().bill().getBillId()));
                btnEdit.setOnAction(e -> openEditBill(row()));
                btnDelete.setOnAction(e -> confirmDeleteBill(row()));
            }

            private BillRow row() {
                return getTableView().getItems().get(getIndex());
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                    return;
                }
                BillRow r = row();
                btnPay.setDisable("Paid".equals(r.status()));      // nothing left to pay
                boolean hasPayments = r.paid() > EPS;
                btnEdit.setDisable(hasPayments);                    // a paid-on bill is locked
                btnDelete.setDisable(hasPayments);
                setGraphic(box);
            }
        });

        // double-click a row = view the bill
        tblBill.setRowFactory(tv -> {
            TableRow<BillRow> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    openView(row.getItem());
                }
            });
            return row;
        });
    }

    /* ======================= PAYMENTS TABLE ======================= */
    private void setupPaymentTable() {
        colPayId.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().payment().getPaymentId()));
        colPayBill.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().payment().getBillId()));
        colPayPlate.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().plate()));
        colPayCustomer.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().customer()));
        colPayMethod.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().payment().getMethod()));
        colPayDate.setCellValueFactory(c -> new ReadOnlyStringWrapper(String.valueOf(c.getValue().payment().getPaymentDate())));
        colPayAmount.setCellValueFactory(c -> new ReadOnlyStringWrapper(money(c.getValue().payment().getAmount())));

        // edit + delete icons
        colPayActions.setCellFactory(col -> new TableCell<PaymentRow, Void>() {
            private final Button btnEdit = new Button("✎");
            private final Button btnDelete = new Button("🗑");
            private final HBox box = new HBox(4, btnEdit, btnDelete);

            {
                btnEdit.getStyleClass().addAll("action-button", "edit-button");
                btnDelete.getStyleClass().addAll("action-button", "delete-button");
                box.setAlignment(Pos.CENTER_LEFT);

                btnEdit.setOnAction(e -> openPaymentForm(getTableView().getItems().get(getIndex()).payment(), null));
                btnDelete.setOnAction(e -> confirmDeletePayment(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        // double-click a row = edit
        tblPayment.setRowFactory(tv -> {
            TableRow<PaymentRow> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    openPaymentForm(row.getItem().payment(), null);
                }
            });
            return row;
        });
    }

    /* ======================= LOAD DATA (background thread) ======================= */
    private void loadAll() {
        Task<LoadedData> task = new Task<>() {
            @Override
            protected LoadedData call() throws Exception {
                return new LoadedData(
                        billModel.getAll(),
                        paymentModel.getAll(),
                        serviceModel.getAll(),
                        bikeModel.getAll(),
                        customerModel.getAll());
            }
        };
        task.setOnSucceeded(e -> {
            build(task.getValue());
            applyFilter(false);
        });
        task.setOnFailed(e -> showError("Could not load billing data", task.getException().getMessage()));

        Thread t = new Thread(task, "billing-load");
        t.setDaemon(true);
        t.start();
    }

    private void build(LoadedData d) {
        serviceMap.clear();
        bikeMap.clear();
        customerMap.clear();
        billById.clear();
        for (ServiceRequestDTO s : d.services()) {
            serviceMap.put(s.getServiceId(), s);
        }
        for (BikeDTO b : d.bikes()) {
            bikeMap.put(b.getBikeId(), b);
        }
        for (CustomerDTO c : d.customers()) {
            customerMap.put(c.getCustomerId(), c);
        }

        Map<String, Double> paidByBill = new HashMap<>();
        for (PaymentDTO p : d.payments()) {
            paidByBill.merge(p.getBillId(), p.getAmount(), Double::sum);
        }

        double billed = 0;
        int unpaidCount = 0;
        List<BillRow> bills = new ArrayList<>();
        for (BillDTO b : d.bills()) {
            ServiceRequestDTO s = serviceMap.get(b.getServiceId());
            BikeDTO bike = s == null ? null : bikeMap.get(s.getBikeId());
            CustomerDTO c = bike == null ? null : customerMap.get(bike.getCustomerId());

            double paid = round2(paidByBill.getOrDefault(b.getBillId(), 0.0));
            double balance = round2(b.getTotalAmount() - paid);
            String status = balance <= EPS ? "Paid" : (paid > EPS ? "Partial" : "Unpaid");

            BillRow row = new BillRow(
                    new BillTM(b.getBillId(), b.getServiceId(), b.getBillDate(),
                            b.getLabourCharge(), b.getPartsTotal(), b.getTotalAmount()),
                    bike == null ? "-" : bike.getRegNumber(),
                    c == null ? "-" : c.getName(),
                    serviceLabel(s),
                    paid, balance, status);
            bills.add(row);
            billById.put(b.getBillId(), row);

            billed += b.getTotalAmount();
            if (balance > EPS) {
                unpaidCount++;
            }
        }

        double received = 0;
        double today = 0;
        LocalDate now = LocalDate.now();
        List<PaymentRow> payments = new ArrayList<>();
        for (PaymentDTO p : d.payments()) {
            BillRow br = billById.get(p.getBillId());
            payments.add(new PaymentRow(
                    new PaymentTM(p.getPaymentId(), p.getBillId(), p.getMethod(), p.getPaymentDate(), p.getAmount()),
                    br == null ? "-" : br.plate(),
                    br == null ? "-" : br.customer()));
            received += p.getAmount();
            if (now.equals(p.getPaymentDate())) {
                today += p.getAmount();
            }
        }

        billRows = bills;
        paymentRows = payments;

        // summary cards
        lblBilled.setText(rs(billed));
        lblBilledSub.setText(bills.size() + (bills.size() == 1 ? " bill" : " bills"));
        lblReceived.setText(rs(received));
        lblReceivedSub.setText(payments.size() + (payments.size() == 1 ? " payment" : " payments"));
        lblOutstanding.setText(rs(Math.max(0, round2(billed - received))));
        lblOutstandingSub.setText(unpaidCount + (unpaidCount == 1 ? " unpaid bill" : " unpaid bills"));
        lblToday.setText(rs(today));
        lblTodaySub.setText(now.toString());
    }

    /* ======================= SEARCH + FILTER (in memory) ======================= */
    @FXML
    private void onSearch(ActionEvent e) {
        searchDelay.stop();
        applyFilter(true);
    }

    private void applyFilter(boolean resetPage) {
        String kw = txtSearch.getText() == null ? "" : txtSearch.getText().trim().toLowerCase();
        String kwReg = BikeModel.normalizeReg(kw);
        String filter = cmbFilter.getValue();

        if (showingBills) {
            List<BillRow> out = new ArrayList<>();
            for (BillRow r : billRows) {
                boolean filterOk = filter == null || ALL_STATUS.equals(filter) || filter.equals(r.status());
                boolean textOk = kw.isEmpty()
                        || contains(r.bill().getBillId(), kw)
                        || contains(r.bill().getServiceId(), kw)
                        || contains(r.customer(), kw)
                        || contains(r.service(), kw)
                        || contains(r.status(), kw)
                        || (!kwReg.isEmpty() && BikeModel.normalizeReg(r.plate()).contains(kwReg));
                if (filterOk && textOk) {
                    out.add(r);
                }
            }
            billPager.setRows(out, resetPage);
        } else {
            List<PaymentRow> out = new ArrayList<>();
            for (PaymentRow r : paymentRows) {
                boolean filterOk = filter == null || ALL_METHODS.equals(filter) || filter.equals(r.payment().getMethod());
                boolean textOk = kw.isEmpty()
                        || contains(r.payment().getPaymentId(), kw)
                        || contains(r.payment().getBillId(), kw)
                        || contains(r.customer(), kw)
                        || contains(r.payment().getMethod(), kw)
                        || (!kwReg.isEmpty() && BikeModel.normalizeReg(r.plate()).contains(kwReg));
                if (filterOk && textOk) {
                    out.add(r);
                }
            }
            paymentPager.setRows(out, resetPage);
        }
    }

    private boolean contains(String text, String keywordLower) {
        return text != null && text.toLowerCase().contains(keywordLower);
    }

    /* ======================= PAGING (one pager per table, shared footer) ======================= */
    private class Pager<T> {
        private final TableView<T> table;
        private final String noun;
        private List<T> rows = new ArrayList<>();
        private int page = 1;

        Pager(TableView<T> table, String noun) {
            this.table = table;
            this.noun = noun;
            table.setPlaceholder(new Label("No " + noun + " found"));
        }

        void setRows(List<T> rows, boolean resetPage) {
            this.rows = rows;
            if (resetPage) {
                page = 1;
            }
            show();
        }

        private int pages() {
            return Math.max(1, (int) Math.ceil(rows.size() / (double) PAGE_SIZE));
        }

        void show() {
            int pages = pages();
            if (page > pages) page = pages;
            if (page < 1) page = 1;

            int from = (page - 1) * PAGE_SIZE;
            int to = Math.min(from + PAGE_SIZE, rows.size());
            table.setItems(FXCollections.observableArrayList(rows.subList(from, to)));

            if (activePager == this) {
                lblCount.setText(rows.isEmpty()
                        ? "No " + noun + " found"
                        : "Showing " + (from + 1) + "–" + to + " of " + rows.size() + " " + noun);
                buildPagination(pages);
            }
        }

        private void goTo(int p) {
            page = p;
            show();
        }

        private void buildPagination(int pages) {
            paginationBox.getChildren().clear();

            paginationBox.getChildren().add(pageButton("‹", page > 1, () -> goTo(page - 1), false));

            int end = Math.min(pages, Math.max(1, page - 2) + 4);
            int start = Math.max(1, end - 4);
            for (int p = start; p <= end; p++) {
                final int target = p;
                paginationBox.getChildren().add(
                        pageButton(String.valueOf(p), true, () -> goTo(target), p == page));
            }

            paginationBox.getChildren().add(pageButton("›", page < pages, () -> goTo(page + 1), false));
        }
    }

    private Button pageButton(String text, boolean enabled, Runnable action, boolean active) {
        Button b = new Button(text);
        b.getStyleClass().add("page-button");
        if (active) b.getStyleClass().add("page-button-active");
        b.setDisable(!enabled);
        b.setOnAction(e -> action.run());
        return b;
    }

    /* ======================= GENERATE BILL ======================= */
    @FXML
    private void onGenerateBill(ActionEvent e) {
        openGenerateBill();
    }

    private void openGenerateBill() {
        List<ServiceRequestDTO> jobs;
        String newId;
        try {
            jobs = serviceModel.getCompletedWithoutBill();
            newId = billModel.getNextId();
        } catch (Exception ex) {
            showError("Could not open the form", ex.getMessage());
            return;
        }
        if (jobs.isEmpty()) {
            showInfo("Nothing to bill",
                    "There are no completed jobs waiting for a bill.\nComplete a job on the Service page first.");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(root.getScene().getWindow());
        dialog.setTitle("Generate Bill");

        DialogPane pane = dialog.getDialogPane();
        styleDialog(pane);
        pane.setPrefWidth(520);

        ButtonType saveType = new ButtonType("Generate Bill", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        // ----- completed jobs without a bill -----
        Map<String, ServiceRequestDTO> jobByItem = new LinkedHashMap<>();
        for (ServiceRequestDTO s : jobs) {
            jobByItem.put(jobLabel(s), s);
        }
        ComboBox<String> cmbJob = new ComboBox<>(FXCollections.observableArrayList(jobByItem.keySet()));
        cmbJob.setPromptText("Select the completed job");
        cmbJob.setMaxWidth(Double.MAX_VALUE);
        cmbJob.getStyleClass().add("form-combo");

        TextField txtId = new TextField(newId);
        txtId.setDisable(true);
        TextField txtLabour = new TextField();
        txtLabour.setPromptText("e.g. 1500.00");

        Label lblParts = new Label(rs(0));
        Label lblTotal = new Label(rs(0));
        HBox partsRow = totalRow("Parts total (from the job)", lblParts, false);
        HBox totalRow = totalRow("Bill total", lblTotal, true);

        // live preview: parts total comes from the database, labour from the field
        Runnable refresh = () -> {
            ServiceRequestDTO s = jobByItem.get(cmbJob.getValue());
            double parts = 0;
            if (s != null) {
                try {
                    parts = servicePartsModel.getPartsTotal(s.getServiceId());
                } catch (Exception ignore) {
                    // preview only
                }
            }
            double labour = 0;
            try {
                labour = Double.parseDouble(txtLabour.getText().trim().replace(",", ""));
            } catch (Exception ignore) {
                // preview only
            }
            if (labour < 0) {
                labour = 0;
            }
            lblParts.setText(rs(round2(parts)));
            lblTotal.setText(rs(round2(parts + labour)));
        };
        cmbJob.valueProperty().addListener((obs, old, now) -> refresh.run());
        txtLabour.textProperty().addListener((obs, old, now) -> refresh.run());

        Label title = new Label("Generate Bill");
        title.getStyleClass().add("form-title");
        Label hint = new Label("The parts total is taken from the spare parts recorded on the job. Labour is entered here.");
        hint.getStyleClass().add("form-hint");
        hint.setWrapText(true);
        Label lblError = errorLabel();

        VBox content = new VBox(10,
                title,
                field("Bill ID", txtId),
                field("Completed job *", cmbJob),
                field("Labour charge (Rs.) *", txtLabour),
                partsRow,
                totalRow,
                hint,
                lblError);
        content.setPadding(new Insets(6, 4, 4, 4));
        pane.setContent(content);

        Button btnSave = (Button) pane.lookupButton(saveType);
        btnSave.getStyleClass().add("form-save");

        boolean[] saved = {false};
        btnSave.addEventFilter(ActionEvent.ACTION, ev -> {
            try {
                ServiceRequestDTO s = jobByItem.get(cmbJob.getValue());
                if (s == null) {
                    throw new Exception("Select the completed job to bill.");
                }
                double labour = parseMoney(txtLabour.getText(), "Enter a valid labour charge.");

                // parts total and grand total are worked out again by the model
                BillDTO dto = new BillDTO(txtId.getText(), s.getServiceId(), LocalDate.now(), labour, 0, 0);
                if (!billModel.save(dto)) {
                    throw new Exception("The bill could not be saved.");
                }
                saved[0] = true;
            } catch (Exception ex) {
                showInline(lblError, ex.getMessage());
                ev.consume();   // keep the dialog open
            }
        });

        Platform.runLater(cmbJob::requestFocus);
        dialog.showAndWait();

        if (saved[0]) {
            tglBills.setSelected(true);
            switchTab(true);   // show the new bill at the top of the Bills tab
            loadAll();
        }
    }

    /* ======================= EDIT BILL (labour charge) ======================= */
    private void openEditBill(BillRow r) {
        BillTM b = r.bill();

        double parts;
        try {
            parts = servicePartsModel.getPartsTotal(b.getServiceId());
        } catch (Exception ex) {
            showError("Could not open the form", ex.getMessage());
            return;
        }
        final double partsTotal = round2(parts);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(root.getScene().getWindow());
        dialog.setTitle("Edit Bill");

        DialogPane pane = dialog.getDialogPane();
        styleDialog(pane);
        pane.setPrefWidth(460);

        ButtonType saveType = new ButtonType("Update", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        Label title = new Label("Edit Bill " + b.getBillId());
        title.getStyleClass().add("form-title");

        VBox summary = new VBox(6,
                infoRow("Job", b.getServiceId() + "  (" + r.service() + ")"),
                infoRow("Bike", r.plate()),
                infoRow("Customer", r.customer()));

        TextField txtLabour = new TextField(String.format(Locale.US, "%.2f", b.getLabourCharge()));
        Label lblParts = new Label(rs(partsTotal));
        Label lblTotal = new Label(rs(round2(b.getLabourCharge() + partsTotal)));
        HBox partsRow = totalRow("Parts total (from the job)", lblParts, false);
        HBox totalRow = totalRow("Bill total", lblTotal, true);

        txtLabour.textProperty().addListener((obs, old, now) -> {
            double labour = 0;
            try {
                labour = Double.parseDouble(now.trim().replace(",", ""));
            } catch (Exception ignore) {
                // preview only
            }
            lblTotal.setText(rs(round2(Math.max(0, labour) + partsTotal)));
        });

        Label lblError = errorLabel();

        VBox content = new VBox(10, title, summary,
                field("Labour charge (Rs.) *", txtLabour), partsRow, totalRow, lblError);
        content.setPadding(new Insets(6, 4, 4, 4));
        pane.setContent(content);

        Button btnSave = (Button) pane.lookupButton(saveType);
        btnSave.getStyleClass().add("form-save");

        boolean[] saved = {false};
        btnSave.addEventFilter(ActionEvent.ACTION, ev -> {
            try {
                double labour = parseMoney(txtLabour.getText(), "Enter a valid labour charge.");
                BillDTO dto = new BillDTO(b.getBillId(), b.getServiceId(), b.getBillDate(), labour, 0, 0);
                if (!billModel.update(dto)) {
                    throw new Exception("The bill could not be updated.");
                }
                saved[0] = true;
            } catch (Exception ex) {
                showInline(lblError, ex.getMessage());
                ev.consume();
            }
        });

        Platform.runLater(txtLabour::requestFocus);
        dialog.showAndWait();

        if (saved[0]) {
            loadAll();
        }
    }

    /* ======================= RECORD / EDIT PAYMENT ======================= */
    @FXML
    private void onRecordPayment(ActionEvent e) {
        openPaymentForm(null, null);
    }

    /** existing != null: edit that payment. presetBillId: bill to pre-select for a new payment. */
    private void openPaymentForm(PaymentTM existing, String presetBillId) {
        boolean editing = existing != null;

        List<BillRow> candidates = new ArrayList<>();
        for (BillRow r : billRows) {
            if (r.balance() > EPS) {
                candidates.add(r);
            }
        }
        if (!editing && candidates.isEmpty()) {
            showInfo("Nothing to pay", "Every bill is fully paid, or no bill has been generated yet.");
            return;
        }

        String newId;
        try {
            newId = editing ? existing.getPaymentId() : paymentModel.getNextId();
        } catch (Exception ex) {
            showError("Could not create a payment ID", ex.getMessage());
            return;
        }

        // bills offered in the drop-down: unpaid ones, or just the bill of the payment being edited
        Map<String, BillRow> billByItem = new LinkedHashMap<>();
        if (editing) {
            BillRow r = billById.get(existing.getBillId());
            if (r != null) {
                billByItem.put(billLabel(r), r);
            }
        } else {
            for (BillRow r : candidates) {
                billByItem.put(billLabel(r), r);
            }
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(root.getScene().getWindow());
        dialog.setTitle(editing ? "Edit Payment" : "Record Payment");

        DialogPane pane = dialog.getDialogPane();
        styleDialog(pane);
        pane.setPrefWidth(520);

        ButtonType saveType = new ButtonType(editing ? "Update" : "Save Payment", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        TextField txtId = new TextField(newId);
        txtId.setDisable(true);

        ComboBox<String> cmbBill = new ComboBox<>(FXCollections.observableArrayList(billByItem.keySet()));
        cmbBill.setPromptText("Select the bill");
        cmbBill.setMaxWidth(Double.MAX_VALUE);
        cmbBill.getStyleClass().add("form-combo");

        // payment method
        List<String> methodItems = new ArrayList<>(METHODS);
        if (editing && existing.getMethod() != null && !methodItems.contains(existing.getMethod())) {
            methodItems.add(existing.getMethod());
        }
        ComboBox<String> cmbMethod = new ComboBox<>(FXCollections.observableArrayList(methodItems));
        cmbMethod.setMaxWidth(Double.MAX_VALUE);
        cmbMethod.getStyleClass().add("form-combo");
        cmbMethod.setValue(editing ? existing.getMethod() : "Cash");

        // amount
        TextField txtAmount = new TextField(editing ? String.format(Locale.US, "%.2f", existing.getAmount()) : "");
        txtAmount.setPromptText("e.g. 4500.00");
        Button btnFull = new Button(editing ? "Maximum" : "Full balance");
        btnFull.getStyleClass().add("link-button");
        HBox amountBox = new HBox(8, txtAmount, btnFull);
        HBox.setHgrow(txtAmount, Priority.ALWAYS);

        // what the bill looks like
        Label lblBillTotal = new Label("-");
        Label lblPaid = new Label("-");
        Label lblDue = new Label("-");
        HBox totalRowBox = totalRow("Bill total", lblBillTotal, false);
        HBox paidRowBox = totalRow(editing ? "Paid by other payments" : "Already paid", lblPaid, false);
        HBox dueRowBox = totalRow(editing ? "Maximum for this payment" : "Balance due", lblDue, true);

        Runnable refresh = () -> {
            BillRow r = billByItem.get(cmbBill.getValue());
            if (r == null) {
                lblBillTotal.setText("-");
                lblPaid.setText("-");
                lblDue.setText("-");
                return;
            }
            double max = maxPayable(r, existing);
            lblBillTotal.setText(rs(r.bill().getTotalAmount()));
            lblPaid.setText(rs(round2(r.bill().getTotalAmount() - max)));
            lblDue.setText(rs(max));
        };
        cmbBill.valueProperty().addListener((obs, old, now) -> {
            refresh.run();
            BillRow r = billByItem.get(now);
            if (!editing && r != null) {
                txtAmount.setText(String.format(Locale.US, "%.2f", maxPayable(r, null)));
            }
        });
        btnFull.setOnAction(ev -> {
            BillRow r = billByItem.get(cmbBill.getValue());
            if (r != null) {
                txtAmount.setText(String.format(Locale.US, "%.2f", maxPayable(r, existing)));
            }
        });

        // pre-select the bill
        if (editing) {
            if (!billByItem.isEmpty()) {
                cmbBill.setValue(billByItem.keySet().iterator().next());
            }
            cmbBill.setDisable(true);   // a payment cannot move to another bill
        } else if (presetBillId != null) {
            for (String item : billByItem.keySet()) {
                if (item.startsWith(presetBillId + " - ")) {
                    cmbBill.setValue(item);
                }
            }
        }
        refresh.run();

        Label title = new Label(editing ? "Edit Payment " + newId : "Record Payment");
        title.getStyleClass().add("form-title");
        Label lblError = errorLabel();

        VBox content = new VBox(10,
                title,
                field("Payment ID", txtId),
                field("Bill *", cmbBill),
                totalRowBox, paidRowBox, dueRowBox,
                row(field("Method *", cmbMethod), field("Amount (Rs.) *", amountBox)),
                lblError);
        content.setPadding(new Insets(6, 4, 4, 4));
        pane.setContent(content);

        Button btnSave = (Button) pane.lookupButton(saveType);
        btnSave.getStyleClass().add("form-save");

        boolean[] saved = {false};
        btnSave.addEventFilter(ActionEvent.ACTION, ev -> {
            try {
                BillRow r = billByItem.get(cmbBill.getValue());
                if (r == null) {
                    throw new Exception("Select the bill.");
                }
                if (cmbMethod.getValue() == null) {
                    throw new Exception("Select a payment method.");
                }
                double amount = parseMoney(txtAmount.getText(), "Enter a valid amount.");

                // date stays as it was when editing, and is today for a new payment (the model decides)
                PaymentDTO dto = new PaymentDTO(txtId.getText(), r.bill().getBillId(),
                        cmbMethod.getValue(), null, amount);

                boolean ok = editing ? paymentModel.update(dto) : paymentModel.save(dto);
                if (!ok) {
                    throw new Exception("The payment could not be saved.");
                }
                saved[0] = true;
            } catch (Exception ex) {
                showInline(lblError, ex.getMessage());
                ev.consume();
            }
        });

        Platform.runLater(txtAmount::requestFocus);
        dialog.showAndWait();

        if (saved[0]) {
            loadAll();
        }
    }

    /** The most this payment may be: the balance, plus the payment's own old amount when editing. */
    private double maxPayable(BillRow r, PaymentTM existing) {
        return existing == null ? r.balance() : round2(r.balance() + existing.getAmount());
    }

    /* ======================= VIEW BILL (details + parts + payments) ======================= */
    private void openView(BillRow r) {
        BillTM b = r.bill();
        ServiceRequestDTO s = serviceMap.get(b.getServiceId());
        BikeDTO bike = s == null ? null : bikeMap.get(s.getBikeId());
        CustomerDTO c = bike == null ? null : customerMap.get(bike.getCustomerId());

        List<ServicePartsDTO> parts;
        List<PaymentDTO> pays;
        try {
            parts = servicePartsModel.getByService(b.getServiceId());
            pays = paymentModel.getByBill(b.getBillId());
        } catch (Exception ex) {
            showError("Could not open the bill", ex.getMessage());
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(root.getScene().getWindow());
        dialog.setTitle("Bill " + b.getBillId());

        DialogPane pane = dialog.getDialogPane();
        styleDialog(pane);
        pane.setPrefWidth(580);
        pane.getButtonTypes().add(ButtonType.CLOSE);

        Label title = new Label("Bill " + b.getBillId() + "   |   " + r.status());
        title.getStyleClass().add("form-title");

        VBox details = new VBox(6,
                infoRow("Customer", c == null ? "-" : c.getName() + (c.getPhone() == null ? "" : "   " + c.getPhone())),
                infoRow("Bike", bike == null ? "-" : bike.getRegNumber() + " - " + bike.getBrand() + " " + bike.getModel()),
                infoRow("Job", b.getServiceId() + "  (" + r.service() + ")"),
                infoRow("Complaint", s == null ? "-" : dash(s.getComplaint())),
                infoRow("Bill date", String.valueOf(b.getBillDate())));

        // ----- parts used -----
        GridPane partsGrid = grid();
        addRow(partsGrid, 0, true, "Part", "Qty", "Unit price", "Amount");
        int line = 1;
        for (ServicePartsDTO p : parts) {
            addRow(partsGrid, line++, false,
                    partName(p.getPartId()), String.valueOf(p.getQuantity()),
                    money(p.getUnitPrice()), money(p.getQuantity() * p.getUnitPrice()));
        }
        Node partsNode = parts.isEmpty() ? mutedLabel("No spare parts were recorded for this job.") : partsGrid;

        // ----- amounts -----
        GridPane amounts = grid();
        addRow(amounts, 0, false, "Labour charge", rs(b.getLabourCharge()));
        addRow(amounts, 1, false, "Parts total", rs(b.getPartsTotal()));
        addRow(amounts, 2, true, "Bill total", rs(b.getTotalAmount()));
        addRow(amounts, 3, false, "Paid", rs(r.paid()));
        addRow(amounts, 4, true, "Balance", rs(r.balance()));

        // ----- payments -----
        GridPane payGrid = grid();
        addRow(payGrid, 0, true, "Payment", "Date", "Method", "Amount");
        int pl = 1;
        for (PaymentDTO p : pays) {
            addRow(payGrid, pl++, false, p.getPaymentId(), String.valueOf(p.getPaymentDate()),
                    p.getMethod(), money(p.getAmount()));
        }
        Node payNode = pays.isEmpty() ? mutedLabel("No payments yet.") : payGrid;

        VBox content = new VBox(12, title, details,
                section("PARTS USED"), partsNode,
                section("AMOUNTS"), amounts,
                section("PAYMENTS"), payNode);
        content.setPadding(new Insets(6, 8, 6, 4));

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(500);
        scroll.getStyleClass().add("bill-scroll");
        pane.setContent(scroll);

        dialog.showAndWait();
    }

    private GridPane grid() {
        GridPane g = new GridPane();
        g.setHgap(20);
        g.setVgap(6);
        return g;
    }

    /** Adds a row of labels to a grid. header/bold rows use the strong style. */
    private void addRow(GridPane g, int rowIndex, boolean strong, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            Label l = new Label(cells[i]);
            l.getStyleClass().add(strong ? "info-value" : "info-label");
            if (i > 0 && cells.length > 2) {
                GridPane.setHalignment(l, javafx.geometry.HPos.RIGHT);
            }
            g.add(l, i, rowIndex);
        }
        if (cells.length > 0) {
            ColumnConstraints first = new ColumnConstraints();
            first.setHgrow(Priority.ALWAYS);
            if (g.getColumnConstraints().isEmpty()) {
                g.getColumnConstraints().add(first);
            }
        }
    }

    private Label section(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("section-label");
        return l;
    }

    private Label mutedLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("form-hint");
        return l;
    }

    private String partName(String partId) {
        try {
            String name = sparePartsModel.getNameById(partId);
            return name == null ? partId : name;
        } catch (Exception ex) {
            return partId;
        }
    }

    /* ======================= DELETE ======================= */
    private void confirmDeleteBill(BillRow r) {
        if (!confirm("Delete Bill", "Delete bill " + r.bill().getBillId() + " (" + r.plate() + ")?\n"
                + "The job will become unbilled again.")) {
            return;
        }
        try {
            billModel.delete(r.bill().getBillId());
            loadAll();
        } catch (Exception ex) {
            showError("Cannot delete this bill", ex.getMessage());
        }
    }

    private void confirmDeletePayment(PaymentRow r) {
        PaymentTM p = r.payment();
        if (!confirm("Delete Payment", "Delete payment " + p.getPaymentId() + " of " + rs(p.getAmount())
                + " (bill " + p.getBillId() + ")?")) {
            return;
        }
        try {
            paymentModel.delete(p.getPaymentId());
            loadAll();
        } catch (Exception ex) {
            showError("Cannot delete this payment", ex.getMessage());
        }
    }

    private boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.initOwner(root.getScene().getWindow());
        alert.setTitle(title);
        alert.setHeaderText(null);
        styleDialog(alert.getDialogPane());
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.YES;
    }

    /* ======================= helpers ======================= */
    private String jobLabel(ServiceRequestDTO s) {
        BikeDTO bike = bikeMap.get(s.getBikeId());
        CustomerDTO c = bike == null ? null : customerMap.get(bike.getCustomerId());
        return s.getServiceId() + " - " + (bike == null ? "-" : bike.getRegNumber())
                + " - " + (c == null ? "-" : c.getName()) + " (" + serviceLabel(s) + ")";
    }

    private String billLabel(BillRow r) {
        return r.bill().getBillId() + " - " + r.plate() + " - " + r.customer()
                + "  (Balance " + rs(r.balance()) + ")";
    }

    /** Service type, or the complaint when the job has no type. */
    private String serviceLabel(ServiceRequestDTO s) {
        if (s == null) {
            return "-";
        }
        if (s.getServiceType() != null && !s.getServiceType().trim().isEmpty()) {
            return s.getServiceType();
        }
        return dash(s.getComplaint());
    }

    private String dash(String text) {
        return (text == null || text.trim().isEmpty()) ? "-" : text;
    }

    private double parseMoney(String text, String errorMessage) throws Exception {
        try {
            double v = Double.parseDouble(text.trim().replace(",", ""));
            if (Double.isNaN(v) || Double.isInfinite(v)) {
                throw new NumberFormatException();
            }
            return v;
        } catch (NumberFormatException nfe) {
            throw new Exception(errorMessage);
        }
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String money(double amount) {
        return String.format(Locale.US, "%,.2f", amount);
    }

    private String rs(double amount) {
        return "Rs. " + money(amount);
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

    private HBox infoRow(String name, String value) {
        Label key = new Label(name);
        key.getStyleClass().add("info-label");
        key.setMinWidth(90);
        Label val = new Label(value);
        val.getStyleClass().add("info-value");
        val.setWrapText(true);
        val.setMaxWidth(400);
        return new HBox(10, key, val);
    }

    /** A boxed "label ........ value" line used for the totals. */
    private HBox totalRow(String name, Label value, boolean big) {
        Label key = new Label(name);
        key.getStyleClass().add("total-label");
        value.getStyleClass().add(big ? "total-value" : "info-value");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox box = new HBox(10, key, spacer, value);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("total-row");
        return box;
    }

    private Label errorLabel() {
        Label lbl = new Label();
        lbl.getStyleClass().add("form-error");
        lbl.setWrapText(true);
        lbl.setVisible(false);
        lbl.setManaged(false);
        return lbl;
    }

    private void showInline(Label lbl, String message) {
        lbl.setText(message);
        lbl.setVisible(true);
        lbl.setManaged(true);
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        if (root.getScene() != null) {
            alert.initOwner(root.getScene().getWindow());
        }
        alert.setTitle(title);
        alert.setHeaderText(null);
        styleDialog(alert.getDialogPane());
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.initOwner(root.getScene().getWindow());
        alert.setTitle(title);
        alert.setHeaderText(null);
        styleDialog(alert.getDialogPane());
        alert.showAndWait();
    }

    private void styleDialog(DialogPane pane) {
        for (String file : new String[]{ "/styles/billing.css"}) {
            URL css = getClass().getResource(file);
            if (css != null) {
                pane.getStylesheets().add(css.toExternalForm());
            }
        }
        pane.getStyleClass().add("dark-dialog");
    }
}