package jack.mwukzibackened.domain.place.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class PlaceSearchRequest {

    @NotEmpty(message = "추천 메뉴 정보가 필요합니다")
    private List<String> menus;

    @DecimalMin(value = "-90.0", message = "위도 범위가 올바르지 않습니다")
    @DecimalMax(value = "90.0", message = "위도 범위가 올바르지 않습니다")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "경도 범위가 올바르지 않습니다")
    @DecimalMax(value = "180.0", message = "경도 범위가 올바르지 않습니다")
    private Double longitude;

    @Min(value = 1, message = "최대 개수는 1 이상이어야 합니다")
    @Max(value = 20, message = "최대 개수는 20 이하여야 합니다")
    private Integer maxItems;
}
