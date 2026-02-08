package jack.mwukzibackened.domain.auth;

import jack.mwukzibackened.domain.auth.dto.KakaoUserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@RequiredArgsConstructor
public class KakaoApiClient {
    
    @Value("${kakao.user-info-url}")
    private String userInfoUrl;
    
    private final WebClient webClient = WebClient.builder().build();
    
    /**
     * 카카오 Access Token으로 사용자 정보 조회
     */
    public KakaoUserInfo getUserInfo(String kakaoAccessToken) {
        return webClient.get()
                .uri(userInfoUrl)
                .header("Authorization", "Bearer " + kakaoAccessToken)
                .retrieve()
                .bodyToMono(KakaoUserInfo.class)
                .block();
    }
}
