package lk.ijse.firstsemfinalproject25.model;

import lk.ijse.firstsemfinalproject25.dto.CustomerDTO;
import lk.ijse.firstsemfinalproject25.util.CrudUtil;
import lk.ijse.firstsemfinalproject25.util.IdGenerator;

import java.sql.ResultSet;
import java.util.ArrayList;

public class CustomerModel {

    public String getNextId() throws Exception {
        return IdGenerator.nextId("customer", "customer_id", "C");
    }

    public ArrayList<String> getAllIds() throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT customer_id FROM customer ORDER BY customer_id");
        ArrayList<String> ids = new ArrayList<>();
        while (rs.next()) {
            ids.add(rs.getString(1));
        }
        return ids;
    }

    public ArrayList<CustomerDTO> getAll() throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT * FROM customer ORDER BY customer_id");
        ArrayList<CustomerDTO> list = new ArrayList<>();
        while (rs.next()) {
            list.add(map(rs));
        }
        return list;
    }

    public CustomerDTO search(String customerId) throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT * FROM customer WHERE customer_id=?", customerId);
        return rs.next() ? map(rs) : null;
    }

    public ArrayList<CustomerDTO> searchByKeyword(String keyword) throws Exception {
        String like = "%" + keyword.trim() + "%";
        ResultSet rs = CrudUtil.execute(
                "SELECT * FROM customer WHERE customer_id LIKE ? OR name LIKE ? OR phone LIKE ? ORDER BY customer_id",
                like, like, like);
        ArrayList<CustomerDTO> list = new ArrayList<>();
        while (rs.next()) {
            list.add(map(rs));
        }
        return list;
    }

    public String getNameById(String customerId) throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT name FROM customer WHERE customer_id=?", customerId);
        return rs.next() ? rs.getString(1) : null;
    }

    public String getIdByContact(String phone) throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT customer_id FROM customer WHERE phone=?", phone.trim());
        return rs.next() ? rs.getString(1) : null;
    }

    public boolean save(CustomerDTO dto) throws Exception {
        validate(dto);
        if (isPhoneUsed(dto.getPhone(), null)) {
            throw new Exception("A customer with this phone number already exists.");
        }
        return CrudUtil.execute(
                "INSERT INTO customer (customer_id, name, email, phone, address) VALUES (?,?,?,?,?)",
                dto.getCustomerId(), dto.getName().trim(), dto.getEmail(), dto.getPhone().trim(), dto.getAddress());
    }

    public boolean update(CustomerDTO dto) throws Exception {
        validate(dto);
        if (isPhoneUsed(dto.getPhone(), dto.getCustomerId())) {
            throw new Exception("Another customer already uses this phone number.");
        }
        return CrudUtil.execute(
                "UPDATE customer SET name=?, email=?, phone=?, address=? WHERE customer_id=?",
                dto.getName().trim(), dto.getEmail(), dto.getPhone().trim(), dto.getAddress(), dto.getCustomerId());
    }


    public boolean delete(String customerId) throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT bike_id FROM motorcycle WHERE customer_id=?", customerId);
        if (rs.next()) {
            throw new Exception("This customer still has registered motorcycles. Remove them first.");
        }
        return CrudUtil.execute("DELETE FROM customer WHERE customer_id=?", customerId);
    }

    private boolean isPhoneUsed(String phone, String excludeCustomerId) throws Exception {
        ResultSet rs = CrudUtil.execute(
                "SELECT customer_id FROM customer WHERE phone=? AND customer_id<>?",
                phone.trim(), excludeCustomerId == null ? "" : excludeCustomerId);
        return rs.next();
    }

    private void validate(CustomerDTO dto) throws Exception {
        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new Exception("Customer name is required.");
        }
        if (dto.getPhone() == null || !dto.getPhone().trim().matches("\\+?\\d{9,12}")) {
            throw new Exception("Enter a valid phone number.");
        }
    }

    private CustomerDTO map(ResultSet rs) throws Exception {
        return new CustomerDTO(
                rs.getString("customer_id"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getString("address"));
    }
}
