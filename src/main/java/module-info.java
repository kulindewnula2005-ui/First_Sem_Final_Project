module lk.ijse.firstsemfinalproject25 {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires static lombok;

    opens lk.ijse.firstsemfinalproject25 to javafx.fxml;
    opens lk.ijse.firstsemfinalproject25.controller to javafx.fxml;
    opens lk.ijse.firstsemfinalproject25.dto to javafx.base;
    opens lk.ijse.firstsemfinalproject25.tm to javafx.base;

    exports lk.ijse.firstsemfinalproject25;
}