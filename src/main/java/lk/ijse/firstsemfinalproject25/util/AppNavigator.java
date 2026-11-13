package lk.ijse.firstsemfinalproject25.util;

import java.util.function.Consumer;

/** Lets any page ask the dashboard to open another page. */
public final class AppNavigator {

    private static Consumer<String> handler;
    private static String pendingBikeId;

    private AppNavigator() { }

    /** Set once by DashboardController. */
    public static void setHandler(Consumer<String> newHandler) {
        handler = newHandler;
    }

    /** Opens a page by its id ("service", "customer", "bike" ...). */
    public static void go(String page) {
        if (handler != null) {
            handler.accept(page);
        }
    }

    /** Opens the Service page with this bike already selected. */
    public static void goToService(String bikeId) {
        pendingBikeId = bikeId;
        go("service");
    }

    /** The Service page calls this once; it returns the bike to pre-select (or null). */
    public static String takePendingBikeId() {
        String id = pendingBikeId;
        pendingBikeId = null;
        return id;
    }
}