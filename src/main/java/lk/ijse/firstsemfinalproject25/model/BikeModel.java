package lk.ijse.firstsemfinalproject25.model;

import lk.ijse.firstsemfinalproject25.dto.BikeDTO;
import lk.ijse.firstsemfinalproject25.util.CrudUtil;
import lk.ijse.firstsemfinalproject25.util.IdGenerator;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

public class BikeModel {

    // compares registration numbers ignoring case, spaces and hyphens
    private static final String NORMALIZED_REG = "REPLACE(REPLACE(UPPER(reg_number),' ',''),'-','')";

    public String getNextId() throws Exception {
        return IdGenerator.nextId("motorcycle", "bike_id", "BK");
    }


    public static String normalizeReg(String reg) {
        return reg == null ? "" : reg.toUpperCase().replaceAll("[\\s-]", "");
    }

    public ArrayList<String> getAllIds() throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT bike_id FROM motorcycle ORDER BY bike_id");
        ArrayList<String> ids = new ArrayList<>();
        while (rs.next()) {
            ids.add(rs.getString(1));
        }
        return ids;
    }

    public ArrayList<BikeDTO> getAll() throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT * FROM motorcycle ORDER BY bike_id");
        ArrayList<BikeDTO> list = new ArrayList<>();
        while (rs.next()) {
            list.add(map(rs));
        }
        return list;
    }

    public BikeDTO search(String bikeId) throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT * FROM motorcycle WHERE bike_id=?", bikeId);
        return rs.next() ? map(rs) : null;
    }


    public BikeDTO searchByRegNumber(String regNumber) throws Exception {
        ResultSet rs = CrudUtil.execute(
                "SELECT * FROM motorcycle WHERE " + NORMALIZED_REG + " = ?", normalizeReg(regNumber));
        return rs.next() ? map(rs) : null;
    }

    public String getIdByRegNumber(String regNumber) throws Exception {
        BikeDTO dto = searchByRegNumber(regNumber);
        return dto == null ? null : dto.getBikeId();
    }


    public ArrayList<BikeDTO> searchPartialReg(String part) throws Exception {
        ResultSet rs = CrudUtil.execute(
                "SELECT * FROM motorcycle WHERE " + NORMALIZED_REG + " LIKE ? ORDER BY reg_number",
                "%" + normalizeReg(part) + "%");
        ArrayList<BikeDTO> list = new ArrayList<>();
        while (rs.next()) {
            list.add(map(rs));
        }
        return list;
    }

    public ArrayList<BikeDTO> getByCustomer(String customerId) throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT * FROM motorcycle WHERE customer_id=? ORDER BY bike_id", customerId);
        ArrayList<BikeDTO> list = new ArrayList<>();
        while (rs.next()) {
            list.add(map(rs));
        }
        return list;
    }


    public String getNameById(String bikeId) throws Exception {
        BikeDTO dto = search(bikeId);
        return dto == null ? null : dto.getBrand() + " " + dto.getModel() + " (" + dto.getRegNumber() + ")";
    }

    public boolean save(BikeDTO dto) throws Exception {
        validate(dto);
        if (searchByRegNumber(dto.getRegNumber()) != null) {
            throw new Exception("A motorcycle with this registration number already exists.");
        }
        return CrudUtil.execute(
                "INSERT INTO motorcycle (bike_id, customer_id, reg_number, brand, model, year) VALUES (?,?,?,?,?,?)",
                dto.getBikeId(), dto.getCustomerId(), dto.getRegNumber().trim().toUpperCase(),
                dto.getBrand().trim(), dto.getModel().trim(), dto.getYear());
    }

    public boolean update(BikeDTO dto) throws Exception {
        validate(dto);
        BikeDTO existing = searchByRegNumber(dto.getRegNumber());
        if (existing != null && !existing.getBikeId().equals(dto.getBikeId())) {
            throw new Exception("Another motorcycle already uses this registration number.");
        }
        return CrudUtil.execute(
                "UPDATE motorcycle SET customer_id=?, reg_number=?, brand=?, model=?, year=? WHERE bike_id=?",
                dto.getCustomerId(), dto.getRegNumber().trim().toUpperCase(),
                dto.getBrand().trim(), dto.getModel().trim(), dto.getYear(), dto.getBikeId());
    }


    public boolean delete(String bikeId) throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT service_id FROM service_request WHERE bike_id=?", bikeId);
        if (rs.next()) {
            throw new Exception("This motorcycle has service history and cannot be deleted.");
        }
        return CrudUtil.execute("DELETE FROM motorcycle WHERE bike_id=?", bikeId);
    }

    private void validate(BikeDTO dto) throws Exception {
        if (dto.getCustomerId() == null || dto.getCustomerId().trim().isEmpty()) {
            throw new Exception("Select the owner (customer).");
        }
        if (normalizeReg(dto.getRegNumber()).isEmpty()) {
            throw new Exception("Registration number is required.");
        }
        if (dto.getBrand() == null || dto.getBrand().trim().isEmpty()
                || dto.getModel() == null || dto.getModel().trim().isEmpty()) {
            throw new Exception("Brand and model are required.");
        }
        int thisYear = java.time.Year.now().getValue();
        if (dto.getYear() < 1950 || dto.getYear() > thisYear + 1) {
            throw new Exception("Enter a valid year.");
        }
    }

    private BikeDTO map(ResultSet rs) throws Exception {
        return new BikeDTO(
                rs.getString("bike_id"),
                rs.getString("customer_id"),
                rs.getString("reg_number"),
                rs.getString("brand"),
                rs.getString("model"),
                rs.getInt("year"));
    }
}
