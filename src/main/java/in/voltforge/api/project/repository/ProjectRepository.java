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

    Page<Project> findByOwnerId(String ownerId, Pageable pageable);

    Page<Project> findByIsPublicTrue(Pageable pageable);

    @Query("SELECT p FROM Project p WHERE p.isPublic = true AND " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.tags) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Project> searchPublicProjects(@Param("query") String query, Pageable pageable);

    @Query("SELECT p FROM Project p WHERE p.isPublic = true AND LOWER(p.tags) LIKE '%template%'")
    Page<Project> findTemplates(Pageable pageable);

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
