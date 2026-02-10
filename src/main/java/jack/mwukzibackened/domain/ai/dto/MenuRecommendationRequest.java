package jack.mwukzibackened.domain.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class MenuRecommendationRequest {

    @Valid
    @NotEmpty(message = "참여자 취향 정보가 필요합니다")
    private List<ParticipantPreferenceInput> participants;

    private Integer count;

    @Getter
    @NoArgsConstructor
    public static class ParticipantPreferenceInput {
        private String name;
        private String preference;
    }
}
