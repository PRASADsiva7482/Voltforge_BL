package in.voltforge.api.admin.service.impl;

import in.voltforge.api.admin.dto.DashboardStatsResponse;
import in.voltforge.api.admin.service.AdminService;
import in.voltforge.api.common.enums.UserRole;
import in.voltforge.api.config.CacheConfig;
import in.voltforge.api.project.repository.ProjectRepository;
import in.voltforge.api.project.repository.ProjectStatsRow;
import in.voltforge.api.user.repository.UserRepository;
import in.voltforge.api.user.repository.UserStatsRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;

    @Override
    @Cacheable(CacheConfig.CACHE_ADMIN_STATS)
    @Transactional(readOnly = true)
    public DashboardStatsResponse getDashboardStats() {
        UserStatsRow userStats = userRepository.getUserStatsAggregate();
        ProjectStatsRow projectStats = projectRepository.getProjectStatsAggregate();

        long totalUsers = userStats != null ? userStats.totalUsers() : 0;
        long adminUsers = userStats != null ? userStats.adminUsers() : 0;
        long regularUsers = Math.max(0, totalUsers - adminUsers);

        Map<String, Long> roleBreakdown = Map.of(
                UserRole.ADMIN.name(), adminUsers,
                UserRole.USER.name(), regularUsers
        );

        return DashboardStatsResponse.builder()
                .totalUsers(totalUsers)
                .activeUsers(userStats != null ? userStats.activeUsers() : 0)
                .newUsersToday(userStats != null ? userStats.newUsersToday() : 0)
                .totalProjects(projectStats != null ? projectStats.totalProjects() : 0)
                .publicProjects(projectStats != null ? projectStats.publicProjects() : 0)
                .newProjectsToday(projectStats != null ? projectStats.newProjectsToday() : 0)
                .roleBreakdown(roleBreakdown)
                .build();
    }
}
