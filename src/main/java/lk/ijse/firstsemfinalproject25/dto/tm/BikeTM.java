package lk.ijse.firstsemfinalproject25.dto.tm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor

public class BikeTM {

    private String bikeId;
    private String customerId;
    private String regNumber;
    private String brand;
    private String model;
    private int year;
}
