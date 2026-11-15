package lk.ijse.firstsemfinalproject25.util;

import lk.ijse.firstsemfinalproject25.dto.UserDTO;
import lk.ijse.firstsemfinalproject25.model.UserModel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Who is using the application right now. The login screen must call Session.setUser(user). */
public final class Session {

    private static UserDTO currentUser;
    private static final List<Consumer<UserDTO>> LISTENERS = new ArrayList<>();

    private Session() { }

    public static synchronized UserDTO getUser() {
        return currentUser;
    }

    public static void login(UserDTO user) { currentUser = user; }

    public static void logout() { currentUser = null; }


    public static boolean isSuperAdmin() {
        return currentUser != null && UserModel.SUPER_ADMIN.equals(currentUser.getRole());
    }

    /** Call at the start of any model method that only the Super Admin may use. */
    public static void requireSuperAdmin() throws Exception {
        if (!isSuperAdmin()) {
            throw new Exception("Only the Super Admin can do this.");
        }
    }

    /** Call after a successful login (and after the profile is edited). Listeners are told, e.g. the dashboard name label. */
    public static void setUser(UserDTO newUser) {
        synchronized (Session.class) {
            currentUser = newUser;
        }
        for (Consumer<UserDTO> listener : new ArrayList<>(LISTENERS)) {
            listener.accept(newUser);
        }
    }

    /** Call on logout. */
    public static void clear() {
        setUser(null);
    }

    public static void addListener(Consumer<UserDTO> listener) {
        LISTENERS.add(listener);
    }

    /**
     * Kept so older code still compiles. It only returns the signed-in user: there is no automatic
     * "first admin" fallback any more, because a login screen now decides who is signed in.
     */
    public static UserDTO ensureUser() throws Exception {
        return getUser();
    }
}