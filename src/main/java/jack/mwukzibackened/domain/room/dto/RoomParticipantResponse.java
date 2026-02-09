package jack.mwukzibackened.domain.room.dto;

import jack.mwukzibackened.domain.participant.ParticipantRole;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class RoomParticipantResponse {
    private UUID participantId;
    private String displayName;
    private ParticipantRole role;
    private boolean hasSubmitted;
}
