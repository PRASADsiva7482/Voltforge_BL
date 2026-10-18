package in.voltforge.api.ai.conversation;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversation c where c.id = :id and c.ownerId = :owner")
    Optional<Conversation> lockOwned(@Param("id") String id, @Param("owner") String owner);
    @Query("select c from Conversation c where c.ownerId = :owner and (:project is null or c.projectId = :project) and lower(c.title) like :search escape '\\'")
    Page<Conversation> search(@Param("owner") String owner, @Param("project") String project, @Param("search") String search, Pageable page);
}
