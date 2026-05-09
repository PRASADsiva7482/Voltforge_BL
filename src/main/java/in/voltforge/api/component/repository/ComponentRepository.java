package in.voltforge.api.component.repository;

import in.voltforge.api.component.entity.ElectronicComponent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComponentRepository extends JpaRepository<ElectronicComponent, String> {

    List<ElectronicComponent> findByCategoryOrderBySortOrderAsc(String category);

    List<ElectronicComponent> findByTypeOrderBySortOrderAsc(String type);

    List<ElectronicComponent> findByIsPremiumFalseOrderBySortOrderAsc();

    @Query("SELECT c FROM ElectronicComponent c WHERE " +
           "LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.description) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<ElectronicComponent> searchComponents(@Param("query") String query);

    @Query("SELECT DISTINCT c.category FROM ElectronicComponent c ORDER BY c.category")
    List<String> findAllCategories();
}
