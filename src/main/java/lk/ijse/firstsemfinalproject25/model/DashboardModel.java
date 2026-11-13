package lk.ijse.firstsemfinalproject25.model;

import lk.ijse.firstsemfinalproject25.util.CrudUtil;

import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class DashboardModel {

        /** One row of the "Recent Service Jobs" table. */
        public record JobRow(String plate, String model, String status, String date) {}

        /** Everything the dashboard needs, loaded in one go. */
        public record DashboardData(int totalCustomers, int customersToday,
                                    int totalBikes, int bikesToday,
                                    int pending, int inProgress, int completedToday,
                                    List<JobRow> recentJobs) {}

        public DashboardData load() throws Exception {
            LocalDate today = LocalDate.now();

            int totalCustomers = count("SELECT COUNT(*) FROM customer");
            int totalBikes = count("SELECT COUNT(*) FROM bike");

            // customers whose bikes came in today
            int customersToday = count(
                    "SELECT COUNT(DISTINCT m.customer_id) FROM service_request s "
                            + "JOIN bike m ON m.bike_id = s.bike_id WHERE s.date_received = ?", today);

            // bikes that came in today
            int bikesToday = count(
                    "SELECT COUNT(DISTINCT bike_id) FROM service_request WHERE date_received = ?", today);

            int pending = count("SELECT COUNT(*) FROM service_request WHERE status = ?",
                    ServiceRequestModel.PENDING);
            int inProgress = count("SELECT COUNT(*) FROM service_request WHERE status = ?",
                    ServiceRequestModel.IN_PROGRESS);
            int completedToday = count(
                    "SELECT COUNT(*) FROM service_request WHERE status = 'Completed' AND date_completed = ?", today);

            return new DashboardData(totalCustomers, customersToday, totalBikes, bikesToday,
                    pending, inProgress, completedToday, getRecentJobs(5));
        }

        /** Latest jobs with the bike's plate and model. */
        public List<JobRow> getRecentJobs(int limit) throws Exception {
            ResultSet rs = CrudUtil.execute(
                    "SELECT m.reg_number, CONCAT(m.brand, ' ', m.model) AS bike_model, s.status, s.date_received "
                            + "FROM service_request s JOIN bike m ON m.bike_id = s.bike_id "
                            + "ORDER BY s.date_received DESC, s.service_id DESC LIMIT ?", limit);
            List<JobRow> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(new JobRow(
                        rs.getString("reg_number"),
                        rs.getString("bike_model"),
                        rs.getString("status"),
                        String.valueOf(rs.getObject("date_received", LocalDate.class))));
            }
            return rows;
        }

        private int count(String sql, Object... params) throws Exception {
            ResultSet rs = CrudUtil.execute(sql, params);
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

