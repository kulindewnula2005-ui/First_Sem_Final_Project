package lk.ijse.firstsemfinalproject25.util;

import lk.ijse.firstsemfinalproject25.dto.UserDTO;

import java.util.Set;

/**
 * Who may do what.
 *   - Everybody who is signed in can ADD records.
 *   - Only the Super Admin can UPDATE and DELETE records.
 */
public final class Permissions {

    private Permissions() { }

    public static final String SUPER_ADMIN = "Super Admin";
    public static final String RECEPTIONIST = "Receptionist";
    public static final String MECHANIC = "Mechanic";

    /**
     * Assigning a mechanic and moving a job Pending -> In Progress -> Completed is part of the daily workflow
     * (the receptionist assigns, the mechanic updates). true = everybody keeps doing this;
     * false = only the Super Admin can do it.
     */
    public static final boolean JOB_PROGRESS_FOR_ALL = true;

    /**
     * Pages each role may open. The names are the ids of the sidebar buttons
     * (dashboard, customer, bike, service, mechanic, spare_part, billing, reports, settings),
     * plus "scan" (the Scan Number Plate page, opened from the dashboard).
     * Super Admin and Admin may open every page.
     */
    private static final Set<String> MECHANIC_PAGES =
            Set.of("dashboard", "bike", "service", "mechanic", "settings", "scan");

    private static final Set<String> RECEPTIONIST_PAGES =
            Set.of("dashboard", "customer", "bike", "billing", "settings", "scan");



    /** true when the signed-in user may open this page (use the sidebar button id as the page name). */
    public static boolean canOpenPage(String page) {
        String role = currentRole();
        if (role == null || page == null) {
            return false;
        }
        if (SUPER_ADMIN.equals(role)) {
            return true;
        }
        if (MECHANIC.equals(role)) {
            return MECHANIC_PAGES.contains(page);
        }
        if (RECEPTIONIST.equals(role)) {
            return RECEPTIONIST_PAGES.contains(page);
        }
        return false;
    }


    public static String currentRole() {
        UserDTO user = Session.getUser();
        return user == null ? null : user.getRole();
    }

    public static boolean isSuperAdmin() {
        return SUPER_ADMIN.equals(currentRole());
    }

    public static boolean isAdminOrAbove() {
        String role = currentRole();
        return SUPER_ADMIN.equals(role);
    }

    /** Assign a mechanic / change a job's status. */
    public static boolean canProgressJobs() {
        return JOB_PROGRESS_FOR_ALL ? currentRole() != null : isSuperAdmin();
    }

    /**
     * Who can create an account with this role:
     * Super Admin - any role; Admin - Admin, Receptionist, Mechanic;
     * everybody else - only Mechanic (needed by "Add Mechanic" with a login account).
     */
    public static boolean canAddUser(String role) {
        String me = currentRole();
        if (me == null || role == null) {
            return false;
        }
        if (SUPER_ADMIN.equals(me)) {
            return true;
        }

        return MECHANIC.equals(role);
    }

    /** Throws when the signed-in user is not the Super Admin. Put it first in every update / delete method. */
    public static void requireSuperAdmin(String action) throws Exception {
        if (!isSuperAdmin()) {
            throw new Exception("Only the Super Admin can " + action + ".");
        }
    }
}