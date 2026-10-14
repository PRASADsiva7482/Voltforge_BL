package in.voltforge.api.user.repository;

public record UserStatsRow(
        long totalUsers,
        long activeUsers,
        long newUsersToday,
        long adminUsers
) {}
