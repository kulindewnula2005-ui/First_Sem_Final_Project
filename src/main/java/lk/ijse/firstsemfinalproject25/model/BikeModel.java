package lk.ijse.firstsemfinalproject25.model;

import lk.ijse.firstsemfinalproject25.dto.BikeDTO;
import lk.ijse.firstsemfinalproject25.util.CrudUtil;
import lk.ijse.firstsemfinalproject25.util.IdGenerator;

import java.sql.ResultSet;
import java.util.ArrayList;

public class BikeModel {

    // compares registration numbers ignoring case, spaces and hyphens
    private static final String NORMALIZED_REG = "REPLACE(REPLACE(UPPER(reg_number),' ',''),'-','')";

    public String getNextId() throws Exception {
        return IdGenerator.nextId("bike", "bike_id", "BK");
    }

    /** "cab - 1234" -> "CAB1234". Use on text coming from the plate scanner / search box. */
    public static String normalizeReg(String reg) {
        return reg == null ? "" : reg.toUpperCase().replaceAll("[\\s-]", "");
    }

    public ArrayList<String> getAllIds() throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT bike_id FROM bike ORDER BY bike_id");
        ArrayList<String> ids = new ArrayList<>();
        while (rs.next()) {
            ids.add(rs.getString(1));
        }
        return ids;
    }

    public ArrayList<BikeDTO> getAll() throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT * FROM bike ORDER BY bike_id");
        ArrayList<BikeDTO> list = new ArrayList<>();
        while (rs.next()) {
            list.add(map(rs));
        }
        return list;
    }

    public BikeDTO search(String bikeId) throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT * FROM bike WHERE bike_id=?", bikeId);
        return rs.next() ? map(rs) : null;
    }

    /** Number-plate scan lookup (FR-13). Returns null when the bike is not registered. */
    public BikeDTO searchByRegNumber(String regNumber) throws Exception {
        ResultSet rs = CrudUtil.execute(
                "SELECT * FROM bike WHERE " + NORMALIZED_REG + " = ?", normalizeReg(regNumber));
        return rs.next() ? map(rs) : null;
    }

    public String getIdByRegNumber(String regNumber) throws Exception {
        BikeDTO dto = searchByRegNumber(regNumber);
        return dto == null ? null : dto.getBikeId();
    }

    /** Suggestions when the scanner misreads a character (FR-14). */
    public ArrayList<BikeDTO> searchPartialReg(String part) throws Exception {
        ResultSet rs = CrudUtil.execute(
                "SELECT * FROM bike WHERE " + NORMALIZED_REG + " LIKE ? ORDER BY reg_number",
                "%" + normalizeReg(part) + "%");
        ArrayList<BikeDTO> list = new ArrayList<>();
        while (rs.next()) {
            list.add(map(rs));
        }
        return list;
    }

    public ArrayList<BikeDTO> getByCustomer(String customerId) throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT * FROM bike WHERE customer_id=? ORDER BY bike_id", customerId);
        ArrayList<BikeDTO> list = new ArrayList<>();
        while (rs.next()) {
            list.add(map(rs));
        }
        return list;
    }

    /** Display name such as "Honda CB125 (CAB-1234)". */
    public String getNameById(String bikeId) throws Exception {
        BikeDTO dto = search(bikeId);
        return dto == null ? null : dto.getBrand() + " " + dto.getModel() + " (" + dto.getRegNumber() + ")";
    }

    public boolean save(BikeDTO dto) throws Exception {
        validate(dto);
        if (searchByRegNumber(dto.getRegNumber()) != null) {
            throw new Exception("A bike with this registration number already exists.");
        }
        return CrudUtil.execute(
                "INSERT INTO bike (bike_id, customer_id, reg_number, brand, model, `year`, engine_no, chassis_no) "
                        + "VALUES (?,?,?,?,?,?,?,?)",                  // 8 columns = 8 placeholders
                dto.getBikeId(),                                       // 1
                dto.getCustomerId(),                                   // 2
                dto.getRegNumber().trim().toUpperCase(),               // 3
                dto.getBrand().trim(),                                 // 4
                dto.getModel().trim(),                                 // 5
                dto.getYear(),                                         // 6
                clean(dto.getEngineNo()),                              // 7
                clean(dto.getChassisNo()));                            // 8
    }

    public boolean update(BikeDTO dto) throws Exception {
        validate(dto);
        BikeDTO existing = searchByRegNumber(dto.getRegNumber());
        if (existing != null && !existing.getBikeId().equals(dto.getBikeId())) {
            throw new Exception("Another bike already uses this registration number.");
        }
        return CrudUtil.execute(
                "UPDATE bike SET customer_id=?, reg_number=?, brand=?, model=?, `year`=?, engine_no=?, chassis_no=? WHERE bike_id=?",                           // 7 in SET + 1 in WHERE = 8
                dto.getCustomerId(),                                   // 1
                dto.getRegNumber().trim().toUpperCase(),               // 2
                dto.getBrand().trim(),                                 // 3
                dto.getModel().trim(),                                 // 4
                dto.getYear(),                                         // 5
                clean(dto.getEngineNo()),                              // 6
                clean(dto.getChassisNo()),                             // 7
                dto.getBikeId());                                      // 8
    }

    /** A bike with service history cannot be deleted. */
    public boolean delete(String bikeId) throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT service_id FROM service_request WHERE bike_id=?", bikeId);
        if (rs.next()) {
            throw new Exception("This bike has service history and cannot be deleted.");
        }
        return CrudUtil.execute("DELETE FROM bike WHERE bike_id=?", bikeId);
    }

    /** blank -> null, otherwise trimmed upper-case (engine / chassis numbers). */
    private String clean(String text) {
        return (text == null || text.trim().isEmpty()) ? null : text.trim().toUpperCase();
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
        if ((dto.getEngineNo() != null && dto.getEngineNo().trim().length() > 50)
                || (dto.getChassisNo() != null && dto.getChassisNo().trim().length() > 50)) {
            throw new Exception("Engine and chassis numbers can be at most 50 characters.");
        }
    }

    private BikeDTO map(ResultSet rs) throws Exception {
        return new BikeDTO(
                rs.getString("bike_id"),
                rs.getString("customer_id"),
                rs.getString("reg_number"),
                rs.getString("brand"),
                rs.getString("model"),
                rs.getInt("year"),
                rs.getString("engine_no"),
                rs.getString("chassis_no"));
    }
}