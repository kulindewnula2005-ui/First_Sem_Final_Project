package lk.ijse.firstsemfinalproject25.dto.tm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor

public class SparePartsTM {

    private String partId;
    private String partName;
    private double unitPrice;
    private int stockQty;
    private int reorderLevel;

}
