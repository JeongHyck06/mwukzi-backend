package jack.mwukzibackened.domain.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jack.mwukzibackened.common.exception.BadRequestException;
import jack.mwukzibackened.common.exception.NotFoundException;
import jack.mwukzibackened.domain.ai.dto.MenuRecommendationRequest;
import jack.mwukzibackened.domain.ai.dto.MenuRecommendationResponse;
import jack.mwukzibackened.domain.room.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(AiRecommendationService.class);
    private static final Duration OPENAI_TIMEOUT = Duration.ofSeconds(15);

    private final RoomRepository roomRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient webClient = WebClient.builder().build();

    @Value("${openai.api-key:}")
    private String openAiApiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String openAiModel;

    @Value("${openai.base-url:https://api.openai.com}")
    private String openAiBaseUrl;

    public MenuRecommendationResponse recommendMenus(
            UUID roomId,
            MenuRecommendationRequest request
    ) {
        if (!roomRepository.existsById(roomId)) {
            throw new NotFoundException("방을 찾을 수 없습니다");
        }
        if (openAiApiKey == null || openAiApiKey.isBlank()) {
            throw new BadRequestException("OPENAI_API_KEY가 설정되지 않았습니다");
        }

        int count = request.getCount() == null ? 5 : Math.max(1, Math.min(request.getCount(), 10));
        String prompt = buildPrompt(request.getParticipants(), count);
        String content = callOpenAi(prompt);
        MenuRecommendationResponse response = parseRecommendation(content);

        log.info("[AI 추천] roomId={}, participants={}, summary={}, commonGround={}, compromise={}",
                roomId,
                request.getParticipants().size(),
                response.getSummary(),
                response.getCommonGround(),
                response.getCompromise());
        for (int i = 0; i < response.getMenus().size(); i++) {
            MenuRecommendationResponse.MenuItem item = response.getMenus().get(i);
            log.info("[AI 추천] {}. {} - {}", i + 1, item.getName(), item.getReason());
        }

        return response;
    }

    private String buildPrompt(
            List<MenuRecommendationRequest.ParticipantPreferenceInput> participants,
            int count
    ) {
        StringBuilder preferences = new StringBuilder();
        for (var participant : participants) {
            String name = participant.getName() == null || participant.getName().isBlank()
                    ? "익명"
                    : participant.getName().trim();
            String preference = participant.getPreference() == null || participant.getPreference().isBlank()
                    ? "입력 없음"
                    : participant.getPreference().trim();
            preferences.append("- ").append(name).append(": ").append(preference).append("\n");
        }

        return """
                너는 한국 음식 추천 전문가야.
                아래 참여자들의 취향 요약을 보고, 함께 먹기 좋은 메뉴를 추천해줘.
                응답은 반드시 JSON으로만 반환해.

                JSON 스키마:
                {
                  "summary": "전체 취향 요약 한 문장",
                  "commonGround": "참여자 공통분모 한 문장",
                  "compromise": "갈등 취향을 반영한 타협안 한 문장",
                  "menus": [
                    {"name": "메뉴명", "reason": "추천 이유"},
                    {"name": "메뉴명", "reason": "추천 이유"}
                  ]
                }

                제약:
                - menus 길이는 정확히 %d개
                - 메뉴명은 한국어
                - 각 reason은 1문장
                - commonGround는 반드시 채워
                - compromise는 반드시 채워

                참여자 취향:
                %s
                """.formatted(count, preferences);
    }

    private String callOpenAi(String prompt) {
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", openAiModel);
            requestBody.put("temperature", 0.7);
            requestBody.put("response_format", Map.of("type", "json_object"));
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", "너는 메뉴 추천 도우미다."),
                    Map.of("role", "user", "content", prompt)
            ));

            String response = webClient.post()
                    .uri(openAiBaseUrl + "/v1/chat/completions")
                    .header("Authorization", "Bearer " + openAiApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse -> clientResponse.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(body -> {
                                log.warn("OpenAI 호출 실패: status={}, body={}", clientResponse.statusCode(), body);
                                return Mono.error(new BadRequestException("GPT 추천 생성에 실패했습니다"));
                            }))
                    .bodyToMono(String.class)
                    .timeout(OPENAI_TIMEOUT)
                    .block();

            if (response == null || response.isBlank()) {
                throw new BadRequestException("GPT 응답이 비어 있습니다");
            }

            JsonNode root = objectMapper.readTree(response);
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (contentNode.isMissingNode() || contentNode.asText().isBlank()) {
                throw new BadRequestException("GPT 응답 형식이 올바르지 않습니다");
            }
            return contentNode.asText();
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("GPT 추천 호출 실패", ex);
            throw new BadRequestException("GPT 추천 생성 중 오류가 발생했습니다");
        }
    }

    private MenuRecommendationResponse parseRecommendation(String content) {
        try {
            JsonNode json = objectMapper.readTree(content);
            String summary = readFirstNonBlankText(
                    json,
                    "summary", "요약"
            );
            String commonGround = readFirstNonBlankText(
                    json,
                    "commonGround", "common_ground", "common", "공통분모"
            );
            String compromise = readFirstNonBlankText(
                    json,
                    "compromise", "compromiseOption", "compromise_option", "타협안"
            );
            JsonNode menusNode = json.path("menus");
            List<MenuRecommendationResponse.MenuItem> items = new ArrayList<>();
            if (menusNode.isArray()) {
                for (JsonNode menuNode : menusNode) {
                    String name = menuNode.path("name").asText("").trim();
                    String reason = menuNode.path("reason").asText("").trim();
                    if (!name.isEmpty()) {
                        items.add(MenuRecommendationResponse.MenuItem.builder()
                                .name(name)
                                .reason(reason.isEmpty() ? "취향 기반 추천 메뉴입니다." : reason)
                                .build());
                    }
                }
            }
            if (items.isEmpty()) {
                throw new BadRequestException("추천 메뉴를 생성하지 못했습니다");
            }
            return MenuRecommendationResponse.builder()
                    .summary(summary.isEmpty() ? "참여자 취향을 반영한 메뉴 추천 결과입니다." : summary)
                    .commonGround(commonGround.isEmpty()
                            ? (summary.isEmpty()
                                ? "참여자들의 입력 취향에서 공통되는 선호를 찾았습니다."
                                : summary)
                            : commonGround)
                    .compromise(compromise.isEmpty()
                            ? "서로 다른 취향을 반영해 선택 가능한 메뉴를 제안합니다."
                            : compromise)
                    .menus(items)
                    .build();
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("GPT 응답 파싱 실패: content={}", content, ex);
            throw new BadRequestException("추천 결과 파싱에 실패했습니다");
        }
    }

    private String readFirstNonBlankText(JsonNode json, String... keys) {
        for (String key : keys) {
            JsonNode node = json.path(key);
            if (!node.isMissingNode()) {
                String value = node.asText("").trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        return "";
    }
}
