package jack.mwukzibackened.domain.place;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jack.mwukzibackened.common.security.AuthenticatedUser;
import jack.mwukzibackened.domain.place.dto.PlaceDetailRequest;
import jack.mwukzibackened.domain.place.dto.PlaceDetailResponse;
import jack.mwukzibackened.domain.place.dto.PlaceSelectionSummaryResponse;
import jack.mwukzibackened.domain.place.dto.PlaceSearchRequest;
import jack.mwukzibackened.domain.place.dto.PlaceSearchResponse;
import jack.mwukzibackened.domain.place.dto.RoulettePickResponse;
import jack.mwukzibackened.domain.place.dto.SubmitPlaceSelectionRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
@Tag(name = "Place", description = "지도 식당 검색 API")
public class PlaceSearchController {

    private final PlaceSearchService placeSearchService;
    private final PlaceSelectionService placeSelectionService;

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

    @PostMapping("/{roomId}/places/selections")
    @Operation(summary = "식당 선택 제출", description = "참가자가 룰렛 후보로 고른 식당 목록을 저장합니다.")
    public ResponseEntity<PlaceSelectionSummaryResponse> submitSelections(
            @PathVariable UUID roomId,
            @Valid @RequestBody(required = false) SubmitPlaceSelectionRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        PlaceSelectionSummaryResponse response = placeSelectionService.submitSelections(
                roomId,
                principal == null ? null : principal.getUserId(),
                request == null ? null : request.getParticipantId(),
                request == null ? List.of() : request.getPlaces()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{roomId}/places/selections/summary")
    @Operation(summary = "식당 선택 현황 조회", description = "참가자별 식당 선택 완료 상태와 후보 목록을 조회합니다.")
    public ResponseEntity<PlaceSelectionSummaryResponse> getSelectionSummary(
            @PathVariable UUID roomId,
            @RequestParam(required = false) UUID participantId,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        PlaceSelectionSummaryResponse response = placeSelectionService.getSummary(
                roomId,
                principal == null ? null : principal.getUserId(),
                participantId
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{roomId}/places/roulette/spin")
    @Operation(summary = "룰렛 추첨", description = "방장이 참가자 전체가 고른 후보를 모아 랜덤으로 1개를 뽑습니다.")
    public ResponseEntity<RoulettePickResponse> spinRoulette(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        RoulettePickResponse response = placeSelectionService.spinRoulette(
                roomId,
                principal == null ? null : principal.getUserId()
        );
        return ResponseEntity.ok(response);
    }
}
