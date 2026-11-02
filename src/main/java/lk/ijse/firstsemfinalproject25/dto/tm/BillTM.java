package lk.ijse.firstsemfinalproject25.dto.tm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor

public class BillTM {

    private String billId;
    private String serviceId;
    private LocalDate billDate;
    private double labourCharge;
    private double partsTotal;
    private double totalAmount;

}
