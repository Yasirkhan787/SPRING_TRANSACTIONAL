package com.yasirkhan.transactional.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;

import java.util.List;

@Builder
public record UserRequest(
        @NotBlank(message = "Name cannot be blank")
        String name,

        Double balance,

        @NotEmpty(message = "At least one phone number must be provided")
        List<String> contacts
) {
    // Compact constructor to set the default value
    public UserRequest {
        if (balance == null) {
            balance = 0.0;
        }
    }
}
