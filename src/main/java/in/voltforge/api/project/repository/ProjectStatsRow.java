package in.voltforge.api.project.repository;

public record ProjectStatsRow(
        long totalProjects,
        long publicProjects,
        long newProjectsToday
) {}
