package lk.ijse.firstsemfinalproject25.model;

import lk.ijse.firstsemfinalproject25.db.DBConnection;
import lk.ijse.firstsemfinalproject25.dto.ServicePartsDTO;
import lk.ijse.firstsemfinalproject25.dto.ServiceRequestDTO;
import lk.ijse.firstsemfinalproject25.dto.SparePartsDTO;
import lk.ijse.firstsemfinalproject25.util.CrudUtil;
import lk.ijse.firstsemfinalproject25.util.Session;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;

/**
 * Spare parts used in a service job.
 *
 * RULE: every change here moves the spare-part stock at the same time, inside ONE transaction:
 *   add a part to a job        -> stock goes DOWN
 *   increase the quantity      -> stock goes DOWN by the difference
 *   decrease the quantity      -> stock goes UP   by the difference
 *   remove a part / cancel job -> stock goes UP   (given back)
 * If any step fails, everything is rolled back, so stock and job can never disagree.
 */
public class ServicePartsModel {

    private final SparePartsModel sparePartModel = new SparePartsModel();
    private final ServiceRequestModel serviceRequestModel = new ServiceRequestModel();

    /* ===================== read ===================== */

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

    public double getPartsTotal(String serviceId) throws Exception {
        ResultSet rs = CrudUtil.execute(
                "SELECT IFNULL(SUM(quantity * unit_price), 0) FROM service_parts WHERE service_id=?", serviceId);
        return rs.next() ? rs.getDouble(1) : 0;
    }

    /* ===================== use a part (stock DOWN) ===================== */

    /**
     * Adds a part to a service job and reduces the spare-part stock by the same quantity.
     * If the part is already on the job, the quantity is added to it.
     * The unit price is always taken from the spare part (dto.getUnitPrice() is ignored).
     */
    public boolean save(ServicePartsDTO dto) throws Exception {
        if (dto.getQuantity() <= 0) {
            throw new Exception("Quantity must be greater than zero.");
        }
        requireOpenJob(dto.getServiceId());
        SparePartsDTO part = sparePartModel.search(dto.getPartId());
        if (part == null) {
            throw new Exception("Spare part not found.");
        }

        return inTransaction(() -> {
            // 1) take the quantity out of stock
            if (!sparePartModel.reduceStock(dto.getPartId(), dto.getQuantity())) {
                SparePartsDTO fresh = sparePartModel.search(dto.getPartId());
                int available = fresh == null ? 0 : fresh.getStockQty();
                throw new Exception("Not enough stock for " + part.getPartName()
                        + ". Available: " + available);
            }

            // 2) record it on the job
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
                throw new Exception("Could not save the part for this job.");
            }
            return true;
        });
    }

    /* ===================== change the quantity (stock UP or DOWN) ===================== */

    public boolean update(String serviceId, String partId, int newQty) throws Exception {
        Session.requireSuperAdmin();                       // only the Super Admin can change a recorded part
        if (newQty <= 0) {
            throw new Exception("Quantity must be greater than zero. Use delete to remove the part.");
        }
        requireOpenJob(serviceId);
        ServicePartsDTO old = search(serviceId, partId);
        if (old == null) {
            throw new Exception("This part is not recorded for the job.");
        }
        int diff = newQty - old.getQuantity();     // + = needs more stock, - = gives stock back

        return inTransaction(() -> {
            if (diff > 0 && !sparePartModel.reduceStock(partId, diff)) {
                throw new Exception("Not enough stock to increase the quantity.");
            }
            if (diff < 0 && !sparePartModel.addStock(partId, -diff)) {
                throw new Exception("Could not return the extra stock.");
            }

            boolean updated = CrudUtil.execute(
                    "UPDATE service_parts SET quantity=? WHERE service_id=? AND part_id=?",
                    newQty, serviceId, partId);
            if (!updated && diff != 0) {
                throw new Exception("Could not update the quantity.");
            }
            return true;
        });
    }

    /* ===================== remove a part (stock UP) ===================== */

    public boolean delete(String serviceId, String partId) throws Exception {
        Session.requireSuperAdmin();                       // only the Super Admin can remove a recorded part
        requireOpenJob(serviceId);
        ServicePartsDTO old = search(serviceId, partId);
        if (old == null) {
            throw new Exception("This part is not recorded for the job.");
        }

        return inTransaction(() -> {
            boolean deleted = CrudUtil.execute(
                    "DELETE FROM service_parts WHERE service_id=? AND part_id=?", serviceId, partId);
            if (!deleted) {
                throw new Exception("Could not remove the part.");
            }
            if (!sparePartModel.addStock(partId, old.getQuantity())) {
                throw new Exception("Could not return the stock.");
            }
            return true;
        });
    }

    /**
     * Gives back ALL parts of a job to stock and removes them from the job.
     * Call this BEFORE you delete / cancel an open service request.
     */
    public boolean deleteByService(String serviceId) throws Exception {
        Session.requireSuperAdmin();
        requireOpenJob(serviceId);
        ArrayList<ServicePartsDTO> used = getByService(serviceId);
        if (used.isEmpty()) {
            return true;
        }

        return inTransaction(() -> {
            for (ServicePartsDTO p : used) {
                if (!sparePartModel.addStock(p.getPartId(), p.getQuantity())) {
                    throw new Exception("Could not return stock for part " + p.getPartId() + ".");
                }
            }
            boolean deleted = CrudUtil.execute("DELETE FROM service_parts WHERE service_id=?", serviceId);
            if (!deleted) {
                throw new Exception("Could not remove the parts from the job.");
            }
            return true;
        });
    }

    /* ===================== helpers ===================== */

    @FunctionalInterface
    private interface TxAction<T> {
        T run() throws Exception;
    }

    /** Runs the action in one transaction: commit when it works, rollback when anything throws. */
    private <T> T inTransaction(TxAction<T> action) throws Exception {
        Connection con = DBConnection.getInstance().getConnection();
        try {
            con.setAutoCommit(false);
            T result = action.run();
            con.commit();
            return result;
        } catch (Exception e) {
            con.rollback();
            throw e;
        } finally {
            con.setAutoCommit(true);
        }
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
