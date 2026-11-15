package lk.ijse.firstsemfinalproject25.model;

import lk.ijse.firstsemfinalproject25.db.DBConnection;
import lk.ijse.firstsemfinalproject25.dto.UserDTO;
import lk.ijse.firstsemfinalproject25.util.CrudUtil;
import lk.ijse.firstsemfinalproject25.util.PasswordUtil;
import lk.ijse.firstsemfinalproject25.util.Permissions;
import lk.ijse.firstsemfinalproject25.util.Session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.ResultSet;
import java.time.LocalDateTime;

/**
 * Forgot-password flow:
 *   1. The Super Admin generates a one-time PIN for a user (generatePin).
 *   2. The Super Admin tells the user the PIN.
 *   3. The user enters username + PIN + new password on the Reset Password screen (resetPassword).
 */
public class PasswordResetModel {

    public static final int PIN_LENGTH = 6;       // digits in a PIN
    public static final int VALID_MINUTES = 15;   // how long a PIN works
    public static final int PIN_MINUTES = VALID_MINUTES;   // same value, older name
    public static final int MAX_ATTEMPTS = 5;     // wrong tries before the PIN is cancelled
    public static final int MIN_PASSWORD = 6;     // same rule as the Reset Password screen

    /** The same message for an unknown user, a wrong PIN and an expired PIN, so nothing is revealed. */
    public static final String GENERIC_ERROR =
            "Invalid username or PIN, or the PIN has expired. Ask the administrator for a new PIN.";

    /**
     * Thrown when the details the person typed cannot be accepted (wrong / expired PIN, unknown user,
     * password too short). The message is meant to be shown on the screen as it is.
     */
    public static class InvalidPinException extends Exception {
        public InvalidPinException(String message) {
            super(message);
        }
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    /* ======================= Super Admin: create a PIN ======================= */
    /** Same as createPin (older name). */
    public String generatePin(String userId) throws Exception {
        return createPin(userId);
    }

    /** Returns the new PIN (shown once). Any older PIN of this user stops working. Super Admin only. */
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
                userId, hashPin(userId, pin), me == null ? null : me.getUserId(), now, now.plusMinutes(VALID_MINUTES));
        if (!saved) {
            throw new Exception("The reset PIN could not be saved.");
        }
        return pin;
    }

    /* ======================= the user: use the PIN ======================= */
    /**
     * Returns true when the password was changed.
     * Throws InvalidPinException for anything the person typed wrong; any other Exception means a database problem.
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
        String storedHash = rs.getString("pin_hash");
        LocalDateTime expires = rs.getObject("expires_at", LocalDateTime.class);
        int attempts = rs.getInt("attempts");

        // expired, or too many wrong tries: the PIN is cancelled
        if (expires == null || LocalDateTime.now().isAfter(expires) || attempts >= MAX_ATTEMPTS) {
            CrudUtil.execute("DELETE FROM password_reset WHERE user_id=?", userId);
            throw new InvalidPinException(GENERIC_ERROR);
        }

        // wrong PIN: count the try
        boolean correct = MessageDigest.isEqual(
                storedHash.getBytes(StandardCharsets.UTF_8),
                hashPin(userId, pin.trim()).getBytes(StandardCharsets.UTF_8));
        if (!correct) {
            if (attempts + 1 >= MAX_ATTEMPTS) {
                CrudUtil.execute("DELETE FROM password_reset WHERE user_id=?", userId);
            } else {
                CrudUtil.execute("UPDATE password_reset SET attempts = attempts + 1 WHERE user_id=?", userId);
            }
            throw new InvalidPinException(GENERIC_ERROR);
        }

        // correct PIN: new password + the PIN is used up, in one transaction
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

    /** SHA-256 of "userId:pin". A short PIN is only safe because of the try limit and the time limit. */
    private String hashPin(String userId, String pin) throws Exception {
        return PasswordUtil.hash(userId + ":" + pin);
    }
}