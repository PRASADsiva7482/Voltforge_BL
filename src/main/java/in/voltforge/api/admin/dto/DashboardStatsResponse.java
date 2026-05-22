package in.voltforge.api.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsResponse {
    private long totalUsers;
    private long activeUsers;
    private long newUsersToday;
    private long totalProjects;
    private long publicProjects;
    private long newProjectsToday;
    private Map<String, Long> roleBreakdown;
}
