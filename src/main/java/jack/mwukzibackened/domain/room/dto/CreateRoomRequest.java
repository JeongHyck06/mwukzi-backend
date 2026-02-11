package jack.mwukzibackened.domain.room.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateRoomRequest {

    @DecimalMin(value = "-90.0", message = "위도 범위가 올바르지 않습니다")
    @DecimalMax(value = "90.0", message = "위도 범위가 올바르지 않습니다")
    private Double centerLat;

    @DecimalMin(value = "-180.0", message = "경도 범위가 올바르지 않습니다")
    @DecimalMax(value = "180.0", message = "경도 범위가 올바르지 않습니다")
    private Double centerLng;

    @Min(value = 300, message = "반경은 300m 이상이어야 합니다")
    @Max(value = 20000, message = "반경은 20km 이하여야 합니다")
    private Integer radiusMeters;
}
