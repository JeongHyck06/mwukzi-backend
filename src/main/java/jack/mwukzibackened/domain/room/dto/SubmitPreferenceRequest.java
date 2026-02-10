package jack.mwukzibackened.domain.room.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class SubmitPreferenceRequest {
    @JsonAlias({"participant_id", "participantId"})
    private UUID participantId;

    @JsonAlias({"chips"})
    private List<String> chips;

    @JsonAlias({"free_text", "freeText"})
    private String freeText;
}
