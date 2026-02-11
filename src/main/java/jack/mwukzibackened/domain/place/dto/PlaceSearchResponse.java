package jack.mwukzibackened.domain.place.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PlaceSearchResponse {
    private List<PlaceItem> places;

    @Getter
    @Builder
    public static class PlaceItem {
        private String name;
        private String category;
        private String address;
        private String roadAddress;
        private String link;
        private Double latitude;
        private Double longitude;
        private Integer distanceMeters;
        private String matchedMenu;
    }
}
