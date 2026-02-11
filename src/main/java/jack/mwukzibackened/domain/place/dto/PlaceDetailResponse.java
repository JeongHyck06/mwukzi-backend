package jack.mwukzibackened.domain.place.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PlaceDetailResponse {
    private String provider;
    private String providerPlaceId;
    private String name;
    private String category;
    private String address;
    private String roadAddress;
    private String phone;
    private Integer distanceMeters;
    private Double latitude;
    private Double longitude;
    private String placeUrl;
    private String sourceKeyword;
    private String imageUrl;
    private List<String> imageUrls;
}
