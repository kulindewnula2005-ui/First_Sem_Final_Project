package lk.ijse.firstsemfinalproject25.util;

import lk.ijse.firstsemfinalproject25.dto.UserDTO;
import lk.ijse.firstsemfinalproject25.model.UserModel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Who is using the application right now. */
public final class Session {

    private static UserDTO user;
    private static final List<Consumer<UserDTO>> LISTENERS = new ArrayList<>();

    private Session() { }

    public static synchronized UserDTO getUser() {
        return user;
    }

    /** Set after login (or after the profile is edited). Listeners are told, e.g. the dashboard name label. */
    public static void setUser(UserDTO newUser) {
        synchronized (Session.class) {
            user = newUser;
        }
        for (Consumer<UserDTO> listener : new ArrayList<>(LISTENERS)) {
            listener.accept(newUser);
        }
    }

    public static void addListener(Consumer<UserDTO> listener) {
        LISTENERS.add(listener);
    }

    /** Until a login screen exists: the first Admin account (or the first account) counts as signed in. */
    public static UserDTO ensureUser() throws Exception {
        synchronized (Session.class) {
            if (user != null) {
                return user;
            }
        }
        List<UserDTO> all = new UserModel().getAllUsers();
        UserDTO found = null;
        for (UserDTO u : all) {
            if ("Admin".equals(u.getRole())) {
                found = u;
                break;
            }
        }
        if (found == null && !all.isEmpty()) {
            found = all.get(0);
        }
        synchronized (Session.class) {
            if (user == null) {
                user = found;
            }
            return user;
        }
    }
}