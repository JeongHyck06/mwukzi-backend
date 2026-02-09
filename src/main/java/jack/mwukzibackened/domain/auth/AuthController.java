package jack.mwukzibackened.domain.auth;

import jack.mwukzibackened.domain.auth.dto.KakaoLoginRequest;
import jack.mwukzibackened.domain.auth.dto.LoginResponse;
import jack.mwukzibackened.domain.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "인증 관련 API")
public class AuthController {
    
    private final AuthService authService;
    
    /**
     * POST /api/v1/auth/kakao
     * 카카오 Access Token으로 로그인
     */
    @PostMapping("/kakao")
    @Operation(summary = "카카오 로그인", description = "카카오 Access Token으로 로그인하고 JWT를 발급합니다.")
    public ResponseEntity<LoginResponse> loginWithKakao(
            @Valid @RequestBody KakaoLoginRequest request
    ) {
        LoginResponse response = authService.loginWithKakao(request.getKakaoAccessToken());
        return ResponseEntity.ok(response);
    }
    
    /**
     * GET /api/v1/auth/me
     * 현재 로그인한 사용자 정보 조회
     * TODO: JWT에서 userId 추출하는 인증 처리 필요
     */
    @GetMapping("/me")
    @Operation(summary = "내 정보 조회", description = "Bearer JWT로 현재 로그인한 사용자 정보를 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, Object>> getCurrentUser(
            @RequestHeader("Authorization") String authHeader
    ) {
        // TODO: JWT 파싱 및 검증 (나중에 인터셉터/필터로 처리)
        // 임시로 예시 응답
        return ResponseEntity.ok(Map.of(
                "message", "인증 미들웨어 구현 필요"
        ));
    }
}
