package jack.mwukzibackened.domain.place.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PlaceDetailRequest {

    @NotBlank(message = "place_name은 필수입니다")
    private String placeName;

    private String providerPlaceId;

    @DecimalMin(value = "-90.0", message = "위도 범위가 올바르지 않습니다")
    @DecimalMax(value = "90.0", message = "위도 범위가 올바르지 않습니다")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "경도 범위가 올바르지 않습니다")
    @DecimalMax(value = "180.0", message = "경도 범위가 올바르지 않습니다")
    private Double longitude;
}
