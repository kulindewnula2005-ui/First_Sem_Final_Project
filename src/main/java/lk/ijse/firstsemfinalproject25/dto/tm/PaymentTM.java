package lk.ijse.firstsemfinalproject25.dto.tm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor

public class PaymentTM {

    private String paymentId;
    private String billId;
    private String method;
    private LocalDate paymentDate;
    private double amount;
}
