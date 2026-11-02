package lk.ijse.firstsemfinalproject25.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor

public class UserDto {
    private String userId;
    private String username;
    private String passwordHash;
    private String role;
    private String fullName;
}
