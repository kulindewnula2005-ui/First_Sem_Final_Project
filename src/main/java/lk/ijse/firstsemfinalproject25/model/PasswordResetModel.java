package lk.ijse.firstsemfinalproject25.model;

import lk.ijse.firstsemfinalproject25.util.CrudUtil;
import lk.ijse.firstsemfinalproject25.dto.UserDTO;
import lk.ijse.firstsemfinalproject25.util.PasswordUtil;
import lk.ijse.firstsemfinalproject25.util.Session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Forgot password with a shared PIN.
 *
 *  1) The ADMIN generates a 6-digit PIN for the user (createPin) and shares it privately.
 *  2) The USER enters username + PIN + a new password on the Forgot Password page (resetPassword).
 *
 * Safety rules: the PIN is random, stored only as a hash, valid for 10 minutes,
 * can be used once, and is cancelled after 5 wrong tries.
 */
public class PasswordResetModel {

    public static final int PIN_LENGTH = 6;
    public static final int VALID_MINUTES = 10;
    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserModel userModel = new UserModel();

    /** Same message for "no such user", "wrong PIN" and "expired" so nothing is leaked. */
    public static class InvalidPinException extends Exception {
        public InvalidPinException() {
            super("Invalid username or PIN, or the PIN has expired. Ask the administrator for a new PIN.");
        }
    }

    /* ===================== ADMIN: make a PIN ===================== */

    /** Creates a fresh PIN for the user and returns it (the only time it is ever visible). */
    public String createPin(String userId) throws Exception {
        Session.requireSuperAdmin();                       // only the Super Admin can make PINs

        UserDTO target = userModel.searchUser(userId);
        if (target == null) {
            throw new Exception("User not found.");
        }
        if (UserModel.SUPER_ADMIN.equals(target.getRole())) {
            throw new Exception("The Super Admin password cannot be reset with a PIN.");
        }
        String pin = String.format("%0" + PIN_LENGTH + "d", RANDOM.nextInt((int) Math.pow(10, PIN_LENGTH)));

        clear(userId);   // a new PIN replaces any old one
        boolean saved = CrudUtil.execute(
                "INSERT INTO password_reset (user_id, pin_hash, expires_at, attempts) "
                        + "VALUES (?, ?, DATE_ADD(NOW(), INTERVAL " + VALID_MINUTES + " MINUTE), 0)",
                userId, hashPin(userId, pin));
        if (!saved) {
            throw new Exception("Could not create the PIN.");
        }
        return pin;
    }

    /* ===================== USER: use the PIN ===================== */

    /** @throws InvalidPinException when the username / PIN is wrong or expired */
    public boolean resetPassword(String username, String pin, String newPassword) throws Exception {
        if (username == null || pin == null || !pin.matches("\\d{" + PIN_LENGTH + "}")) {
            throw new InvalidPinException();
        }
        String userId = userModel.getIdByUsername(username);
        if (userId == null) {
            throw new InvalidPinException();
        }

        ResultSet rs = CrudUtil.execute(
                "SELECT pin_hash, attempts, (expires_at > NOW()) AS still_valid "
                        + "FROM password_reset WHERE user_id=?", userId);
        if (!rs.next()) {
            throw new InvalidPinException();
        }
        String storedHash = rs.getString("pin_hash");
        int attempts = rs.getInt("attempts");
        boolean stillValid = rs.getBoolean("still_valid");

        if (!stillValid || attempts >= MAX_ATTEMPTS) {
            clear(userId);
            throw new InvalidPinException();
        }

        boolean pinOk = MessageDigest.isEqual(
                hashPin(userId, pin).getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
        if (!pinOk) {
            if (attempts + 1 >= MAX_ATTEMPTS) {
                clear(userId);                      // too many wrong tries - PIN is cancelled
            } else {
                CrudUtil.execute("UPDATE password_reset SET attempts = attempts + 1 WHERE user_id=?", userId);
            }
            throw new InvalidPinException();
        }

        clear(userId);                              // one-time: use up the PIN first ...
        return userModel.changePassword(userId, newPassword);   // ... then set the new password
    }

    /* ===================== live numbers for the Super Admin dashboard ===================== */

    /** How many PINs are valid right now. */
    public int countActive() throws Exception {
        ResultSet rs = CrudUtil.execute(
                "SELECT COUNT(*) FROM password_reset WHERE expires_at > NOW() AND attempts < " + MAX_ATTEMPTS);
        rs.next();
        return rs.getInt(1);
    }

    /** Users who have a valid PIN right now (shown as a chip on their profile card). */
    public Set<String> getUserIdsWithActivePin() throws Exception {
        ResultSet rs = CrudUtil.execute(
                "SELECT user_id FROM password_reset WHERE expires_at > NOW() AND attempts < " + MAX_ATTEMPTS);
        Set<String> ids = new HashSet<>();
        while (rs.next()) {
            ids.add(rs.getString(1));
        }
        return ids;
    }

    /* ===================== helpers ===================== */

    private void clear(String userId) throws Exception {
        CrudUtil.execute("DELETE FROM password_reset WHERE user_id=?", userId);
    }

    /** The user id is mixed in so the same PIN gives a different hash for every user. */
    private String hashPin(String userId, String pin) throws Exception {
        return PasswordUtil.hash(userId + ":" + pin);
    }
}
