package jack.mwukzibackened.domain.participant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ParticipantRepository extends JpaRepository<Participant, UUID> {
    List<Participant> findByRoomId(UUID roomId);
    boolean existsByRoomIdAndDisplayName(UUID roomId, String displayName);
    Optional<Participant> findByRoomIdAndUserId(UUID roomId, UUID userId);
    void deleteByRoomId(UUID roomId);
}
