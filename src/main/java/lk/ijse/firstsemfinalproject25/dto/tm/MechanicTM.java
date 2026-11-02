package lk.ijse.firstsemfinalproject25.dto.tm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor

public class MechanicTM {

    private String mechanicId;
    private String userId;
    private String name;
    private String specialization;
    private String phone;
}
