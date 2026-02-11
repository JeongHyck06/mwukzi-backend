package jack.mwukzibackened.domain.place;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jack.mwukzibackened.domain.place.dto.PlaceSearchRequest;
import jack.mwukzibackened.domain.place.dto.PlaceSearchResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/places")
@RequiredArgsConstructor
@Tag(name = "Place", description = "주변 장소 검색 API")
public class PlaceController {

    private final PlaceSearchService placeSearchService;

    @PostMapping("/search")
    @Operation(summary = "주변 장소 검색", description = "추천 메뉴를 기반으로 주변 식당을 검색합니다.")
    public ResponseEntity<PlaceSearchResponse> searchPlaces(
            @Valid @RequestBody PlaceSearchRequest request
    ) {
        PlaceSearchResponse response = placeSearchService.searchNearbyPlaces(request);
        return ResponseEntity.ok(response);
    }
}
