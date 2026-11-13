module lk.ijse.firstsemfinalproject25 {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires static lombok;
    requires com.github.librepdf.openpdf;
    requires java.desktop;
    requires webcam.capture;
    requires java.prefs;

    opens lk.ijse.firstsemfinalproject25 to javafx.fxml;
    opens lk.ijse.firstsemfinalproject25.controller to javafx.fxml;
    opens lk.ijse.firstsemfinalproject25.dto to javafx.base;
    opens lk.ijse.firstsemfinalproject25.dto.tm to javafx.base;

    exports lk.ijse.firstsemfinalproject25;
}