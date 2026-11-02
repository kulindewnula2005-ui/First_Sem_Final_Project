package lk.ijse.firstsemfinalproject25.model;

import lk.ijse.firstsemfinalproject25.db.DBConnection;
import lk.ijse.firstsemfinalproject25.dto.ServicePartsDTO;
import lk.ijse.firstsemfinalproject25.dto.ServiceRequestDTO;
import lk.ijse.firstsemfinalproject25.dto.SparePartsDTO;
import lk.ijse.firstsemfinalproject25.util.CrudUtil;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;

public class ServicePartsModel {

    private final SparePartsModel sparePartModel = new SparePartsModel();
    private final ServiceRequestModel serviceRequestModel = new ServiceRequestModel();

    public ArrayList<ServicePartsDTO> getAll() throws Exception {
        return query("SELECT * FROM service_parts ORDER BY service_id, part_id");
    }

    public ArrayList<ServicePartsDTO> getByService(String serviceId) throws Exception {
        return query("SELECT * FROM service_parts WHERE service_id=? ORDER BY part_id", serviceId);
    }

    public ServicePartsDTO search(String serviceId, String partId) throws Exception {
        ResultSet rs = CrudUtil.execute(
                "SELECT * FROM service_parts WHERE service_id=? AND part_id=?", serviceId, partId);
        return rs.next() ? map(rs) : null;
    }

    public boolean save(ServicePartsDTO dto) throws Exception {
        if (dto.getQuantity() <= 0) {
            throw new Exception("Quantity must be greater than zero.");
        }
        requireOpenJob(dto.getServiceId());
        SparePartsDTO part = sparePartModel.search(dto.getPartId());
        if (part == null) {
            throw new Exception("Spare part not found.");
        }

        Connection con = DBConnection.getInstance().getConnection();
        try {
            con.setAutoCommit(false);

            if (!sparePartModel.reduceStock(dto.getPartId(), dto.getQuantity())) {
                con.rollback();
                throw new Exception("Not enough stock for " + part.getPartName()
                        + ". Available: " + part.getStockQty());
            }

            boolean saved;
            if (search(dto.getServiceId(), dto.getPartId()) != null) {
                saved = CrudUtil.execute(
                        "UPDATE service_parts SET quantity = quantity + ? WHERE service_id=? AND part_id=?",
                        dto.getQuantity(), dto.getServiceId(), dto.getPartId());
            } else {
                saved = CrudUtil.execute(
                        "INSERT INTO service_parts (service_id, part_id, quantity, unit_price) VALUES (?,?,?,?)",
                        dto.getServiceId(), dto.getPartId(), dto.getQuantity(), part.getUnitPrice());
            }

            if (!saved) {
                con.rollback();
                return false;
            }
            con.commit();
            return true;
        } catch (Exception e) {
            con.rollback();
            throw e;
        } finally {
            con.setAutoCommit(true);
        }
    }


    public boolean update(String serviceId, String partId, int newQty) throws Exception {
        if (newQty <= 0) {
            throw new Exception("Quantity must be greater than zero. Use delete to remove the part.");
        }
        requireOpenJob(serviceId);
        ServicePartsDTO old = search(serviceId, partId);
        if (old == null) {
            throw new Exception("This part is not recorded for the job.");
        }
        int diff = newQty - old.getQuantity();

        Connection con = DBConnection.getInstance().getConnection();
        try {
            con.setAutoCommit(false);

            boolean stockOk = true;
            if (diff > 0) {
                stockOk = sparePartModel.reduceStock(partId, diff);
            } else if (diff < 0) {
                stockOk = sparePartModel.addStock(partId, -diff);
            }
            if (!stockOk) {
                con.rollback();
                throw new Exception("Not enough stock to increase the quantity.");
            }

            boolean updated = CrudUtil.execute(
                    "UPDATE service_parts SET quantity=? WHERE service_id=? AND part_id=?",
                    newQty, serviceId, partId);
            if (!updated) {
                con.rollback();
                return false;
            }
            con.commit();
            return true;
        } catch (Exception e) {
            con.rollback();
            throw e;
        } finally {
            con.setAutoCommit(true);
        }
    }


    public boolean delete(String serviceId, String partId) throws Exception {
        requireOpenJob(serviceId);
        ServicePartsDTO old = search(serviceId, partId);
        if (old == null) {
            throw new Exception("This part is not recorded for the job.");
        }

        Connection con = DBConnection.getInstance().getConnection();
        try {
            con.setAutoCommit(false);
            boolean deleted = CrudUtil.execute(
                    "DELETE FROM service_parts WHERE service_id=? AND part_id=?", serviceId, partId);
            boolean restored = sparePartModel.addStock(partId, old.getQuantity());
            if (deleted && restored) {
                con.commit();
                return true;
            }
            con.rollback();
            return false;
        } catch (Exception e) {
            con.rollback();
            throw e;
        } finally {
            con.setAutoCommit(true);
        }
    }


    public double getPartsTotal(String serviceId) throws Exception {
        ResultSet rs = CrudUtil.execute(
                "SELECT IFNULL(SUM(quantity * unit_price), 0) FROM service_parts WHERE service_id=?", serviceId);
        return rs.next() ? rs.getDouble(1) : 0;
    }

    private void requireOpenJob(String serviceId) throws Exception {
        ServiceRequestDTO sr = serviceRequestModel.search(serviceId);
        if (sr == null) {
            throw new Exception("Service request not found.");
        }
        if (ServiceRequestModel.COMPLETED.equals(sr.getStatus()) || serviceRequestModel.hasBill(serviceId)) {
            throw new Exception("Parts cannot be changed on a completed or billed job.");
        }
    }

    private ArrayList<ServicePartsDTO> query(String sql, Object... params) throws Exception {
        ResultSet rs = CrudUtil.execute(sql, params);
        ArrayList<ServicePartsDTO> list = new ArrayList<>();
        while (rs.next()) {
            list.add(map(rs));
        }
        return list;
    }

    private ServicePartsDTO map(ResultSet rs) throws Exception {
        return new ServicePartsDTO(
                rs.getString("service_id"),
                rs.getString("part_id"),
                rs.getInt("quantity"),
                rs.getDouble("unit_price"));
    }

}
