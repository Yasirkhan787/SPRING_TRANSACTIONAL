package com.yasirkhan.transactional.dtos;

import com.yasirkhan.transactional.entities.UserContact;
import lombok.Builder;

import java.util.List;

@Builder
public record UserResponse(
        Long id,
        String name,
        double balance,
        List<UserContact> contacts
) {
}
