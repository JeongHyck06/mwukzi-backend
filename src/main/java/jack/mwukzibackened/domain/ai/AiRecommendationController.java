package jack.mwukzibackened.domain.ai;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jack.mwukzibackened.domain.ai.dto.MenuRecommendationRequest;
import jack.mwukzibackened.domain.ai.dto.MenuRecommendationResponse;
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
@Tag(name = "AI", description = "AI 추천 API")
public class AiRecommendationController {

    private final AiRecommendationService aiRecommendationService;

    @PostMapping("/{roomId}/ai/recommend-menu")
    @Operation(summary = "메뉴 추천", description = "참여자 취향을 기반으로 GPT 메뉴 추천을 생성합니다.")
    public ResponseEntity<MenuRecommendationResponse> recommendMenu(
            @PathVariable UUID roomId,
            @Valid @RequestBody MenuRecommendationRequest request
    ) {
        MenuRecommendationResponse response = aiRecommendationService.recommendMenus(roomId, request);
        return ResponseEntity.ok(response);
    }
}
