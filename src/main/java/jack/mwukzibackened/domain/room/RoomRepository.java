package jack.mwukzibackened.domain.room;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomRepository extends JpaRepository<Room, UUID> {
    Optional<Room> findByInviteCode(String inviteCode);
    boolean existsByInviteCode(String inviteCode);
}
