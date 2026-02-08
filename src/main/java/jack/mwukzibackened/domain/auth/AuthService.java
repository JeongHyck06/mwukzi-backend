package jack.mwukzibackened.domain.auth;

import jack.mwukzibackened.common.jwt.JwtUtil;
import jack.mwukzibackened.domain.auth.dto.KakaoUserInfo;
import jack.mwukzibackened.domain.auth.dto.LoginResponse;
import jack.mwukzibackened.domain.user.User;
import jack.mwukzibackened.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {
    
    private final KakaoApiClient kakaoApiClient;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    
    /**
     * 카카오 Access Token으로 로그인
     * 1. 카카오 API로 사용자 정보 조회
     * 2. DB에 사용자 upsert
     * 3. JWT 발급
     */
    @Transactional
    public LoginResponse loginWithKakao(String kakaoAccessToken) {
        // 1. 카카오 사용자 정보 조회
        KakaoUserInfo kakaoUser = kakaoApiClient.getUserInfo(kakaoAccessToken);
        
        // 2. DB에서 사용자 조회 또는 생성
        User user = userRepository.findByProviderAndProviderUserId("kakao", kakaoUser.getId().toString())
                .orElseGet(() -> createUser(kakaoUser));
        
        // 닉네임 업데이트 (카카오에서 변경했을 수 있음)
        user.updateNickname(kakaoUser.getNickname());
        userRepository.save(user);
        
        // 3. JWT 생성
        String accessToken = jwtUtil.generateUserToken(user.getId(), "kakao");
        
        // 4. 응답 생성
        return LoginResponse.builder()
                .accessToken(accessToken)
                .user(LoginResponse.UserInfo.builder()
                        .userId(user.getId())
                        .provider("kakao")
                        .nickname(user.getNickname())
                        .email(user.getEmail())
                        .build())
                .build();
    }
    
    /**
     * 새 사용자 생성
     */
    private User createUser(KakaoUserInfo kakaoUser) {
        User user = User.builder()
                .provider("kakao")
                .providerUserId(kakaoUser.getId().toString())
                .nickname(kakaoUser.getNickname())
                .email(kakaoUser.getEmail())
                .build();
        
        return userRepository.save(user);
    }
    
    /**
     * 현재 로그인한 사용자 정보 조회
     */
    public User getUserById(java.util.UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));
    }
}
