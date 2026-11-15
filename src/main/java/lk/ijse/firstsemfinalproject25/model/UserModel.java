package lk.ijse.firstsemfinalproject25.model;

import lk.ijse.firstsemfinalproject25.dto.UserDTO;
import lk.ijse.firstsemfinalproject25.util.CrudUtil;
import lk.ijse.firstsemfinalproject25.util.IdGenerator;
import lk.ijse.firstsemfinalproject25.util.PasswordUtil;
import lk.ijse.firstsemfinalproject25.util.Session;

import java.sql.ResultSet;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * All database work for the `user` table.
 *
 * ROLES:  Super Admin (only ONE in the system) > Admin > Receptionist / Mechanic
 *  - the very first account created on a new system becomes the Super Admin
 *  - people signing up themselves can only be Receptionist or Mechanic
 *  - only the Super Admin can add, edit or delete users
 */
public class UserModel {

    public static final String SUPER_ADMIN = "Super Admin";
    public static final List<String> ROLES = Arrays.asList(SUPER_ADMIN, "Admin", "Receptionist", "Mechanic");

    private static final Set<String> SELF_SIGNUP_ROLES = Set.of("Receptionist", "Mechanic");
    private static final int MIN_PASSWORD_LENGTH = 4;

    /** Thrown when the username is already used by another account. */
    public static class UsernameTakenException extends Exception {
        public UsernameTakenException() {
            super("Username already exists.");
        }
    }

    /* ===================== SIGN IN ===================== */

    /** Returns the user when username + password are correct, otherwise null. */
    public UserDTO authenticate(String username, String rawPassword) throws Exception {
        if (username == null || rawPassword == null) return null;

        ResultSet rs = CrudUtil.execute("SELECT * FROM `user` WHERE username=?", username.trim());
        if (!rs.next()) return null;

        UserDTO user = map(rs);
        return PasswordUtil.matches(rawPassword, user.getPasswordHash()) ? user : null;
    }

    /* ===================== SIGN UP ===================== */

    /** True when no account exists yet (the first account becomes the Super Admin). */
    public boolean isEmpty() throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT COUNT(*) FROM `user`");
        rs.next();
        return rs.getInt(1) == 0;
    }

    /** Used by the live "username available?" check on the Sign Up page. */
    public boolean usernameExists(String username) throws Exception {
        return usernameExists(username, null);
    }

    /**
     * Creates an account from the Sign Up page (no login needed).
     * First account = Super Admin, everybody else = Receptionist or Mechanic.
     * @throws UsernameTakenException if the username already exists
     */
    public boolean register(String fullName, String username, String rawPassword, String requestedRole)
            throws Exception {
        String role = isEmpty()
                ? SUPER_ADMIN
                : (SELF_SIGNUP_ROLES.contains(requestedRole) ? requestedRole : "Receptionist");

        return insertUser(new UserDTO(getNextId(), username, null, role, fullName), rawPassword);
    }

    /* ===================== READ ===================== */

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

    /** role -> how many users (for the Super Admin dashboard numbers). */
    public Map<String, Integer> countByRole() throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT role, COUNT(*) FROM `user` GROUP BY role");
        Map<String, Integer> counts = new LinkedHashMap<>();
        while (rs.next()) {
            counts.put(rs.getString(1), rs.getInt(2));
        }
        return counts;
    }

    public boolean superAdminExists() throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT user_id FROM `user` WHERE role=?", SUPER_ADMIN);
        return rs.next();
    }

    /* ===================== MANAGE USERS (Super Admin only) ===================== */

    /** Super Admin adds a user. rawPassword is hashed before saving. */
    public boolean save(UserDTO dto, String rawPassword) throws Exception {
        Session.requireSuperAdmin();
        return insertUser(dto, rawPassword);
    }

    /** Updates username, role and full name (not the password). */
    public boolean update(UserDTO dto) throws Exception {
        Session.requireSuperAdmin();
        validate(dto);

        UserDTO old = searchUser(dto.getUserId());
        if (old == null) {
            throw new Exception("User not found.");
        }
        boolean roleChanged = !old.getRole().equals(dto.getRole());
        if (roleChanged && (SUPER_ADMIN.equals(old.getRole()) || SUPER_ADMIN.equals(dto.getRole()))) {
            throw new Exception("The Super Admin role cannot be given to or taken from anyone.");
        }
        if (usernameExists(dto.getUsername(), dto.getUserId())) {
            throw new UsernameTakenException();
        }
        return CrudUtil.execute(
                "UPDATE `user` SET username=?, role=?, full_name=? WHERE user_id=?",
                dto.getUsername().trim(), dto.getRole(), dto.getFullName().trim(), dto.getUserId());
    }

    public boolean delete(String userId) throws Exception {
        Session.requireSuperAdmin();

        UserDTO user = searchUser(userId);
        if (user == null) {
            throw new Exception("User not found.");
        }
        if (SUPER_ADMIN.equals(user.getRole())) {
            throw new Exception("The Super Admin account cannot be deleted.");
        }
        ResultSet rs = CrudUtil.execute("SELECT mechanic_id FROM mechanic WHERE user_id=?", userId);
        if (rs.next()) {
            throw new Exception("This user is linked to a mechanic. Delete the mechanic first.");
        }
        return CrudUtil.execute("DELETE FROM `user` WHERE user_id=?", userId);
    }

    /** Used by the reset-PIN flow (the user is NOT logged in), so no Super Admin check here. */
    public boolean changePassword(String userId, String newRawPassword) throws Exception {
        if (newRawPassword == null || newRawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new Exception("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        return CrudUtil.execute("UPDATE `user` SET password_hash=? WHERE user_id=?",
                PasswordUtil.hash(newRawPassword), userId);
    }

    /* ===================== helpers ===================== */

    private boolean insertUser(UserDTO dto, String rawPassword) throws Exception {
        validate(dto);
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new Exception("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (SUPER_ADMIN.equals(dto.getRole()) && superAdminExists()) {
            throw new Exception("There can only be one Super Admin in the system.");
        }
        if (usernameExists(dto.getUsername(), null)) {
            throw new UsernameTakenException();
        }
        try {
            return CrudUtil.execute(
                    "INSERT INTO `user` (user_id, username, password_hash, role, full_name) VALUES (?,?,?,?,?)",
                    dto.getUserId(), dto.getUsername().trim(), PasswordUtil.hash(rawPassword),
                    dto.getRole(), dto.getFullName().trim());
        } catch (Exception ex) {
            // two people took the same username at the same moment (UNIQUE key on username)
            if (ex instanceof SQLIntegrityConstraintViolationException) {
                throw new UsernameTakenException();
            }
            throw ex;
        }
    }

    /** excludeUserId lets "update" ignore the user's own row. */
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
        if (dto.getUsername().trim().length() > 50) {
            throw new Exception("Username must be 50 characters or less.");
        }
        if (dto.getFullName() == null || dto.getFullName().trim().isEmpty()) {
            throw new Exception("Full name is required.");
        }
        if (dto.getFullName().trim().length() > 100) {
            throw new Exception("Full name must be 100 characters or less.");
        }
        if (!ROLES.contains(dto.getRole())) {
            throw new Exception("Role must be one of: " + String.join(", ", ROLES) + ".");
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
