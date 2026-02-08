package jack.mwukzibackened.domain.auth;

import jack.mwukzibackened.domain.auth.dto.KakaoLoginRequest;
import jack.mwukzibackened.domain.auth.dto.LoginResponse;
import jack.mwukzibackened.domain.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final AuthService authService;
    
    /**
     * POST /api/v1/auth/kakao
     * 카카오 Access Token으로 로그인
     */
    @PostMapping("/kakao")
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
