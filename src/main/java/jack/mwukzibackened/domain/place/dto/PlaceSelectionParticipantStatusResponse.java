package jack.mwukzibackened.domain.place.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class PlaceSelectionParticipantStatusResponse {
    private UUID participantId;
    private String displayName;
    private boolean completed;
}
