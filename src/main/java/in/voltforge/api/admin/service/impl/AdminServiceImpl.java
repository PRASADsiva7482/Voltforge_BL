package in.voltforge.api.admin.service.impl;

import in.voltforge.api.admin.dto.DashboardStatsResponse;
import in.voltforge.api.admin.service.AdminService;
import in.voltforge.api.common.enums.AccountStatus;
import in.voltforge.api.common.enums.SubscriptionType;
import in.voltforge.api.common.enums.UserRole;
import in.voltforge.api.project.repository.ProjectRepository;
import in.voltforge.api.subscription.repository.SubscriptionRepository;
import in.voltforge.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Override
    @Transactional(readOnly = true)
    public DashboardStatsResponse getDashboardStats() {
        // User stats
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByAccountStatus(AccountStatus.ACTIVE);
        long newUsersToday = userRepository.countNewUsersToday();

        // Project stats
        long totalProjects = projectRepository.countAllProjects();
        long publicProjects = projectRepository.countPublicProjects();
        long newProjectsToday = projectRepository.countNewProjectsToday();

        // Subscription breakdown
        Map<String, Long> subscriptionBreakdown = new HashMap<>();
        for (SubscriptionType type : SubscriptionType.values()) {
            subscriptionBreakdown.put(type.name(), userRepository.countBySubscriptionType(type));
        }

        // Role breakdown
        Map<String, Long> roleBreakdown = new HashMap<>();
        for (UserRole role : UserRole.values()) {
            roleBreakdown.put(role.name(), userRepository.countByRole(role));
        }

        return DashboardStatsResponse.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .newUsersToday(newUsersToday)
                .totalProjects(totalProjects)
                .publicProjects(publicProjects)
                .newProjectsToday(newProjectsToday)
                .subscriptionBreakdown(subscriptionBreakdown)
                .roleBreakdown(roleBreakdown)
                .build();
    }
}
