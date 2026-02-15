package jack.mwukzibackened.domain.place.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PlaceSelectionSummaryResponse {
    private boolean allCompleted;
    private boolean myCompleted;
    private int totalSelectedCount;
    private List<String> candidateNames;
    private List<PlaceSelectionParticipantStatusResponse> participants;
}
