package jack.mwukzibackened.domain.ai.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MenuRecommendationResponse {
    private String summary;
    private String commonGround;
    private String compromise;
    private List<MenuItem> menus;

    @Getter
    @Builder
    public static class MenuItem {
        private String name;
        private String reason;
    }
}
