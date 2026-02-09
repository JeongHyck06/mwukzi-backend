package jack.mwukzibackened.domain.room.dto;

import jack.mwukzibackened.domain.room.RoomStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class JoinRoomResponse {
    private UUID roomId;
    private String inviteCode;
    private UUID participantId;
    private String displayName;
    private RoomStatus roomStatus;
}
