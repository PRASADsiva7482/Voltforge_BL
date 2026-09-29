package in.voltforge.api.project.repository;

import in.voltforge.api.common.enums.AccountStatus;
import in.voltforge.api.common.enums.BoardType;
import in.voltforge.api.common.enums.UserRole;

import java.time.LocalDateTime;

/** Scalar list projection: never hydrates project documents or lazy collections. */
public record ProjectSummaryRow(
        String id, String name, String description, BoardType boardType,
        Boolean isPublic, Integer forkCount, Integer viewCount, String thumbnailUrl, String tags,
        LocalDateTime createdAt, LocalDateTime updatedAt,
        String ownerId, String ownerKeycloakId, String ownerUsername, String ownerEmail,
        String ownerDisplayName, String ownerAvatarUrl, String ownerBio, UserRole ownerRole,
        AccountStatus ownerAccountStatus, LocalDateTime ownerCreatedAt, LocalDateTime ownerUpdatedAt
) {}
