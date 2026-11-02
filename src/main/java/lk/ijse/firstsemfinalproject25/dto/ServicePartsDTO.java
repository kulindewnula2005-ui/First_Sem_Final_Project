package lk.ijse.firstsemfinalproject25.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor

public class ServicePartsDTO {

    private String serviceId;
    private String partId;
    private int quantity;
    private double unitPrice;
}
