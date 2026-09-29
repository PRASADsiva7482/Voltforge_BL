package in.voltforge.api.project.repository;

import in.voltforge.api.project.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, String> {

    String SUMMARY_SELECT = """
            SELECT new in.voltforge.api.project.repository.ProjectSummaryRow(
                p.id, p.name, p.description, p.boardType, p.isPublic, p.forkCount, p.viewCount,
                p.thumbnailUrl, p.tags, p.createdAt, p.updatedAt,
                o.id, o.keycloakId, o.username, o.email, o.displayName, o.avatarUrl, o.bio,
                o.role, o.accountStatus, o.createdAt, o.updatedAt)
            FROM Project p LEFT JOIN p.owner o
            """;

    String PUBLIC_SEARCH = "p.isPublic = true AND " +
            "(LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(p.tags) LIKE LOWER(CONCAT('%', :query, '%')))";

    @Query(value = SUMMARY_SELECT + " WHERE p.owner.id = :ownerId",
            countQuery = "SELECT COUNT(p) FROM Project p WHERE p.owner.id = :ownerId")
    Page<ProjectSummaryRow> findByOwnerId(@Param("ownerId") String ownerId, Pageable pageable);

    @Query(value = SUMMARY_SELECT + " WHERE p.isPublic = true",
            countQuery = "SELECT COUNT(p) FROM Project p WHERE p.isPublic = true")
    Page<ProjectSummaryRow> findByIsPublicTrue(Pageable pageable);

    @Query(value = SUMMARY_SELECT + " WHERE " + PUBLIC_SEARCH,
            countQuery = "SELECT COUNT(p) FROM Project p WHERE " + PUBLIC_SEARCH)
    Page<ProjectSummaryRow> searchPublicProjects(@Param("query") String query, Pageable pageable);

    @Query(value = SUMMARY_SELECT + " WHERE p.isPublic = true AND LOWER(p.tags) LIKE '%template%'",
            countQuery = "SELECT COUNT(p) FROM Project p WHERE p.isPublic = true AND LOWER(p.tags) LIKE '%template%'")
    Page<ProjectSummaryRow> findTemplates(Pageable pageable);

    List<Project> findByForkedFromId(String projectId);

    List<Project> findByOwnerIdAndForkedFromId(String ownerId, String forkedFromId);

    long countByOwnerId(String ownerId);

    @Query("SELECT COUNT(p) FROM Project p")
    long countAllProjects();

    @Query("SELECT COUNT(p) FROM Project p WHERE p.isPublic = true")
    long countPublicProjects();

    @Query("SELECT COUNT(p) FROM Project p WHERE p.createdAt >= CURRENT_DATE")
    long countNewProjectsToday();

    @Modifying
    @Query("UPDATE Project p SET p.viewCount = p.viewCount + 1, p.updatedAt = p.updatedAt WHERE p.id = :projectId")
    void incrementViewCount(@Param("projectId") String projectId);

    @Modifying
    @Query("UPDATE Project p SET p.forkCount = p.forkCount + 1, p.updatedAt = p.updatedAt WHERE p.id = :projectId")
    void incrementForkCount(@Param("projectId") String projectId);
}
