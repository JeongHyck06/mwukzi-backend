package jack.mwukzibackened.domain.auth;

import jack.mwukzibackened.common.exception.KakaoApiException;
import jack.mwukzibackened.common.exception.KakaoAuthException;
import jack.mwukzibackened.domain.auth.dto.KakaoUserInfo;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import io.netty.channel.ChannelOption;
import java.time.temporal.ChronoUnit;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class KakaoApiClient {
    
    private static final Logger log = LoggerFactory.getLogger(KakaoApiClient.class);
    private static final Duration KAKAO_TIMEOUT = Duration.ofSeconds(8);

    @Value("${kakao.user-info-url}")
    private String userInfoUrl;
    
    private final WebClient webClient = WebClient.builder()
            .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(
                    HttpClient.create()
                            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                            .responseTimeout(Duration.ofSeconds(8))
            ))
            .build();
    
    /**
     * 카카오 Access Token으로 사용자 정보 조회
     */
    public KakaoUserInfo getUserInfo(String kakaoAccessToken) {
        try {
            KakaoUserInfo userInfo = webClient.get()
                    .uri(userInfoUrl)
                    .header("Authorization", "Bearer " + kakaoAccessToken)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, response ->
                            response.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(body -> {
                                        int status = response.statusCode().value();
                                        if (status == 400 || status == 401) {
                                            return Mono.error(new KakaoAuthException("카카오 인증이 실패했습니다"));
                                        }
                                        return Mono.error(new KakaoApiException("카카오 API 4xx 응답"));
                                    })
                    )
                    .onStatus(HttpStatusCode::is5xxServerError, response ->
                            Mono.error(new KakaoApiException("카카오 API 5xx 응답"))
                    )
                    .bodyToMono(KakaoUserInfo.class)
                    .timeout(KAKAO_TIMEOUT)
                    .retryWhen(reactor.util.retry.Retry
                            .fixedDelay(1, Duration.of(300, ChronoUnit.MILLIS))
                            .filter(ex -> ex instanceof java.util.concurrent.TimeoutException))
                    .block();

            if (userInfo == null) {
                throw new KakaoApiException("카카오 사용자 정보 응답이 비어 있습니다");
            }
            return userInfo;
        } catch (KakaoAuthException | KakaoApiException ex) {
            log.warn("카카오 API 오류: {}", ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            log.error("카카오 API 호출 실패", ex);
            throw new KakaoApiException("카카오 API 호출 실패", ex);
        }
    }
}
