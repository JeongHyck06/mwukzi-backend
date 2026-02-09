package jack.mwukzibackened.domain.room.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class LeaveRoomRequest {
    private UUID roomId;
    private UUID participantId;
}
