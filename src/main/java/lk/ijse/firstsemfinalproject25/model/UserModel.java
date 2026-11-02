package lk.ijse.firstsemfinalproject25.model;

import lk.ijse.firstsemfinalproject25.dto.UserDTO;
import lk.ijse.firstsemfinalproject25.util.CrudUtil;
import lk.ijse.firstsemfinalproject25.util.IdGenerator;
import lk.ijse.firstsemfinalproject25.util.PasswordUtil;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class UserModel {

    public static final List<String> ROLES = Arrays.asList("Admin", "Receptionist", "Mechanic");

    public String getNextId() throws Exception {
        return IdGenerator.nextId("`user`", "user_id", "U");
    }

    public ArrayList<String> getAllUserIds() throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT user_id FROM `user` ORDER BY user_id");
        ArrayList<String> ids = new ArrayList<>();
        while (rs.next()) {
            ids.add(rs.getString(1));
        }
        return ids;
    }

    public ArrayList<UserDTO> getAllUsers() throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT * FROM `user` ORDER BY user_id");
        ArrayList<UserDTO> list = new ArrayList<>();
        while (rs.next()) {
            list.add(map(rs));
        }
        return list;
    }

    public UserDTO searchUser(String userId) throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT * FROM `user` WHERE user_id=?", userId);
        return rs.next() ? map(rs) : null;
    }

    public String getUsernameById(String userId) throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT username FROM `user` WHERE user_id=?", userId);
        return rs.next() ? rs.getString(1) : null;
    }

    public String getFullNameById(String userId) throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT full_name FROM `user` WHERE user_id=?", userId);
        return rs.next() ? rs.getString(1) : null;
    }

    public String getIdByUsername(String username) throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT user_id FROM `user` WHERE username=?", username.trim());
        return rs.next() ? rs.getString(1) : null;
    }

    /** rawPassword is what the user typed; it is hashed before saving. */
    public boolean save(UserDTO dto, String rawPassword) throws Exception {
        validate(dto);
        if (rawPassword == null || rawPassword.length() < 4) {
            throw new Exception("Password must be at least 4 characters.");
        }
        if (usernameExists(dto.getUsername(), null)) {
            throw new Exception("Username already exists.");
        }
        return CrudUtil.execute(
                "INSERT INTO `user` (user_id, username, password_hash, role, full_name) VALUES (?,?,?,?,?)",
                dto.getUserId(), dto.getUsername().trim(), PasswordUtil.hash(rawPassword),
                dto.getRole(), dto.getFullName().trim());
    }

    /** Updates username, role and full name (not the password). */
    public boolean update(UserDTO dto) throws Exception {
        validate(dto);
        if (usernameExists(dto.getUsername(), dto.getUserId())) {
            throw new Exception("Username already exists.");
        }
        return CrudUtil.execute(
                "UPDATE `user` SET username=?, role=?, full_name=? WHERE user_id=?",
                dto.getUsername().trim(), dto.getRole(), dto.getFullName().trim(), dto.getUserId());
    }

    public boolean changePassword(String userId, String newRawPassword) throws Exception {
        if (newRawPassword == null || newRawPassword.length() < 4) {
            throw new Exception("Password must be at least 4 characters.");
        }
        return CrudUtil.execute("UPDATE `user` SET password_hash=? WHERE user_id=?",
                PasswordUtil.hash(newRawPassword), userId);
    }

    public boolean delete(String userId) throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT mechanic_id FROM mechanic WHERE user_id=?", userId);
        if (rs.next()) {
            throw new Exception("This user is linked to a mechanic. Delete the mechanic first.");
        }
        return CrudUtil.execute("DELETE FROM `user` WHERE user_id=?", userId);
    }

    /** Login: returns the user, or null when the username/password is wrong. */
    public UserDTO authenticate(String username, String rawPassword) throws Exception {
        ResultSet rs = CrudUtil.execute(
                "SELECT * FROM `user` WHERE username=? AND password_hash=?",
                username.trim(), PasswordUtil.hash(rawPassword));
        return rs.next() ? map(rs) : null;
    }

    private boolean usernameExists(String username, String excludeUserId) throws Exception {
        ResultSet rs = CrudUtil.execute(
                "SELECT user_id FROM `user` WHERE username=? AND user_id<>?",
                username.trim(), excludeUserId == null ? "" : excludeUserId);
        return rs.next();
    }

    private void validate(UserDTO dto) throws Exception {
        if (dto.getUsername() == null || dto.getUsername().trim().isEmpty()) {
            throw new Exception("Username is required.");
        }
        if (dto.getFullName() == null || dto.getFullName().trim().isEmpty()) {
            throw new Exception("Full name is required.");
        }
        if (!ROLES.contains(dto.getRole())) {
            throw new Exception("Role must be Admin, Receptionist or Mechanic.");
        }
    }

    private UserDTO map(ResultSet rs) throws Exception {
        return new UserDTO(
                rs.getString("user_id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("role"),
                rs.getString("full_name"));
    }
}