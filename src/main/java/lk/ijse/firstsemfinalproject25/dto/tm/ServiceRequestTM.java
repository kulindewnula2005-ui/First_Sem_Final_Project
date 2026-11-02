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
    private String mechanicId;
    private String complaint;
    private String status;
    private LocalDate dateReceived;
    private LocalDate dateCompleted;
}
