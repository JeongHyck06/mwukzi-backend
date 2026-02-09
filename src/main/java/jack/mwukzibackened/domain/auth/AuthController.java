package jack.mwukzibackened.domain.auth;

import jack.mwukzibackened.common.exception.UnauthorizedException;
import jack.mwukzibackened.common.security.AuthenticatedUser;
import jack.mwukzibackened.domain.auth.dto.KakaoLoginRequest;
import jack.mwukzibackened.domain.auth.dto.LoginResponse;
import jack.mwukzibackened.domain.auth.dto.UserMeResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
     */
    @GetMapping("/me")
    @Operation(summary = "내 정보 조회", description = "Bearer JWT로 현재 로그인한 사용자 정보를 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<UserMeResponse> getCurrentUser(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        if (principal == null) {
            throw new UnauthorizedException("인증이 필요합니다");
        }

        var user = authService.getUserById(principal.getUserId());
        return ResponseEntity.ok(UserMeResponse.builder()
                .userId(user.getId())
                .provider(user.getProvider())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .build());
    }
}
