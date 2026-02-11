package jack.mwukzibackened.domain.place.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class PlaceSearchRequest {

    @DecimalMin(value = "-90.0", message = "위도 범위가 올바르지 않습니다")
    @DecimalMax(value = "90.0", message = "위도 범위가 올바르지 않습니다")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "경도 범위가 올바르지 않습니다")
    @DecimalMax(value = "180.0", message = "경도 범위가 올바르지 않습니다")
    private Double longitude;

    @Min(value = 300, message = "반경은 300m 이상이어야 합니다")
    @Max(value = 20000, message = "반경은 20km 이하여야 합니다")
    private Integer radiusMeters;

    @Min(value = 1, message = "키워드당 검색 개수는 1 이상이어야 합니다")
    @Max(value = 15, message = "키워드당 검색 개수는 15 이하여야 합니다")
    private Integer sizePerKeyword;

    private List<String> keywords;
}
