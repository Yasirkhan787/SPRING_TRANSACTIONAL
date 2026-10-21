package com.yasirkhan.transactional.dtos;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ErrorResponse(int statusCode, String message, LocalDateTime timestamp)  {
}
