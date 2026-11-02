package lk.ijse.firstsemfinalproject25.model;

import lk.ijse.firstsemfinalproject25.dto.ServiceRequestDTO;
import lk.ijse.firstsemfinalproject25.util.CrudUtil;
import lk.ijse.firstsemfinalproject25.util.IdGenerator;

import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ServiceRequestModel {

    public static final String PENDING = "Pending";
    public static final String IN_PROGRESS = "In Progress";
    public static final String COMPLETED = "Completed";
    public static final List<String> STATUSES = List.of(PENDING, IN_PROGRESS, COMPLETED);

    public String getNextId() throws Exception {
        return IdGenerator.nextId("service_request", "service_id", "S");
    }

    public ArrayList<String> getAllIds() throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT service_id FROM service_request ORDER BY service_id");
        ArrayList<String> ids = new ArrayList<>();
        while (rs.next()) {
            ids.add(rs.getString(1));
        }
        return ids;
    }

    public ArrayList<ServiceRequestDTO> getAll() throws Exception {
        return query("SELECT * FROM service_request ORDER BY date_received DESC, service_id DESC");
    }

    public ServiceRequestDTO search(String serviceId) throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT * FROM service_request WHERE service_id=?", serviceId);
        return rs.next() ? map(rs) : null;
    }


    public boolean save(ServiceRequestDTO dto) throws Exception {
        validate(dto);
        LocalDate received = dto.getDateReceived() != null ? dto.getDateReceived() : LocalDate.now();
        return CrudUtil.execute(
                "INSERT INTO service_request (service_id, bike_id, mechanic_id, complaint, status, date_received, date_completed) "
                        + "VALUES (?,?,?,?,?,?,?)",
                dto.getServiceId(), dto.getBikeId(), dto.getMechanicId(), dto.getComplaint().trim(),
                PENDING, received, null);
    }


    public boolean update(ServiceRequestDTO dto) throws Exception {
        validate(dto);
        requireEditable(dto.getServiceId());
        return CrudUtil.execute(
                "UPDATE service_request SET bike_id=?, mechanic_id=?, complaint=? WHERE service_id=?",
                dto.getBikeId(), dto.getMechanicId(), dto.getComplaint().trim(), dto.getServiceId());
    }


    public boolean delete(String serviceId) throws Exception {
        ServiceRequestDTO sr = requireEditable(serviceId);
        if (!PENDING.equals(sr.getStatus())) {
            throw new Exception("Only Pending jobs can be deleted.");
        }
        ResultSet rs = CrudUtil.execute("SELECT part_id FROM service_parts WHERE service_id=?", serviceId);
        if (rs.next()) {
            throw new Exception("Remove the recorded spare parts first.");
        }
        return CrudUtil.execute("DELETE FROM service_request WHERE service_id=?", serviceId);
    }


    public boolean assignMechanic(String serviceId, String mechanicId) throws Exception {
        ServiceRequestDTO sr = requireEditable(serviceId);
        if (COMPLETED.equals(sr.getStatus())) {
            throw new Exception("A completed job cannot be re-assigned.");
        }
        return CrudUtil.execute("UPDATE service_request SET mechanic_id=? WHERE service_id=?", mechanicId, serviceId);
    }


    public boolean updateStatus(String serviceId, String status) throws Exception {
        if (!STATUSES.contains(status)) {
            throw new Exception("Invalid status: " + status);
        }
        ServiceRequestDTO sr = requireEditable(serviceId);
        if (!PENDING.equals(status) && (sr.getMechanicId() == null || sr.getMechanicId().isBlank())) {
            throw new Exception("Assign a mechanic before starting the job.");
        }
        LocalDate completed = COMPLETED.equals(status) ? LocalDate.now() : null;
        return CrudUtil.execute(
                "UPDATE service_request SET status=?, date_completed=? WHERE service_id=?",
                status, completed, serviceId);
    }



    public boolean updateStatusByMechanic(String serviceId, String mechanicId, String status) throws Exception {
        ServiceRequestDTO sr = search(serviceId);
        if (sr == null || !mechanicId.equals(sr.getMechanicId())) {
            throw new Exception("This job is not assigned to you.");
        }
        return updateStatus(serviceId, status);
    }


    public ArrayList<ServiceRequestDTO> getByMechanic(String mechanicId) throws Exception {
        return query("SELECT * FROM service_request WHERE mechanic_id=? "
                + "ORDER BY (status='Completed'), date_received DESC", mechanicId);
    }


    public ArrayList<ServiceRequestDTO> getByStatus(String status) throws Exception {
        return query("SELECT * FROM service_request WHERE status=? ORDER BY date_received DESC", status);
    }


    public ArrayList<ServiceRequestDTO> getByDateReceived(LocalDate date) throws Exception {
        return query("SELECT * FROM service_request WHERE date_received=? ORDER BY service_id", date);
    }


    public ArrayList<ServiceRequestDTO> getHistoryByBike(String bikeId) throws Exception {
        return query("SELECT * FROM service_request WHERE bike_id=? ORDER BY date_received DESC, service_id DESC", bikeId);
    }


    public ArrayList<ServiceRequestDTO> getCompletedWithoutBill() throws Exception {
        return query("SELECT * FROM service_request s WHERE s.status='Completed' "
                + "AND NOT EXISTS (SELECT 1 FROM bill b WHERE b.service_id = s.service_id) ORDER BY s.date_completed");
    }

    public boolean hasBill(String serviceId) throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT bill_id FROM bill WHERE service_id=?", serviceId);
        return rs.next();
    }

    private ServiceRequestDTO requireEditable(String serviceId) throws Exception {
        ServiceRequestDTO sr = search(serviceId);
        if (sr == null) {
            throw new Exception("Service request not found.");
        }
        if (hasBill(serviceId)) {
            throw new Exception("This job is already billed and cannot be changed.");
        }
        return sr;
    }

    private void validate(ServiceRequestDTO dto) throws Exception {
        if (dto.getBikeId() == null || dto.getBikeId().isBlank()) {
            throw new Exception("Select the motorcycle.");
        }
        if (dto.getComplaint() == null || dto.getComplaint().isBlank()) {
            throw new Exception("Enter the customer's complaint.");
        }
    }

    private ArrayList<ServiceRequestDTO> query(String sql, Object... params) throws Exception {
        ResultSet rs = CrudUtil.execute(sql, params);
        ArrayList<ServiceRequestDTO> list = new ArrayList<>();
        while (rs.next()) {
            list.add(map(rs));
        }
        return list;
    }

    private ServiceRequestDTO map(ResultSet rs) throws Exception {
        return new ServiceRequestDTO(
                rs.getString("service_id"),
                rs.getString("bike_id"),
                rs.getString("mechanic_id"),
                rs.getString("complaint"),
                rs.getString("status"),
                rs.getObject("date_received", LocalDate.class),
                rs.getObject("date_completed", LocalDate.class));
    }
}
