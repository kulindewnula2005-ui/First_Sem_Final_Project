package lk.ijse.firstsemfinalproject25.dto.tm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor

public class ServicePartsTM {

    private String serviceId;
    private String partId;
    private int quantity;
    private double unitPrice;
}
