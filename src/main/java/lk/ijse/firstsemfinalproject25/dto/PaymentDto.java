package lk.ijse.firstsemfinalproject25.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor

public class PaymentDto {

    private String paymentId;
    private String billId;
    private String method;
    private LocalDate paymentDate;
    private double amount;
}
