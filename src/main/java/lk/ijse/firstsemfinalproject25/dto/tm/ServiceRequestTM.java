package lk.ijse.firstsemfinalproject25.dto.tm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor

public class ServiceRequestTM {

    private String serviceId;
    private String bikeId;
    private String plate;
    private String bikeName;
    private String customerName;
    private String complaint;
    private String serviceType;
    private String mechanicId;
    private String mechanicName;
    private String status;
    private String dateReceived;

}
