package com.ayshriv.authplatform.dto;

import com.ayshriv.authplatform.entity.LogAction;
import com.ayshriv.authplatform.entity.LogSubAction;

import java.time.Instant;

public record UserLogResponse(
        Long id,
        Long userId,
        LogAction action,
        LogSubAction subAction,
        Instant createdOn
) {
}
