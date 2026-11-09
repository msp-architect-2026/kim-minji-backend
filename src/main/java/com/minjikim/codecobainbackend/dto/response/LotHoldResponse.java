package com.minjikim.codecobainbackend.dto.response;

import com.minjikim.codecobainbackend.entity.HoldSource;
import com.minjikim.codecobainbackend.entity.LotHoldEntity;

import java.time.LocalDateTime;

public record LotHoldResponse(
        Long id,
        HoldSource source,
        String reason,
        String heldBy,
        LocalDateTime heldAt,
        String releasedBy,
        String releaseComment,
        LocalDateTime releasedAt
) {
    public static LotHoldResponse from(LotHoldEntity hold) {
        return new LotHoldResponse(hold.getId(), hold.getSource(), hold.getReason(), hold.getHeldBy(), hold.getHeldAt(),
                hold.getReleasedBy(), hold.getReleaseComment(), hold.getReleasedAt());
    }
}
