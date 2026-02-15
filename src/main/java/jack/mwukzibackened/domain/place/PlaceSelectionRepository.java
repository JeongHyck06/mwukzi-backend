package jack.mwukzibackened.domain.place;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PlaceSelectionRepository extends JpaRepository<PlaceSelection, UUID> {
    List<PlaceSelection> findByRoomId(UUID roomId);
    List<PlaceSelection> findByRoomIdAndParticipantId(UUID roomId, UUID participantId);
    void deleteByRoomIdAndParticipantId(UUID roomId, UUID participantId);
}
