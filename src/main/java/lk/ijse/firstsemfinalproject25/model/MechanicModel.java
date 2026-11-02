package lk.ijse.firstsemfinalproject25.model;

import lk.ijse.firstsemfinalproject25.dto.MechanicDTO;
import lk.ijse.firstsemfinalproject25.util.CrudUtil;
import lk.ijse.firstsemfinalproject25.util.IdGenerator;

import java.sql.ResultSet;
import java.util.ArrayList;

public class MechanicModel {

    public String getNextId() throws Exception {
        return IdGenerator.nextId("mechanic", "mechanic_id", "M");
    }

    public ArrayList<String> getAllIds() throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT mechanic_id FROM mechanic ORDER BY mechanic_id");
        ArrayList<String> ids = new ArrayList<>();
        while (rs.next()) {
            ids.add(rs.getString(1));
        }
        return ids;
    }

    public ArrayList<MechanicDTO> getAll() throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT * FROM mechanic ORDER BY mechanic_id");
        ArrayList<MechanicDTO> list = new ArrayList<>();
        while (rs.next()) {
            list.add(map(rs));
        }
        return list;
    }

    public MechanicDTO search(String mechanicId) throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT * FROM mechanic WHERE mechanic_id=?", mechanicId);
        return rs.next() ? map(rs) : null;
    }


    public MechanicDTO searchByUserId(String userId) throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT * FROM mechanic WHERE user_id=?", userId);
        return rs.next() ? map(rs) : null;
    }

    public String getNameById(String mechanicId) throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT name FROM mechanic WHERE mechanic_id=?", mechanicId);
        return rs.next() ? rs.getString(1) : null;
    }

    public String getIdByContact(String phone) throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT mechanic_id FROM mechanic WHERE phone=?", phone.trim());
        return rs.next() ? rs.getString(1) : null;
    }


    public ArrayList<MechanicDTO> getAvailable() throws Exception {
        ResultSet rs = CrudUtil.execute(
                "SELECT * FROM mechanic m WHERE NOT EXISTS "
                        + "(SELECT 1 FROM service_request s WHERE s.mechanic_id = m.mechanic_id AND s.status = 'In Progress') "
                        + "ORDER BY m.mechanic_id");
        ArrayList<MechanicDTO> list = new ArrayList<>();
        while (rs.next()) {
            list.add(map(rs));
        }
        return list;
    }

    public boolean save(MechanicDTO dto) throws Exception {
        validate(dto);
        if (isPhoneUsed(dto.getPhone(), null)) {
            throw new Exception("A mechanic with this phone number already exists.");
        }
        return CrudUtil.execute(
                "INSERT INTO mechanic (mechanic_id, user_id, name, specialization, phone) VALUES (?,?,?,?,?)",
                dto.getMechanicId(), dto.getUserId(), dto.getName().trim(), dto.getSpecialization(), dto.getPhone().trim());
    }

    public boolean update(MechanicDTO dto) throws Exception {
        validate(dto);
        if (isPhoneUsed(dto.getPhone(), dto.getMechanicId())) {
            throw new Exception("Another mechanic already uses this phone number.");
        }
        return CrudUtil.execute(
                "UPDATE mechanic SET user_id=?, name=?, specialization=?, phone=? WHERE mechanic_id=?",
                dto.getUserId(), dto.getName().trim(), dto.getSpecialization(), dto.getPhone().trim(), dto.getMechanicId());
    }


    public boolean delete(String mechanicId) throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT service_id FROM service_request WHERE mechanic_id=?", mechanicId);
        if (rs.next()) {
            throw new Exception("This mechanic has assigned jobs and cannot be deleted.");
        }
        return CrudUtil.execute("DELETE FROM mechanic WHERE mechanic_id=?", mechanicId);
    }

    private boolean isPhoneUsed(String phone, String excludeMechanicId) throws Exception {
        ResultSet rs = CrudUtil.execute(
                "SELECT mechanic_id FROM mechanic WHERE phone=? AND mechanic_id<>?",
                phone.trim(), excludeMechanicId == null ? "" : excludeMechanicId);
        return rs.next();
    }

    private void validate(MechanicDTO dto) throws Exception {
        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new Exception("Mechanic name is required.");
        }
        if (dto.getPhone() == null || !dto.getPhone().trim().matches("\\+?\\d{9,12}")) {
            throw new Exception("Enter a valid phone number.");
        }
    }

    private MechanicDTO map(ResultSet rs) throws Exception {
        return new MechanicDTO(
                rs.getString("mechanic_id"),
                rs.getString("user_id"),
                rs.getString("name"),
                rs.getString("specialization"),
                rs.getString("phone"));
    }
}
