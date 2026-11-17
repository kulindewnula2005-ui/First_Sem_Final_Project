package lk.ijse.firstsemfinalproject25.model;

import lk.ijse.firstsemfinalproject25.db.DBConnection;
import lk.ijse.firstsemfinalproject25.dto.UserDTO;
import lk.ijse.firstsemfinalproject25.util.CrudUtil;
import lk.ijse.firstsemfinalproject25.util.PasswordUtil;
import lk.ijse.firstsemfinalproject25.util.Permissions;
import lk.ijse.firstsemfinalproject25.util.Session;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Forgot-password flow:
 * 1. The Super Admin generates a one-time PIN for a user (createPin / generatePin).
 * 2. The plain 6-digit PIN is saved so it displays clearly on the user's dashboard notification.
 * 3. The user enters username + PIN + new password on the Reset Password screen (resetPassword).
 */
public class PasswordResetModel {

    public static final int PIN_LENGTH = 6;       // digits in a PIN
    public static final int VALID_MINUTES = 15;   // how long a PIN works
    public static final int PIN_MINUTES = VALID_MINUTES;
    public static final int MAX_ATTEMPTS = 5;     // wrong tries before the PIN is cancelled
    public static final int MIN_PASSWORD = 6;     // minimum password length

    public static final String GENERIC_ERROR =
            "Invalid username or PIN, or the PIN has expired. Ask the administrator for a new PIN.";

    public static class InvalidPinException extends Exception {
        public InvalidPinException(String message) {
            super(message);
        }
    }

    /** One person waiting for a PIN (shown in the Super Admin's "PIN Requests" inbox). */
    public record PinRequest(String userId, String username, String fullName, String role, int minutesAgo) { }

    private static final SecureRandom RANDOM = new SecureRandom();

    /* ======================= Super Admin: create a PIN ======================= */
    /** Same as createPin (older name). */
    public String generatePin(String userId) throws Exception {
        return createPin(userId);
    }

    /** Returns the new plain 6-digit PIN. Any older PIN of this user stops working. Super Admin only. */
    public String createPin(String userId) throws Exception {
        Permissions.requireSuperAdmin("generate a reset PIN");

        UserDTO target = new UserModel().searchUser(userId);
        if (target == null) {
            throw new Exception("User not found.");
        }

        int limit = (int) Math.pow(10, PIN_LENGTH);
        String pin = String.format("%0" + PIN_LENGTH + "d", RANDOM.nextInt(limit));
        LocalDateTime now = LocalDateTime.now();
        UserDTO me = Session.getUser();

        CrudUtil.execute("DELETE FROM password_reset WHERE user_id=?", userId);
        boolean saved = CrudUtil.execute(
                "INSERT INTO password_reset (user_id, pin_hash, created_by, created_at, expires_at, attempts) "
                        + "VALUES (?,?,?,?,?,0)",
                userId, pin, me == null ? null : me.getUserId(), now, now.plusMinutes(VALID_MINUTES));
        if (!saved) {
            throw new Exception("The reset PIN could not be saved.");
        }
        CrudUtil.execute("DELETE FROM pin_request WHERE user_id=?", userId); // mark request as answered
        return pin;
    }

    /* ======================= the user: use the PIN ======================= */
    /**
     * Returns true when the password was changed.
     * Throws InvalidPinException for anything typed wrong; any other Exception means a database problem.
     */
    public boolean resetPassword(String username, String pin, String newPassword) throws Exception {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD) {
            throw new InvalidPinException("The new password must be at least " + MIN_PASSWORD + " characters.");
        }
        if (username == null || username.trim().isEmpty()
                || pin == null || !pin.trim().matches("\\d{" + PIN_LENGTH + "}")) {
            throw new InvalidPinException(GENERIC_ERROR);
        }

        ResultSet userRs = CrudUtil.execute("SELECT user_id FROM `user` WHERE username=?", username.trim());
        if (!userRs.next()) {
            throw new InvalidPinException(GENERIC_ERROR);
        }
        String userId = userRs.getString(1);

        ResultSet rs = CrudUtil.execute(
                "SELECT pin_hash, expires_at, attempts FROM password_reset WHERE user_id=?", userId);
        if (!rs.next()) {
            throw new InvalidPinException(GENERIC_ERROR);
        }
        String storedPin = rs.getString("pin_hash");
        LocalDateTime expires = rs.getObject("expires_at", LocalDateTime.class);
        int attempts = rs.getInt("attempts");

        // expired, or too many wrong tries: the PIN is cancelled
        if (expires == null || LocalDateTime.now().isAfter(expires) || attempts >= MAX_ATTEMPTS) {
            CrudUtil.execute("DELETE FROM password_reset WHERE user_id=?", userId);
            throw new InvalidPinException(GENERIC_ERROR);
        }

        // wrong PIN: count the try (compare plain 6-digit PINs directly)
        boolean correct = storedPin != null && storedPin.equals(pin.trim());
        if (!correct) {
            if (attempts + 1 >= MAX_ATTEMPTS) {
                CrudUtil.execute("DELETE FROM password_reset WHERE user_id=?", userId);
            } else {
                CrudUtil.execute("UPDATE password_reset SET attempts = attempts + 1 WHERE user_id=?", userId);
            }
            throw new InvalidPinException(GENERIC_ERROR);
        }

        // correct PIN: update password + delete used PIN in one transaction
        Connection con = DBConnection.getInstance().getConnection();
        try {
            con.setAutoCommit(false);
            boolean updated = CrudUtil.execute(
                    "UPDATE `user` SET password_hash=? WHERE user_id=?", PasswordUtil.hash(newPassword), userId);
            CrudUtil.execute("DELETE FROM password_reset WHERE user_id=?", userId);
            if (!updated) {
                con.rollback();
                throw new Exception("The password could not be changed.");
            }
            con.commit();
            return true;
        } catch (Exception e) {
            con.rollback();
            throw e;
        } finally {
            con.setAutoCommit(true);
        }
    }

    /* ======================= the user: ask for a PIN ======================= */
    /**
     * Called by the "Request PIN" button on the Forgot Password page (no sign-in needed).
     * Always returns true so nobody can enumerate existing usernames.
     */
    public boolean requestPin(String username) throws Exception {
        if (username == null || username.trim().isEmpty()) {
            throw new InvalidPinException("Enter your username first.");
        }
        ResultSet rs = CrudUtil.execute("SELECT user_id FROM `user` WHERE username=?", username.trim());
        if (!rs.next()) {
            return true;
        }
        String userId = rs.getString(1);

        // one request per user: a new request replaces the old one
        CrudUtil.execute("DELETE FROM pin_request WHERE user_id=?", userId);
        CrudUtil.execute("INSERT INTO pin_request (user_id, requested_at) VALUES (?,?)", userId, LocalDateTime.now());
        return true;
    }

    /* ======================= Super Admin: the inbox ======================= */
    /** Everybody waiting for a PIN, oldest first. Requests older than 24 hours are removed. */
    public List<PinRequest> getPendingRequests() throws Exception {
        Permissions.requireSuperAdmin("view PIN requests");

        LocalDateTime now = LocalDateTime.now();
        CrudUtil.execute("DELETE FROM pin_request WHERE requested_at < ?", now.minusHours(24));

        ResultSet rs = CrudUtil.execute(
                "SELECT r.user_id, u.username, u.full_name, u.role, r.requested_at "
                        + "FROM pin_request r JOIN `user` u ON u.user_id = r.user_id ORDER BY r.requested_at");
        List<PinRequest> list = new ArrayList<>();
        while (rs.next()) {
            LocalDateTime at = rs.getObject("requested_at", LocalDateTime.class);
            int minutes = at == null ? 0 : (int) Math.max(0, Duration.between(at, now).toMinutes());
            list.add(new PinRequest(rs.getString("user_id"), rs.getString("username"),
                    rs.getString("full_name"), rs.getString("role"), minutes));
        }
        return list;
    }

    /** For a badge on the bell. Returns 0 for anybody who is not the Super Admin. */
    public int getPendingRequestCount() throws Exception {
        if (!Permissions.isSuperAdmin()) {
            return 0;
        }
        ResultSet rs = CrudUtil.execute("SELECT COUNT(*) FROM pin_request");
        return rs.next() ? rs.getInt(1) : 0;
    }

    /** Removes a request without generating a PIN. */
    public void dismissRequest(String userId) throws Exception {
        Permissions.requireSuperAdmin("dismiss PIN requests");
        CrudUtil.execute("DELETE FROM pin_request WHERE user_id=?", userId);
    }

    /** How many PINs are valid right now (for the dashboard number). */
    public int countActive() throws Exception {
        if (!Permissions.isSuperAdmin()) {
            return 0;
        }
        ResultSet rs = CrudUtil.execute(
                "SELECT COUNT(*) FROM password_reset WHERE expires_at > ? AND attempts < ?",
                LocalDateTime.now(), MAX_ATTEMPTS);
        return rs.next() ? rs.getInt(1) : 0;
    }

    /** Users who have a valid PIN right now (shown as a chip on their card). */
    public Set<String> getUserIdsWithActivePin() throws Exception {
        Set<String> ids = new HashSet<>();
        if (!Permissions.isSuperAdmin()) {
            return ids;
        }
        ResultSet rs = CrudUtil.execute(
                "SELECT user_id FROM password_reset WHERE expires_at > ? AND attempts < ?",
                LocalDateTime.now(), MAX_ATTEMPTS);
        while (rs.next()) {
            ids.add(rs.getString(1));
        }
        return ids;
    }
}