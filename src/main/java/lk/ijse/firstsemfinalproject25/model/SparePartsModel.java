package lk.ijse.firstsemfinalproject25.model;

import lk.ijse.firstsemfinalproject25.dto.SparePartsDTO;
import lk.ijse.firstsemfinalproject25.util.CrudUtil;
import lk.ijse.firstsemfinalproject25.util.IdGenerator;

import java.sql.ResultSet;
import java.util.ArrayList;

public class SparePartsModel {

    public String getNextId() throws Exception {
        return IdGenerator.nextId("spare_part", "part_id", "P");
    }

    public ArrayList<String> getAllIds() throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT part_id FROM spare_part ORDER BY part_id");
        ArrayList<String> ids = new ArrayList<>();
        while (rs.next()) {
            ids.add(rs.getString(1));
        }
        return ids;
    }

    public ArrayList<SparePartsDTO> getAll() throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT * FROM spare_part ORDER BY part_id");
        ArrayList<SparePartsDTO> list = new ArrayList<>();
        while (rs.next()) {
            list.add(map(rs));
        }
        return list;
    }

    public SparePartsDTO search(String partId) throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT * FROM spare_part WHERE part_id=?", partId);
        return rs.next() ? map(rs) : null;
    }

    public String getNameById(String partId) throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT part_name FROM spare_part WHERE part_id=?", partId);
        return rs.next() ? rs.getString(1) : null;
    }


    public ArrayList<SparePartsDTO> getLowStock() throws Exception {
        ResultSet rs = CrudUtil.execute(
                "SELECT * FROM spare_part WHERE stock_qty <= reorder_level ORDER BY stock_qty");
        ArrayList<SparePartsDTO> list = new ArrayList<>();
        while (rs.next()) {
            list.add(map(rs));
        }
        return list;
    }

    public boolean save(SparePartsDTO dto) throws Exception {
        validate(dto);
        return CrudUtil.execute(
                "INSERT INTO spare_part (part_id, part_name, unit_price, stock_qty, reorder_level) VALUES (?,?,?,?,?)",
                dto.getPartId(), dto.getPartName().trim(), dto.getUnitPrice(), dto.getStockQty(), dto.getReorderLevel());
    }

    public boolean update(SparePartsDTO dto) throws Exception {
        validate(dto);
        return CrudUtil.execute(
                "UPDATE spare_part SET part_name=?, unit_price=?, stock_qty=?, reorder_level=? WHERE part_id=?",
                dto.getPartName().trim(), dto.getUnitPrice(), dto.getStockQty(), dto.getReorderLevel(), dto.getPartId());
    }


    public boolean delete(String partId) throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT service_id FROM service_parts WHERE part_id=?", partId);
        if (rs.next()) {
            throw new Exception("This part has been used in services and cannot be deleted.");
        }
        return CrudUtil.execute("DELETE FROM spare_part WHERE part_id=?", partId);
    }


    public boolean reduceStock(String partId, int qty) throws Exception {
        return CrudUtil.execute(
                "UPDATE spare_part SET stock_qty = stock_qty - ? WHERE part_id=? AND stock_qty >= ?",
                qty, partId, qty);
    }

    public boolean addStock(String partId, int qty) throws Exception {
        return CrudUtil.execute("UPDATE spare_part SET stock_qty = stock_qty + ? WHERE part_id=?", qty, partId);
    }

    private void validate(SparePartsDTO dto) throws Exception {
        if (dto.getPartName() == null || dto.getPartName().trim().isEmpty()) {
            throw new Exception("Part name is required.");
        }
        if (dto.getUnitPrice() < 0) {
            throw new Exception("Unit price cannot be negative.");
        }
        if (dto.getStockQty() < 0 || dto.getReorderLevel() < 0) {
            throw new Exception("Stock quantity and reorder level cannot be negative.");
        }
    }

    private SparePartsDTO map(ResultSet rs) throws Exception {
        return new SparePartsDTO(
                rs.getString("part_id"),
                rs.getString("part_name"),
                rs.getDouble("unit_price"),
                rs.getInt("stock_qty"),
                rs.getInt("reorder_level"));
    }

}
