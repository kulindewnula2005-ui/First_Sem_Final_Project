package lk.ijse.firstsemfinalproject25.model;

import lk.ijse.firstsemfinalproject25.dto.UserDTO;
import lk.ijse.firstsemfinalproject25.util.*;

import java.sql.ResultSet;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;


public class UserModel {


    /** Roles a person may pick for themselves on the Sign Up page (Admin is never self-service). */
    private static final Set<String> SELF_SIGNUP_ROLES = Set.of("Receptionist", "Mechanic");

    public static final List<String> ROLES = Arrays.asList(
            Permissions.SUPER_ADMIN, Permissions.RECEPTIONIST, Permissions.MECHANIC);

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

    public int countByRole(String role) throws Exception {
        ResultSet rs = CrudUtil.execute("SELECT COUNT(*) FROM `user` WHERE role=?", role);
        return rs.next() ? rs.getInt(1) : 0;
    }

    /** ADD: see Permissions.canAddUser for who may create which role. */
    public boolean save(UserDTO dto, String rawPassword) throws Exception {
        validate(dto);
        if (!Permissions.canAddUser(dto.getRole())) {
            throw new Exception("You are not allowed to add a " + dto.getRole() + " account.");
        }
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

    /**
     * UPDATE: only the Super Admin can update other accounts.
     * Everybody can edit their own name and username (but never their own role).
     */
    public boolean update(UserDTO dto) throws Exception {
        validate(dto);
        UserDTO old = searchUser(dto.getUserId());
        if (old == null) {
            throw new Exception("User not found.");
        }

        UserDTO me = Session.getUser();
        boolean self = me != null && me.getUserId().equals(dto.getUserId());
        if (!Permissions.isSuperAdmin()) {
            if (!self) {
                throw new Exception("Only the Super Admin can update other user accounts.");
            }
            if (!old.getRole().equals(dto.getRole())) {
                throw new Exception("You cannot change your own role.");
            }
        }
        if (Permissions.SUPER_ADMIN.equals(old.getRole()) && !Permissions.SUPER_ADMIN.equals(dto.getRole())
                && countByRole(Permissions.SUPER_ADMIN) <= 1) {
            throw new Exception("At least one Super Admin must remain.");
        }
        if (usernameExists(dto.getUsername(), dto.getUserId())) {
            throw new Exception("Username already exists.");
        }
        return CrudUtil.execute(
                "UPDATE `user` SET username=?, role=?, full_name=? WHERE user_id=?",
                dto.getUsername().trim(), dto.getRole(), dto.getFullName().trim(), dto.getUserId());
    }

    /** Your own password, or (Super Admin only) anybody's password. */
    public boolean changePassword(String userId, String newRawPassword) throws Exception {
        UserDTO me = Session.getUser();
        boolean self = me != null && me.getUserId().equals(userId);
        if (!self && !Permissions.isSuperAdmin()) {
            throw new Exception("Only the Super Admin can change another user's password.");
        }
        if (newRawPassword == null || newRawPassword.length() < 4) {
            throw new Exception("Password must be at least 4 characters.");
        }
        return CrudUtil.execute("UPDATE `user` SET password_hash=? WHERE user_id=?",
                PasswordUtil.hash(newRawPassword), userId);
    }

    /** DELETE: Super Admin only. */
    public boolean delete(String userId) throws Exception {
        Permissions.requireSuperAdmin("delete user accounts");

        UserDTO target = searchUser(userId);
        if (target == null) {
            throw new Exception("User not found.");
        }
        UserDTO me = Session.getUser();
        if (me != null && me.getUserId().equals(userId)) {
            throw new Exception("You cannot delete the account you are signed in with.");
        }
        if (Permissions.SUPER_ADMIN.equals(target.getRole()) && countByRole(Permissions.SUPER_ADMIN) <= 1) {
            throw new Exception("At least one Super Admin must remain.");
        }
        ResultSet rs = CrudUtil.execute("SELECT mechanic_id FROM mechanic WHERE user_id=?", userId);
        if (rs.next()) {
            throw new Exception("This user is linked to a mechanic. Delete the mechanic first.");
        }
        return CrudUtil.execute("DELETE FROM `user` WHERE user_id=?", userId);
    }

    /**
     * Used by "Add Mechanic" when saving the mechanic fails after its new login account was created:
     * removes that unused Mechanic account again (it needs no Super Admin, because nothing was kept).
     */
    public void discardUnlinkedMechanicAccount(String userId) throws Exception {
        ResultSet role = CrudUtil.execute("SELECT role FROM `user` WHERE user_id=?", userId);
        if (!role.next() || !Permissions.MECHANIC.equals(role.getString(1))) {
            return;
        }
        ResultSet linked = CrudUtil.execute("SELECT mechanic_id FROM mechanic WHERE user_id=?", userId);
        if (linked.next()) {
            return;
        }
        CrudUtil.execute("DELETE FROM `user` WHERE user_id=?", userId);
    }

    /** Login: returns the user, or null when the username / password is wrong. */
    public UserDTO authenticate(String username, String rawPassword) throws Exception {
        ResultSet rs = CrudUtil.execute(
                "SELECT * FROM `user` WHERE username=? AND password_hash=?",
                username.trim(), PasswordUtil.hash(rawPassword));
        return rs.next() ? map(rs) : null;
    }

    /** Thrown when the username is already used by another account. */
    public static class UsernameTakenException extends Exception {
        public UsernameTakenException() {
            super("Username already exists.");
        }
    }

    /** True when no account exists yet (the first account becomes the Admin). */
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
     * Creates an account from the Sign Up page.
     * - the very first account is created as Admin
     * - everyone else gets the chosen role (Receptionist / Mechanic only)
     * @throws UsernameTakenException if the username already exists
     */
    public boolean register(String fullName, String username, String rawPassword, String requestedRole)
            throws Exception {
        String role = isEmpty()
                ? "Admin"
                : (SELF_SIGNUP_ROLES.contains(requestedRole) ? requestedRole : "Receptionist");

        UserDTO dto = new UserDTO(getNextId(), username, null, role, fullName);
        return save(dto, rawPassword);
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