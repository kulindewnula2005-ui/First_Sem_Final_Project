package lk.ijse.firstsemfinalproject25;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class AppInitializer extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        // The app now starts on the login page.
        // After a successful login, LoginController opens DashboardPage.fxml
        // (and sets the 1100 x 650 minimum size + stops the dashboard timers on close).
        Parent root = FXMLLoader.load(getClass().getResource("/view/login.fxml"));

        stage.setTitle("Bike Garage - Login");
        stage.setScene(new Scene(root));
        stage.setResizable(false);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}