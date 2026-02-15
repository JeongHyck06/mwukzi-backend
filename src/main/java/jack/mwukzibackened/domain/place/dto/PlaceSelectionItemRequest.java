package jack.mwukzibackened.domain.place.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PlaceSelectionItemRequest {
    @JsonAlias({"place_name", "placeName"})
    private String placeName;

    @JsonAlias({"provider_place_id", "providerPlaceId"})
    private String providerPlaceId;
}
