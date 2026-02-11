package jack.mwukzibackened.domain.place;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jack.mwukzibackened.domain.place.dto.PlaceDetailRequest;
import jack.mwukzibackened.domain.place.dto.PlaceDetailResponse;
import jack.mwukzibackened.domain.place.dto.PlaceSearchRequest;
import jack.mwukzibackened.domain.place.dto.PlaceSearchResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
@Tag(name = "Place", description = "지도 식당 검색 API")
public class PlaceSearchController {

    private final PlaceSearchService placeSearchService;

    @PostMapping("/{roomId}/places/search")
    @Operation(summary = "주변 식당 검색", description = "방 중심 좌표 또는 요청 좌표 기준으로 주변 식당을 검색합니다.")
    public ResponseEntity<PlaceSearchResponse> searchPlaces(
            @PathVariable UUID roomId,
            @Valid @RequestBody(required = false) PlaceSearchRequest request
    ) {
        PlaceSearchResponse response = placeSearchService.search(roomId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{roomId}/places/detail")
    @Operation(summary = "식당 상세 조회", description = "선택한 식당의 최신 상세 정보를 다시 조회합니다.")
    public ResponseEntity<PlaceDetailResponse> getPlaceDetail(
            @PathVariable UUID roomId,
            @Valid @RequestBody PlaceDetailRequest request
    ) {
        PlaceDetailResponse response = placeSearchService.getPlaceDetail(roomId, request);
        return ResponseEntity.ok(response);
    }
}
