package in.voltforge.api.ai.memory;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface UserMemoryRepository extends JpaRepository<UserMemory,String> {
    List<UserMemory> findByOwnerIdOrderByPreferenceKey(String ownerId);
    Optional<UserMemory> findByOwnerIdAndPreferenceKey(String ownerId,String key);
}
