package lk.ijse.firstsemfinalproject25.util;

import lk.ijse.firstsemfinalproject25.dto.UserDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Who is using the application right now. The login screen must call Session.setUser(user). */
public final class Session {

    private static UserDTO user;
    private static final List<Consumer<UserDTO>> LISTENERS = new ArrayList<>();

    private Session() { }

    public static synchronized UserDTO getUser() {
        return user;
    }

    /** Call after a successful login (and after the profile is edited). Listeners are told, e.g. the dashboard name label. */
    public static void setUser(UserDTO newUser) {
        synchronized (Session.class) {
            user = newUser;
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