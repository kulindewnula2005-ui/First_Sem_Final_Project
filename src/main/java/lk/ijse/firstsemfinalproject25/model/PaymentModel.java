package lk.ijse.firstsemfinalproject25.model;

import lk.ijse.firstsemfinalproject25.dto.PaymentDTO;
import lk.ijse.firstsemfinalproject25.util.CrudUtil;
import lk.ijse.firstsemfinalproject25.util.IdGenerator;

import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;

public class PaymentModel {

    private final BillModel billModel = new BillModel();

    public String getNextId() throws Exception {
        return IdGenerator.nextId("payment", "payment_id", "PY");
    }

    public ArrayList<String> getAllIds() throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT payment_id FROM payment ORDER BY payment_id");
        ArrayList<String> ids = new ArrayList<>();
        while (rs.next()) {
            ids.add(rs.getString(1));
        }
        return ids;
    }

    public ArrayList<PaymentDTO> getAll() throws Exception {
        return query("SELECT * FROM payment ORDER BY payment_date DESC, payment_id DESC");
    }

    public PaymentDTO search(String paymentId) throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT * FROM payment WHERE payment_id=?", paymentId);
        return rs.next() ? map(rs) : null;
    }

    public ArrayList<PaymentDTO> getByBill(String billId) throws Exception {
        return query("SELECT * FROM payment WHERE bill_id=? ORDER BY payment_date, payment_id", billId);
    }

    public double getTotalPaid(String billId) throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT IFNULL(SUM(amount), 0) FROM payment WHERE bill_id=?", billId);
        return rs.next() ? rs.getDouble(1) : 0;
    }


    public ArrayList<PaymentDTO> getPaymentsBetween(LocalDate from, LocalDate to) throws Exception {
        return query("SELECT * FROM payment WHERE payment_date BETWEEN ? AND ? ORDER BY payment_date, payment_id", from, to);
    }

    public double getIncomeBetween(LocalDate from, LocalDate to) throws Exception {
        ResultSet rs = CrudUtil.execute(
                "SELECT IFNULL(SUM(amount), 0) FROM payment WHERE payment_date BETWEEN ? AND ?", from, to);
        return rs.next() ? rs.getDouble(1) : 0;
    }

    public double getDailyIncome(LocalDate date) throws Exception {
        return getIncomeBetween(date, date);
    }


    public boolean save(PaymentDTO dto) throws Exception {
        if (dto.getBillId() == null || billModel.search(dto.getBillId()) == null) {
            throw new Exception("Bill not found.");
        }
        validate(dto);
        double balance = billModel.getBalance(dto.getBillId());
        if (balance <= 0) {
            throw new Exception("This bill is already fully paid.");
        }
        if (dto.getAmount() > balance + 0.005) {
            throw new Exception("Amount exceeds the remaining balance of " + balance);
        }
        LocalDate date = dto.getPaymentDate() != null ? dto.getPaymentDate() : LocalDate.now();
        return CrudUtil.execute(
                "INSERT INTO payment (payment_id, bill_id, method, payment_date, amount) VALUES (?,?,?,?,?)",
                dto.getPaymentId(), dto.getBillId(), dto.getMethod(), date, BillModel.round(dto.getAmount()));
    }


    public boolean update(PaymentDTO dto) throws Exception {
        PaymentDTO old = search(dto.getPaymentId());
        if (old == null) {
            throw new Exception("Payment not found.");
        }
        validate(dto);
        double balanceIfRemoved = billModel.getBalance(old.getBillId()) + old.getAmount();
        if (dto.getAmount() > balanceIfRemoved + 0.005) {
            throw new Exception("Amount exceeds the bill total. Maximum allowed: " + balanceIfRemoved);
        }
        LocalDate date = dto.getPaymentDate() != null ? dto.getPaymentDate() : old.getPaymentDate();
        return CrudUtil.execute(
                "UPDATE payment SET method=?, payment_date=?, amount=? WHERE payment_id=?",
                dto.getMethod(), date, BillModel.round(dto.getAmount()), dto.getPaymentId());
    }

    public boolean delete(String paymentId) throws Exception {
        return CrudUtil.execute("DELETE FROM payment WHERE payment_id=?", paymentId);
    }

    private void validate(PaymentDTO dto) throws Exception {
        if (dto.getMethod() == null || dto.getMethod().isBlank()) {
            throw new Exception("Select a payment method.");
        }
        if (dto.getAmount() <= 0) {
            throw new Exception("Payment amount must be greater than zero.");
        }
    }

    private ArrayList<PaymentDTO> query(String sql, Object... params) throws Exception {
        ResultSet rs = CrudUtil.execute(sql, params);
        ArrayList<PaymentDTO> list = new ArrayList<>();
        while (rs.next()) {
            list.add(map(rs));
        }
        return list;
    }

    private PaymentDTO map(ResultSet rs) throws Exception {
        return new PaymentDTO(
                rs.getString("payment_id"),
                rs.getString("bill_id"),
                rs.getString("method"),
                rs.getObject("payment_date", LocalDate.class),
                rs.getDouble("amount"));
    }

}
