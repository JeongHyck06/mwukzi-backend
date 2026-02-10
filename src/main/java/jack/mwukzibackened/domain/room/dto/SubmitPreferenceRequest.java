package jack.mwukzibackened.domain.room.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class SubmitPreferenceRequest {
    private UUID participantId;
    private List<String> chips;
    private String freeText;
}
