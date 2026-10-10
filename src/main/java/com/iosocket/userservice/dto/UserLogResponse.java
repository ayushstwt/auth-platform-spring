package com.iosocket.userservice.dto;

import com.iosocket.userservice.entity.LogAction;
import com.iosocket.userservice.entity.LogSubAction;

import java.time.Instant;

public record UserLogResponse(
        Long id,
        Long userId,
        LogAction action,
        LogSubAction subAction,
        Instant createdOn
) {
}
