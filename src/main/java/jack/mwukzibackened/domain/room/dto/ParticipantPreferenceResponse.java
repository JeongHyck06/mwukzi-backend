package jack.mwukzibackened.domain.room.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class ParticipantPreferenceResponse {
    private UUID participantId;
    private String displayName;
    private boolean hasSubmitted;
    private String preferenceText;
}
