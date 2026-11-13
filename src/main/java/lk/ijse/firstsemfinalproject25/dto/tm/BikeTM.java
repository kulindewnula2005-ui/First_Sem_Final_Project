package lk.ijse.firstsemfinalproject25.dto.tm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class BikeTM {

    private String bikeId;
    private String customerId;
    private String customerName;
    private String regNumber;
    private String brand;
    private String model;
    private int year;
    private String engineNo;
    private String chassisNo;
}