package lk.ijse.firstsemfinalproject25.controller;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import lk.ijse.firstsemfinalproject25.dto.BikeDTO;
import lk.ijse.firstsemfinalproject25.dto.BillDTO;
import lk.ijse.firstsemfinalproject25.dto.CustomerDTO;
import lk.ijse.firstsemfinalproject25.dto.MechanicDTO;
import lk.ijse.firstsemfinalproject25.dto.PaymentDTO;
import lk.ijse.firstsemfinalproject25.dto.ServiceRequestDTO;
import lk.ijse.firstsemfinalproject25.dto.SparePartsDTO;
import lk.ijse.firstsemfinalproject25.model.BikeModel;
import lk.ijse.firstsemfinalproject25.model.BillModel;
import lk.ijse.firstsemfinalproject25.model.CustomerModel;
import lk.ijse.firstsemfinalproject25.model.MechanicModel;
import lk.ijse.firstsemfinalproject25.model.PaymentModel;
import lk.ijse.firstsemfinalproject25.model.ServiceRequestModel;
import lk.ijse.firstsemfinalproject25.model.SparePartsModel;

import java.awt.Color;
import java.awt.Desktop;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.ToDoubleFunction;

public class ReportsController implements Initializable {

    private enum Tab { ALL, DAILY, COMPLETED, PENDING, INCOME, STOCK }

    private enum Fmt { COUNT, MONEY, PERCENT }

    private static final DateTimeFormatter PICK_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_FORMAT = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_NAME = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /* ---------- header, tabs, date ---------- */
    @FXML private VBox root;
    @FXML private Label lblMessage;
    @FXML private ToggleGroup tabGroup;
    @FXML private ToggleButton tglAll;
    @FXML private ToggleButton tglDaily;
    @FXML private ToggleButton tglCompleted;
    @FXML private ToggleButton tglPending;
    @FXML private ToggleButton tglIncome;
    @FXML private ToggleButton tglStock;
    @FXML private DatePicker dpDate;
    @FXML private Label lblScope;
    @FXML private Button btnGenerate;

    /* ---------- summary cards ---------- */
    @FXML private Label lblT1;
    @FXML private Label lblV1;
    @FXML private Label lblT2;
    @FXML private Label lblV2;
    @FXML private Label lblT3;
    @FXML private Label lblV3;
    @FXML private Label lblT4;
    @FXML private Label lblV4;

    /* ---------- chart ---------- */
    @FXML private Label lblChartTitle;
    @FXML private Label lblChartSub;
    @FXML private BarChart<String, Number> chart;
    @FXML private CategoryAxis xAxis;
    @FXML private NumberAxis yAxis;

    /* ---------- table ---------- */
    @FXML private Label lblTableTitle;
    @FXML private Label lblRowCount;
    @FXML private TableView<List<String>> tblReport;

    private final ServiceRequestModel serviceModel = new ServiceRequestModel();
    private final BillModel billModel = new BillModel();
    private final PaymentModel paymentModel = new PaymentModel();
    private final BikeModel bikeModel = new BikeModel();
    private final CustomerModel customerModel = new CustomerModel();
    private final MechanicModel mechanicModel = new MechanicModel();
    private final SparePartsModel partModel = new SparePartsModel();

    private record LoadedData(List<ServiceRequestDTO> services, List<BillDTO> bills,
                              List<PaymentDTO> payments, List<BikeDTO> bikes,
                              List<CustomerDTO> customers, List<MechanicDTO> mechanics,
                              List<SparePartsDTO> parts) {}

    private record ChartData(List<String> labels, List<Double> values, List<LocalDate> dates) {}

    private record Report(String title, String chartTitle, String chartSub, Fmt fmt,
                          String[] cardTitles, String[] cardValues, ChartData chart,
                          String tableTitle, List<String> headers, List<List<String>> rows,
                          String emptyText, String scope) {}

    private LoadedData data;
    private Report current;
    private Tab tab = Tab.ALL;

    private final Map<String, ServiceRequestDTO> serviceMap = new HashMap<>();
    private final Map<String, BikeDTO> bikeMap = new HashMap<>();
    private final Map<String, CustomerDTO> customerMap = new HashMap<>();
    private final Map<String, MechanicDTO> mechanicMap = new HashMap<>();
    private final Map<String, BillDTO> billById = new HashMap<>();
    private final Map<String, BillDTO> billByService = new HashMap<>();

    /* ======================================================= */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        tglAll.setUserData(Tab.ALL);
        tglDaily.setUserData(Tab.DAILY);
        tglCompleted.setUserData(Tab.COMPLETED);
        tglPending.setUserData(Tab.PENDING);
        tglIncome.setUserData(Tab.INCOME);
        tglStock.setUserData(Tab.STOCK);

        // never allow "no tab selected"
        tabGroup.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null && old != null) {
                old.setSelected(true);
            }
        });

        // date picker: "21 Oct 2026", pick from the calendar only
        dpDate.setConverter(new StringConverter<LocalDate>() {
            @Override
            public String toString(LocalDate date) {
                return date == null ? "" : PICK_FORMAT.format(date);
            }

            @Override
            public LocalDate fromString(String text) {
                try {
                    return (text == null || text.trim().isEmpty()) ? null : LocalDate.parse(text.trim(), PICK_FORMAT);
                } catch (Exception ex) {
                    return null;
                }
            }
        });
        dpDate.setEditable(false);
        dpDate.setValue(LocalDate.now());
        dpDate.valueProperty().addListener((obs, old, now) -> {
            if (now == null) {
                dpDate.setValue(LocalDate.now());
            } else {
                render();
            }
        });

        chart.setAnimated(false);
        xAxis.setAnimated(false);
        yAxis.setAnimated(false);

        loadAll(null);
    }

    /* ======================= TABS ======================= */
    @FXML
    private void onTab(ActionEvent e) {
        tab = (Tab) ((ToggleButton) e.getSource()).getUserData();
        hideMessage();
        render();
    }

    /* ======================= LOAD DATA (background thread) ======================= */
    private void loadAll(Runnable afterRender) {
        Task<LoadedData> task = new Task<>() {
            @Override
            protected LoadedData call() throws Exception {
                return new LoadedData(
                        serviceModel.getAll(),
                        billModel.getAll(),
                        paymentModel.getAll(),
                        bikeModel.getAll(),
                        customerModel.getAll(),
                        mechanicModel.getAll(),
                        partModel.getAll());
            }
        };
        task.setOnSucceeded(e -> {
            data = task.getValue();
            index();
            render();
            btnGenerate.setDisable(false);
            if (afterRender != null) {
                afterRender.run();
            }
        });
        task.setOnFailed(e -> {
            btnGenerate.setDisable(false);
            showMessage("Could not load report data: " + task.getException().getMessage(), true);
        });

        Thread t = new Thread(task, "reports-load");
        t.setDaemon(true);
        t.start();
    }

    private void index() {
        serviceMap.clear();
        bikeMap.clear();
        customerMap.clear();
        mechanicMap.clear();
        billById.clear();
        billByService.clear();
        for (ServiceRequestDTO s : data.services()) {
            serviceMap.put(s.getServiceId(), s);
        }
        for (BikeDTO b : data.bikes()) {
            bikeMap.put(b.getBikeId(), b);
        }
        for (CustomerDTO c : data.customers()) {
            customerMap.put(c.getCustomerId(), c);
        }
        for (MechanicDTO m : data.mechanics()) {
            mechanicMap.put(m.getMechanicId(), m);
        }
        for (BillDTO b : data.bills()) {
            billById.put(b.getBillId(), b);
            billByService.put(b.getServiceId(), b);
        }
    }

    /* ======================= RENDER ======================= */
    private void render() {
        if (data == null) {
            return;
        }
        LocalDate date = dpDate.getValue() == null ? LocalDate.now() : dpDate.getValue();

        // Pending and Stock show the situation right now, so the date is not used
        // (the "All" tab uses the date only for its Daily bar)
        boolean dateBased = tab == Tab.ALL || tab == Tab.DAILY || tab == Tab.COMPLETED || tab == Tab.INCOME;
        dpDate.setDisable(!dateBased);

        current = switch (tab) {
            case ALL -> all(date);
            case DAILY -> daily(date);
            case COMPLETED -> completed(date);
            case PENDING -> pending();
            case INCOME -> income(date);
            case STOCK -> stock();
        };
        show(current);
    }

    private void show(Report r) {
        lblT1.setText(r.cardTitles()[0]);
        lblV1.setText(r.cardValues()[0]);
        lblT2.setText(r.cardTitles()[1]);
        lblV2.setText(r.cardValues()[1]);
        lblT3.setText(r.cardTitles()[2]);
        lblV3.setText(r.cardValues()[2]);
        lblT4.setText(r.cardTitles()[3]);
        lblV4.setText(r.cardValues()[3]);

        lblScope.setText(r.scope());
        lblChartTitle.setText(r.chartTitle());
        lblChartSub.setText(r.chartSub());
        setChart(r.chart(), r.fmt());

        lblTableTitle.setText(r.tableTitle());
        lblRowCount.setText(r.rows().size() + (r.rows().size() == 1 ? " row" : " rows"));
        setTable(r);
    }

    /* ======================= THE SIX REPORTS ======================= */

    /**
     * "All" tab: one bar per report, each shown as a level from 0 to 100 %.
     *   Daily     = jobs received on the picked date that are completed
     *   Completed = all jobs that are completed
     *   Pending   = all jobs that are still open
     *   Income    = money collected out of the money billed
     *   Stock     = spare parts that are above their reorder level
     */
    private Report all(LocalDate date) {
        // ---- Daily ----
        int dayTotal = 0;
        int dayDone = 0;
        for (ServiceRequestDTO s : data.services()) {
            if (date.equals(s.getDateReceived())) {
                dayTotal++;
                if (ServiceRequestModel.COMPLETED.equals(s.getStatus())) {
                    dayDone++;
                }
            }
        }

        // ---- Completed / Pending ----
        int jobs = data.services().size();
        int done = 0;
        for (ServiceRequestDTO s : data.services()) {
            if (ServiceRequestModel.COMPLETED.equals(s.getStatus())) {
                done++;
            }
        }
        int open = jobs - done;

        // ---- Income ----
        double billed = 0;
        for (BillDTO b : data.bills()) {
            billed += b.getTotalAmount();
        }
        double paid = 0;
        for (PaymentDTO p : data.payments()) {
            paid += p.getAmount();
        }

        // ---- Stock ----
        int parts = data.parts().size();
        int healthy = 0;
        int low = 0;
        int out = 0;
        for (SparePartsDTO p : data.parts()) {
            if (p.getStockQty() <= 0) {
                out++;
            } else if (p.getStockQty() <= p.getReorderLevel()) {
                low++;
            } else {
                healthy++;
            }
        }

        double dailyLevel = percent(dayDone, dayTotal);
        double completedLevel = percent(done, jobs);
        double pendingLevel = percent(open, jobs);
        double incomeLevel = percent(paid, billed);
        double stockLevel = percent(healthy, parts);

        List<String> labels = List.of("Daily", "Completed", "Pending", "Income", "Stock");
        List<Double> values = List.of(dailyLevel, completedLevel, pendingLevel, incomeLevel, stockLevel);

        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Daily", pct(dailyLevel),
                dayDone + " of " + dayTotal + " jobs received on " + PICK_FORMAT.format(date) + " are completed"));
        rows.add(List.of("Completed", pct(completedLevel),
                done + " of " + jobs + " jobs are completed"));
        rows.add(List.of("Pending", pct(pendingLevel),
                open + " of " + jobs + " jobs are still open"));
        rows.add(List.of("Income", pct(incomeLevel),
                rs(paid) + " collected out of " + rs(billed) + " billed"));
        rows.add(List.of("Stock", pct(stockLevel),
                healthy + " of " + parts + " parts are above the reorder level (" + low + " low, " + out + " out)"));

        return new Report(
                "Overview Report - " + PICK_FORMAT.format(date),
                "Current Level of Each Report", "Each bar is shown as a percentage (0 - 100 %)", Fmt.PERCENT,
                new String[]{"Jobs on Date", "Open Jobs", "Income Collected", "Low / Out Stock"},
                new String[]{String.valueOf(dayTotal), String.valueOf(open), rs(paid), String.valueOf(low + out)},
                new ChartData(labels, values, null),
                "Report levels",
                List.of("Report", "Level", "Details"),
                rows, "There is no data yet",
                "Daily bar uses " + PICK_FORMAT.format(date) + " - the other bars show the current level");
    }

    private Report daily(LocalDate date) {
        List<ServiceRequestDTO> jobs = new ArrayList<>();
        for (ServiceRequestDTO s : data.services()) {
            if (date.equals(s.getDateReceived())) {
                jobs.add(s);
            }
        }
        int completed = 0;
        for (ServiceRequestDTO s : jobs) {
            if (ServiceRequestModel.COMPLETED.equals(s.getStatus())) {
                completed++;
            }
        }
        int pending = jobs.size() - completed;

        List<List<String>> rows = new ArrayList<>();
        for (ServiceRequestDTO s : jobs) {
            rows.add(List.of(s.getServiceId(), plateOf(s), customerOf(s), serviceOf(s),
                    mechanicOf(s), s.getStatus(), String.valueOf(s.getDateReceived())));
        }

        ChartData weekly = weekChart(date, d -> countReceived(d));
        return new Report(
                "Daily Report - " + PICK_FORMAT.format(date),
                "Daily Service Overview", weekText(date), Fmt.COUNT,
                new String[]{"Total Jobs", "Completed", "Pending", "Total Income"},
                new String[]{String.valueOf(jobs.size()), String.valueOf(completed),
                        String.valueOf(pending), rs(sumPayments(date, date))},
                weekly,
                "Jobs received on " + PICK_FORMAT.format(date),
                List.of("ID", "Number Plate", "Customer", "Service", "Mechanic", "Status", "Received"),
                rows, "No jobs were received on this date",
                "Jobs received on " + PICK_FORMAT.format(date));
    }

    private Report completed(LocalDate date) {
        List<ServiceRequestDTO> jobs = new ArrayList<>();
        for (ServiceRequestDTO s : data.services()) {
            if (date.equals(s.getDateCompleted())) {
                jobs.add(s);
            }
        }
        int billed = 0;
        double billedAmount = 0;
        List<List<String>> rows = new ArrayList<>();
        for (ServiceRequestDTO s : jobs) {
            BillDTO bill = billByService.get(s.getServiceId());
            if (bill != null) {
                billed++;
                billedAmount += bill.getTotalAmount();
            }
            rows.add(List.of(s.getServiceId(), plateOf(s), customerOf(s), serviceOf(s), mechanicOf(s),
                    String.valueOf(s.getDateCompleted()),
                    bill == null ? "-" : money(bill.getTotalAmount())));
        }

        ChartData weekly = weekChart(date, d -> countCompleted(d));
        return new Report(
                "Completed Jobs Report - " + PICK_FORMAT.format(date),
                "Jobs Completed This Week", weekText(date), Fmt.COUNT,
                new String[]{"Completed Jobs", "Billed", "Not Billed", "Billed Amount"},
                new String[]{String.valueOf(jobs.size()), String.valueOf(billed),
                        String.valueOf(jobs.size() - billed), rs(billedAmount)},
                weekly,
                "Jobs completed on " + PICK_FORMAT.format(date),
                List.of("ID", "Number Plate", "Customer", "Service", "Mechanic", "Completed", "Bill Total (Rs.)"),
                rows, "No jobs were completed on this date",
                "Jobs completed on " + PICK_FORMAT.format(date));
    }

    private Report pending() {
        LocalDate today = LocalDate.now();
        List<ServiceRequestDTO> open = new ArrayList<>();
        for (ServiceRequestDTO s : data.services()) {
            if (!ServiceRequestModel.COMPLETED.equals(s.getStatus())) {
                open.add(s);
            }
        }
        open.sort(Comparator.comparing(ServiceRequestDTO::getDateReceived,
                Comparator.nullsLast(Comparator.naturalOrder())));

        int notStarted = 0;
        int inProgress = 0;
        int unassigned = 0;
        Map<String, Integer> perMechanic = new HashMap<>();
        List<List<String>> rows = new ArrayList<>();
        for (ServiceRequestDTO s : open) {
            if (ServiceRequestModel.IN_PROGRESS.equals(s.getStatus())) {
                inProgress++;
            } else {
                notStarted++;
            }
            boolean none = s.getMechanicId() == null || s.getMechanicId().trim().isEmpty();
            if (none) {
                unassigned++;
            } else {
                perMechanic.merge(s.getMechanicId(), 1, Integer::sum);
            }
            long days = s.getDateReceived() == null ? 0 : ChronoUnit.DAYS.between(s.getDateReceived(), today);
            rows.add(List.of(s.getServiceId(), plateOf(s), customerOf(s), serviceOf(s), mechanicOf(s),
                    s.getStatus(), String.valueOf(s.getDateReceived()), String.valueOf(days)));
        }

        // chart: open jobs per mechanic (so you can see who is busy and who is free)
        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (MechanicDTO m : data.mechanics()) {
            labels.add(shorten(m.getName(), 12));
            values.add((double) perMechanic.getOrDefault(m.getMechanicId(), 0));
        }
        labels.add("Unassigned");
        values.add((double) unassigned);

        return new Report(
                "Pending Jobs Report - as at " + PICK_FORMAT.format(today),
                "Open Jobs by Mechanic", "Current workload", Fmt.COUNT,
                new String[]{"Open Jobs", "Pending", "In Progress", "Unassigned"},
                new String[]{String.valueOf(open.size()), String.valueOf(notStarted),
                        String.valueOf(inProgress), String.valueOf(unassigned)},
                new ChartData(labels, values, null),
                "Open jobs (not completed)",
                List.of("ID", "Number Plate", "Customer", "Service", "Mechanic", "Status", "Received", "Days Open"),
                rows, "There are no open jobs",
                "Current open jobs - the date is not used");
    }

    private Report income(LocalDate date) {
        List<PaymentDTO> pays = new ArrayList<>();
        for (PaymentDTO p : data.payments()) {
            if (date.equals(p.getPaymentDate())) {
                pays.add(p);
            }
        }
        pays.sort(Comparator.comparing(PaymentDTO::getPaymentId));

        List<List<String>> rows = new ArrayList<>();
        for (PaymentDTO p : pays) {
            BillDTO bill = billById.get(p.getBillId());
            ServiceRequestDTO s = bill == null ? null : serviceMap.get(bill.getServiceId());
            rows.add(List.of(p.getPaymentId(), p.getBillId(), plateOf(s), customerOf(s),
                    p.getMethod() == null ? "-" : p.getMethod(),
                    String.valueOf(p.getPaymentDate()), money(p.getAmount())));
        }

        LocalDate monday = mondayOf(date);
        double week = sumPayments(monday, monday.plusDays(6));

        double billed = 0;
        for (BillDTO b : data.bills()) {
            billed += b.getTotalAmount();
        }
        double paid = 0;
        for (PaymentDTO p : data.payments()) {
            paid += p.getAmount();
        }
        double outstanding = Math.max(0, billed - paid);

        ChartData weekly = weekChart(date, d -> sumPayments(d, d));
        return new Report(
                "Income Report - " + PICK_FORMAT.format(date),
                "Income This Week", weekText(date), Fmt.MONEY,
                new String[]{"Total Income", "Payments", "This Week", "Outstanding"},
                new String[]{rs(sumPayments(date, date)), String.valueOf(pays.size()), rs(week), rs(outstanding)},
                weekly,
                "Payments received on " + PICK_FORMAT.format(date),
                List.of("Payment", "Bill", "Number Plate", "Customer", "Method", "Date", "Amount (Rs.)"),
                rows, "No payments were received on this date",
                "Payments received on " + PICK_FORMAT.format(date));
    }

    private Report stock() {
        List<SparePartsDTO> parts = new ArrayList<>(data.parts());
        parts.sort(Comparator.comparingInt(SparePartsDTO::getStockQty)
                .thenComparing(SparePartsDTO::getPartName, String.CASE_INSENSITIVE_ORDER));

        int low = 0;
        int out = 0;
        double value = 0;
        List<List<String>> rows = new ArrayList<>();
        for (SparePartsDTO p : parts) {
            String status;
            if (p.getStockQty() <= 0) {
                status = "Out";
                out++;
            } else if (p.getStockQty() <= p.getReorderLevel()) {
                status = "Low";
                low++;
            } else {
                status = "OK";
            }
            double worth = p.getStockQty() * p.getUnitPrice();
            value += worth;
            rows.add(List.of(p.getPartId(), p.getPartName(), String.valueOf(p.getStockQty()),
                    String.valueOf(p.getReorderLevel()), money(p.getUnitPrice()), money(worth), status));
        }

        // chart: the 8 parts that are closest to running out
        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < Math.min(8, parts.size()); i++) {
            labels.add(shorten(parts.get(i).getPartName(), 12));
            values.add((double) parts.get(i).getStockQty());
        }

        return new Report(
                "Stock Report - as at " + PICK_FORMAT.format(LocalDate.now()),
                "Lowest Stock Levels", "Parts closest to running out", Fmt.COUNT,
                new String[]{"Total Parts", "Low Stock", "Out of Stock", "Stock Value"},
                new String[]{String.valueOf(parts.size()), String.valueOf(low), String.valueOf(out), rs(value)},
                new ChartData(labels, values, null),
                "All spare parts (lowest stock first)",
                List.of("ID", "Part Name", "Stock", "Reorder Level", "Unit Price (Rs.)", "Stock Value (Rs.)", "Status"),
                rows, "There are no spare parts",
                "Current stock levels - the date is not used");
    }

    /* ======================= CALCULATION HELPERS ======================= */

    /** part / whole as a percentage, kept between 0 and 100 (0 when there is nothing to divide by). */
    private double percent(double part, double whole) {
        if (whole <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(100, part * 100.0 / whole));
    }

    private String pct(double value) {
        return String.format(Locale.US, "%.0f%%", value);
    }

    private LocalDate mondayOf(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private String weekText(LocalDate date) {
        LocalDate monday = mondayOf(date);
        return "Week of " + SHORT_FORMAT.format(monday) + " – " + PICK_FORMAT.format(monday.plusDays(6));
    }

    /** Seven bars, Monday to Sunday, for the week that contains the date. */
    private ChartData weekChart(LocalDate date, ToDoubleFunction<LocalDate> valueFor) {
        LocalDate monday = mondayOf(date);
        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        List<LocalDate> dates = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate day = monday.plusDays(i);
            labels.add(DAY_NAME.format(day));
            values.add(valueFor.applyAsDouble(day));
            dates.add(day);
        }
        return new ChartData(labels, values, dates);
    }

    private int countReceived(LocalDate day) {
        int n = 0;
        for (ServiceRequestDTO s : data.services()) {
            if (day.equals(s.getDateReceived())) {
                n++;
            }
        }
        return n;
    }

    private int countCompleted(LocalDate day) {
        int n = 0;
        for (ServiceRequestDTO s : data.services()) {
            if (day.equals(s.getDateCompleted())) {
                n++;
            }
        }
        return n;
    }

    /** Sum of payments between two dates, both included. */
    private double sumPayments(LocalDate from, LocalDate to) {
        double total = 0;
        for (PaymentDTO p : data.payments()) {
            LocalDate d = p.getPaymentDate();
            if (d != null && !d.isBefore(from) && !d.isAfter(to)) {
                total += p.getAmount();
            }
        }
        return total;
    }

    private String plateOf(ServiceRequestDTO s) {
        BikeDTO b = s == null ? null : bikeMap.get(s.getBikeId());
        return b == null ? "-" : b.getRegNumber();
    }

    private String customerOf(ServiceRequestDTO s) {
        BikeDTO b = s == null ? null : bikeMap.get(s.getBikeId());
        CustomerDTO c = b == null ? null : customerMap.get(b.getCustomerId());
        return c == null ? "-" : c.getName();
    }

    private String mechanicOf(ServiceRequestDTO s) {
        if (s == null || s.getMechanicId() == null) {
            return "-";
        }
        MechanicDTO m = mechanicMap.get(s.getMechanicId());
        return m == null ? "-" : m.getName();
    }

    /** Service type, or the complaint when the job has no type. */
    private String serviceOf(ServiceRequestDTO s) {
        if (s.getServiceType() != null && !s.getServiceType().trim().isEmpty()) {
            return s.getServiceType();
        }
        return s.getComplaint() == null || s.getComplaint().trim().isEmpty() ? "-" : shorten(s.getComplaint(), 40);
    }

    private String shorten(String text, int max) {
        if (text == null) {
            return "-";
        }
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    private String money(double amount) {
        return String.format(Locale.US, "%,.2f", amount);
    }

    private String rs(double amount) {
        return "Rs. " + money(amount);
    }

    /* ======================= CHART ======================= */
    private void setChart(ChartData c, Fmt fmt) {
        List<String> labels = uniqueLabels(c.labels());
        double max = 0;
        for (double v : c.values()) {
            max = Math.max(max, v);
        }

        chart.getData().clear();                 // old bars out before the categories change
        xAxis.setCategories(FXCollections.observableArrayList(labels));
        configureYAxis(max, fmt);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (int i = 0; i < labels.size(); i++) {
            final String label = labels.get(i);
            final double value = c.values().get(i);
            final LocalDate day = c.dates() == null ? null : c.dates().get(i);

            XYChart.Data<String, Number> point = new XYChart.Data<>(label, value);
            point.nodeProperty().addListener((obs, old, node) -> decorate(node, label, value, day, fmt));
            series.getData().add(point);
        }
        chart.getData().add(series);
    }

    /** Tooltip on every bar, and grey for days that have not happened yet. */
    private void decorate(Node node, String label, double value, LocalDate day, Fmt fmt) {
        if (node == null) {
            return;
        }
        String shown = switch (fmt) {
            case MONEY -> rs(value);
            case PERCENT -> pct(value);
            default -> String.valueOf((long) value);
        };
        String when = day == null ? label : DAY_NAME.format(day) + " " + SHORT_FORMAT.format(day);
        Tooltip.install(node, new Tooltip(when + ": " + shown));

        if (day != null && day.isAfter(LocalDate.now())) {
            node.setStyle("-fx-bar-fill: #4b5563;");
        } else {
            node.setStyle("");
        }
    }

    private void configureYAxis(double max, Fmt fmt) {
        double step;
        double upper;
        if (fmt == Fmt.PERCENT) {
            step = 20;       // 0, 20, 40, 60, 80, 100 %
            upper = 100;
        } else {
            step = niceStep(max / 5.0);
            upper = Math.max(step * 3, Math.ceil(max / step) * step);
        }

        yAxis.setAutoRanging(false);
        yAxis.setLowerBound(0);
        yAxis.setUpperBound(upper);
        yAxis.setTickUnit(step);
        yAxis.setMinorTickVisible(false);
        yAxis.setTickLabelFormatter(new StringConverter<Number>() {
            @Override
            public String toString(Number n) {
                double v = n.doubleValue();
                if (fmt == Fmt.PERCENT) {
                    return (long) v + "%";
                }
                if (fmt == Fmt.MONEY) {
                    return String.format(Locale.US, "%,.0f", v);
                }
                return v == Math.rint(v) ? String.valueOf((long) v) : "";
            }

            @Override
            public Number fromString(String text) {
                return null;
            }
        });
    }

    /** 1, 2, 5, 10, 20, 50 ... so the vertical axis gets tidy numbers. */
    private double niceStep(double raw) {
        if (raw <= 1) {
            return 1;
        }
        double pow = Math.pow(10, Math.floor(Math.log10(raw)));
        double r = raw / pow;
        double nice = r <= 1 ? 1 : r <= 2 ? 2 : r <= 5 ? 5 : 10;
        return nice * pow;
    }

    /** A category axis cannot hold two bars with the same name. */
    private List<String> uniqueLabels(List<String> labels) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String l : labels) {
            String base = (l == null || l.trim().isEmpty()) ? "-" : l;
            String candidate = base;
            int n = 2;
            while (!seen.add(candidate)) {
                candidate = base + " (" + n + ")";
                n++;
            }
            out.add(candidate);
        }
        return out;
    }

    /* ======================= TABLE ======================= */
    private void setTable(Report r) {
        tblReport.getColumns().clear();
        for (int i = 0; i < r.headers().size(); i++) {
            final int index = i;
            String header = r.headers().get(i);

            TableColumn<List<String>, String> col = new TableColumn<>(header);
            col.setSortable(false);
            col.setPrefWidth(widthFor(header));
            col.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                    index < c.getValue().size() ? c.getValue().get(index) : ""));
            if (header.endsWith("(Rs.)")) {
                col.setStyle("-fx-alignment: CENTER-RIGHT;");
            }
            if ("Status".equals(header)) {
                col.setCellFactory(c -> statusCell());
            }
            tblReport.getColumns().add(col);
        }
        tblReport.setPlaceholder(new Label(r.emptyText()));
        tblReport.setItems(FXCollections.observableArrayList(r.rows()));
    }

    private double widthFor(String header) {
        return switch (header) {
            case "ID", "Bill", "Payment", "Stock", "Days Open", "Level" -> 70;
            case "Number Plate", "Report" -> 110;
            case "Service", "Customer", "Part Name" -> 130;
            case "Details" -> 320;
            default -> 95;
        };
    }

    /** Coloured badge for job status (Pending / In Progress / Completed) and stock status (OK / Low / Out). */
    private TableCell<List<String>, String> statusCell() {
        return new TableCell<List<String>, String>() {
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
                    case "Pending", "Low" -> badge.getStyleClass().add("badge-pending");
                    case "Completed", "OK" -> badge.getStyleClass().add("badge-completed");
                    case "Out" -> badge.getStyleClass().add("badge-out");
                    default -> { }
                }
                setGraphic(badge);
                setText(null);
            }
        };
    }

    /* ======================= GENERATE REPORT (save as a PDF file) ======================= */
    @FXML
    private void onGenerate(ActionEvent e) {
        hideMessage();
        btnGenerate.setDisable(true);
        loadAll(this::exportPdf);    // reload fresh data first, then save
    }

    private void exportPdf() {
        if (current == null) {
            return;
        }
        Report r = current;
        LocalDate date = dpDate.getValue() == null ? LocalDate.now() : dpDate.getValue();

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save report as PDF");
        chooser.setInitialFileName("bike-garage-" + tab.name().toLowerCase() + "-report-" + date + ".pdf");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF file (*.pdf)", "*.pdf"));
        File home = new File(System.getProperty("user.home"));
        if (home.isDirectory()) {
            chooser.setInitialDirectory(home);
        }

        File file = chooser.showSaveDialog(root.getScene().getWindow());
        if (file == null) {
            return;   // cancelled
        }
        if (!file.getName().toLowerCase().endsWith(".pdf")) {
            file = new File(file.getParentFile(), file.getName() + ".pdf");
        }

        try {
            buildPdf(r, file);
            showMessage("Report saved: " + file.getAbsolutePath(), false);
            openInViewer(file);
        } catch (Exception ex) {
            showMessage("Could not save the report: " + ex.getMessage(), true);
        }
    }

    /** Opens the saved PDF in the computer's default PDF viewer. */
    private void openInViewer(File file) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file);
            }
        } catch (Exception ignored) {
            // the file is already saved; not being able to open a viewer is not an error
        }
    }

    private static final Color PDF_RED = new Color(225, 29, 29);
    private static final Color PDF_GREY = new Color(110, 110, 118);
    private static final Color PDF_LINE = new Color(215, 215, 220);
    private static final Color PDF_ZEBRA = new Color(246, 246, 248);

    private void buildPdf(Report r, File file) throws Exception {
        Font brandFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, PDF_RED);
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Color.BLACK);
        Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 9, PDF_GREY);
        Font cardTitleFont = FontFactory.getFont(FontFactory.HELVETICA, 9, PDF_GREY);
        Font cardValueFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Color.BLACK);
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.BLACK);
        Font headFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);

        Document doc = new Document(PageSize.A4.rotate(), 30, 30, 30, 30);   // landscape: tables are wide
        FileOutputStream out = new FileOutputStream(file);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            // ---- heading ----
            doc.add(new Paragraph("Bike Garage Management System", brandFont));
            Paragraph title = new Paragraph(r.title(), titleFont);
            title.setSpacingBefore(2);
            doc.add(title);
            Paragraph meta = new Paragraph("Generated: " + STAMP.format(LocalDateTime.now())
                    + "      Scope: " + r.scope(), metaFont);
            meta.setSpacingAfter(12);
            doc.add(meta);

            // ---- summary cards (4 boxes in one row) ----
            PdfPTable cards = new PdfPTable(4);
            cards.setWidthPercentage(100);
            cards.setSpacingAfter(14);
            for (int i = 0; i < r.cardTitles().length; i++) {
                PdfPCell card = new PdfPCell();
                card.setPadding(8);
                card.setBorderColor(PDF_LINE);
                card.addElement(new Paragraph(r.cardTitles()[i], cardTitleFont));
                card.addElement(new Paragraph(r.cardValues()[i], cardValueFont));
                cards.addCell(card);
            }
            doc.add(cards);

            // ---- details table ----
            Paragraph section = new Paragraph(r.tableTitle() + "  (" + r.rows().size()
                    + (r.rows().size() == 1 ? " row)" : " rows)"), sectionFont);
            section.setSpacingAfter(6);
            doc.add(section);

            if (r.rows().isEmpty()) {
                doc.add(new Paragraph(r.emptyText(), metaFont));
            } else {
                int cols = r.headers().size();
                PdfPTable table = new PdfPTable(cols);
                table.setWidthPercentage(100);
                table.setHeaderRows(1);                 // header repeats on every page

                float[] widths = new float[cols];
                for (int i = 0; i < cols; i++) {
                    widths[i] = (float) widthFor(r.headers().get(i));
                }
                table.setWidths(widths);

                for (String h : r.headers()) {
                    int align = h.endsWith("(Rs.)") ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT;
                    table.addCell(pdfCell(h, headFont, PDF_RED, align));
                }
                int n = 0;
                for (List<String> row : r.rows()) {
                    Color bg = (n++ % 2 == 0) ? Color.WHITE : PDF_ZEBRA;
                    for (int i = 0; i < cols; i++) {
                        String text = i < row.size() ? row.get(i) : "";
                        int align = r.headers().get(i).endsWith("(Rs.)") ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT;
                        table.addCell(pdfCell(text, bodyFont, bg, align));
                    }
                }
                doc.add(table);
            }
        } finally {
            if (doc.isOpen()) {
                doc.close();    // also closes the file stream
            }
        }
    }

    private PdfPCell pdfCell(String text, Font font, Color background, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
        cell.setBackgroundColor(background);
        cell.setHorizontalAlignment(align);
        cell.setPadding(5);
        cell.setBorderColor(PDF_LINE);
        return cell;
    }

    /* ======================= MESSAGES ======================= */
    private void showMessage(String text, boolean error) {
        lblMessage.getStyleClass().removeAll("form-error", "rep-ok");
        lblMessage.getStyleClass().add(error ? "form-error" : "rep-ok");
        lblMessage.setText(text);
        lblMessage.setVisible(true);
        lblMessage.setManaged(true);
    }

    private void hideMessage() {
        lblMessage.setVisible(false);
        lblMessage.setManaged(false);
    }
}