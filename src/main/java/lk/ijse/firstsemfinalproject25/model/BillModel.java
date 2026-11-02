package lk.ijse.firstsemfinalproject25.model;

import lk.ijse.firstsemfinalproject25.dto.BillDTO;
import lk.ijse.firstsemfinalproject25.dto.ServiceRequestDTO;
import lk.ijse.firstsemfinalproject25.util.CrudUtil;
import lk.ijse.firstsemfinalproject25.util.IdGenerator;

import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;

public class BillModel {

    private final ServiceRequestModel serviceRequestModel = new ServiceRequestModel();
    private final ServicePartsModel servicePartsModel = new ServicePartsModel();

    public String getNextId() throws Exception {
        return IdGenerator.nextId("bill", "bill_id", "BL");
    }

    public ArrayList<String> getAllIds() throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT bill_id FROM bill ORDER BY bill_id");
        ArrayList<String> ids = new ArrayList<>();
        while (rs.next()) {
            ids.add(rs.getString(1));
        }
        return ids;
    }

    public ArrayList<BillDTO> getAll() throws Exception {
        return query("SELECT * FROM bill ORDER BY bill_date DESC, bill_id DESC");
    }

    public BillDTO search(String billId) throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT * FROM bill WHERE bill_id=?", billId);
        return rs.next() ? map(rs) : null;
    }

    public BillDTO searchByService(String serviceId) throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT * FROM bill WHERE service_id=?", serviceId);
        return rs.next() ? map(rs) : null;
    }


    public ArrayList<BillDTO> getUnpaid() throws Exception {
        return query("SELECT * FROM bill b WHERE b.total_amount > "
                + "IFNULL((SELECT SUM(p.amount) FROM payment p WHERE p.bill_id = b.bill_id), 0) ORDER BY b.bill_date");
    }


    public double calculateTotal(String serviceId, double labourCharge) throws Exception {
        return round(labourCharge + servicePartsModel.getPartsTotal(serviceId));
    }


    public double getBalance(String billId) throws Exception {
        ResultSet rs = CrudUtil.execute(
                "SELECT b.total_amount - IFNULL((SELECT SUM(p.amount) FROM payment p WHERE p.bill_id = b.bill_id), 0) "
                        + "FROM bill b WHERE b.bill_id=?", billId);
        if (!rs.next()) {
            throw new Exception("Bill not found.");
        }
        return round(rs.getDouble(1));
    }


    public boolean save(BillDTO dto) throws Exception {
        if (dto.getLabourCharge() < 0) {
            throw new Exception("Labour charge cannot be negative.");
        }
        ServiceRequestDTO sr = serviceRequestModel.search(dto.getServiceId());
        if (sr == null) {
            throw new Exception("Service request not found.");
        }
        if (!ServiceRequestModel.COMPLETED.equals(sr.getStatus())) {
            throw new Exception("Only completed jobs can be billed.");
        }
        if (serviceRequestModel.hasBill(dto.getServiceId())) {
            throw new Exception("This job already has a bill.");
        }
        double labour = round(dto.getLabourCharge());
        double parts = round(servicePartsModel.getPartsTotal(dto.getServiceId()));
        LocalDate date = dto.getBillDate() != null ? dto.getBillDate() : LocalDate.now();

        return CrudUtil.execute(
                "INSERT INTO bill (bill_id, service_id, bill_date, labour_charge, parts_total, total_amount) VALUES (?,?,?,?,?,?)",
                dto.getBillId(), dto.getServiceId(), date, labour, parts, round(labour + parts));
    }


    public boolean update(BillDTO dto) throws Exception {
        if (dto.getLabourCharge() < 0) {
            throw new Exception("Labour charge cannot be negative.");
        }
        BillDTO old = search(dto.getBillId());
        if (old == null) {
            throw new Exception("Bill not found.");
        }
        requireNoPayments(dto.getBillId());
        double labour = round(dto.getLabourCharge());
        double parts = round(servicePartsModel.getPartsTotal(old.getServiceId()));
        return CrudUtil.execute(
                "UPDATE bill SET labour_charge=?, parts_total=?, total_amount=? WHERE bill_id=?",
                labour, parts, round(labour + parts), dto.getBillId());
    }


    public boolean delete(String billId) throws Exception {
        requireNoPayments(billId);
        return CrudUtil.execute("DELETE FROM bill WHERE bill_id=?", billId);
    }

    private void requireNoPayments(String billId) throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT payment_id FROM payment WHERE bill_id=?", billId);
        if (rs.next()) {
            throw new Exception("This bill already has payments and cannot be changed.");
        }
    }

    private ArrayList<BillDTO> query(String sql, Object... params) throws Exception {
        ResultSet rs = CrudUtil.execute(sql, params);
        ArrayList<BillDTO> list = new ArrayList<>();
        while (rs.next()) {
            list.add(map(rs));
        }
        return list;
    }

    static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private BillDTO map(ResultSet rs) throws Exception {
        return new BillDTO(
                rs.getString("bill_id"),
                rs.getString("service_id"),
                rs.getObject("bill_date", LocalDate.class),
                rs.getDouble("labour_charge"),
                rs.getDouble("parts_total"),
                rs.getDouble("total_amount"));
    }
}
