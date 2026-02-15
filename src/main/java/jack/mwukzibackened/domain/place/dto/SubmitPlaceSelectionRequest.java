package jack.mwukzibackened.domain.place.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class SubmitPlaceSelectionRequest {
    @JsonAlias({"participant_id", "participantId"})
    private UUID participantId;

    @JsonAlias({"places"})
    private List<PlaceSelectionItemRequest> places;
}
