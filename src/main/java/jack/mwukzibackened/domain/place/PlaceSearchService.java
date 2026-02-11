package jack.mwukzibackened.domain.place;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jack.mwukzibackened.common.exception.BadRequestException;
import jack.mwukzibackened.common.exception.KakaoApiException;
import jack.mwukzibackened.common.exception.NotFoundException;
import jack.mwukzibackened.domain.ai.AiRecommendationService;
import jack.mwukzibackened.domain.ai.dto.MenuRecommendationResponse;
import jack.mwukzibackened.domain.place.dto.PlaceSearchRequest;
import jack.mwukzibackened.domain.place.dto.PlaceSearchResponse;
import jack.mwukzibackened.domain.room.Room;
import jack.mwukzibackened.domain.room.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaceSearchService {

    private static final Duration KAKAO_TIMEOUT = Duration.ofSeconds(4);
    private static final int DEFAULT_SIZE_PER_KEYWORD = 5;
    private static final int DEFAULT_MAX_KEYWORDS = 5;
    private static final int MAX_RESULT_SIZE = 30;

    private final RoomRepository roomRepository;
    private final AiRecommendationService aiRecommendationService;
    private final WebClient webClient = WebClient.builder().build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${kakao.rest-api-key:}")
    private String kakaoRestApiKey;

    @Value("${kakao.local-search-url:https://dapi.kakao.com/v2/local/search/keyword.json}")
    private String kakaoLocalSearchUrl;

    public PlaceSearchResponse search(UUID roomId, PlaceSearchRequest request) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("방을 찾을 수 없습니다"));

        double centerLat = request != null && request.getLatitude() != null
                ? request.getLatitude()
                : room.getCenterLat().doubleValue();
        double centerLng = request != null && request.getLongitude() != null
                ? request.getLongitude()
                : room.getCenterLng().doubleValue();
        int radiusMeters = request != null && request.getRadiusMeters() != null
                ? request.getRadiusMeters()
                : room.getRadiusMeters();
        int sizePerKeyword = request != null && request.getSizePerKeyword() != null
                ? request.getSizePerKeyword()
                : DEFAULT_SIZE_PER_KEYWORD;

        List<String> keywords = normalizeKeywords(request == null ? List.of() : request.getKeywords());
        if (keywords.isEmpty()) {
            keywords = readKeywordsFromLatestRecommendation(roomId);
        }
        if (keywords.isEmpty()) {
            throw new BadRequestException("검색 키워드가 없습니다. keywords를 전달하거나 AI 추천을 먼저 생성해 주세요");
        }

        if (kakaoRestApiKey == null || kakaoRestApiKey.isBlank()) {
            throw new BadRequestException("KAKAO_REST_API_KEY가 설정되지 않았습니다");
        }

        Map<String, PlaceSearchResponse.PlaceItem> merged = new LinkedHashMap<>();
        for (String keyword : keywords) {
            List<PlaceSearchResponse.PlaceItem> items = callKakaoKeywordSearch(
                    keyword,
                    centerLat,
                    centerLng,
                    radiusMeters,
                    sizePerKeyword
            );
            for (PlaceSearchResponse.PlaceItem item : items) {
                String key = item.getProviderPlaceId() == null || item.getProviderPlaceId().isBlank()
                        ? item.getName() + ":" + item.getLatitude() + ":" + item.getLongitude()
                        : item.getProviderPlaceId();
                merged.putIfAbsent(key, item);
            }
        }

        List<PlaceSearchResponse.PlaceItem> places = merged.values().stream()
                .sorted(Comparator.comparing(item -> item.getDistanceMeters() == null ? Integer.MAX_VALUE : item.getDistanceMeters()))
                .limit(MAX_RESULT_SIZE)
                .toList();

        return PlaceSearchResponse.builder()
                .centerLat(centerLat)
                .centerLng(centerLng)
                .radiusMeters(radiusMeters)
                .keywordsUsed(keywords)
                .places(places)
                .build();
    }

    private List<String> readKeywordsFromLatestRecommendation(UUID roomId) {
        try {
            MenuRecommendationResponse latest = aiRecommendationService.getLatestRecommendation(roomId);
            List<String> menuKeywords = latest.getMenus().stream()
                    .map(MenuRecommendationResponse.MenuItem::getName)
                    .toList();
            return normalizeKeywords(menuKeywords);
        } catch (NotFoundException ex) {
            return List.of();
        }
    }

    private List<String> normalizeKeywords(List<String> rawKeywords) {
        if (rawKeywords == null || rawKeywords.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String keyword : rawKeywords) {
            if (keyword == null) {
                continue;
            }
            String normalized = keyword.trim();
            if (!normalized.isEmpty()) {
                unique.add(normalized);
            }
            if (unique.size() >= DEFAULT_MAX_KEYWORDS) {
                break;
            }
        }
        return new ArrayList<>(unique);
    }

    private List<PlaceSearchResponse.PlaceItem> callKakaoKeywordSearch(
            String keyword,
            double centerLat,
            double centerLng,
            int radiusMeters,
            int sizePerKeyword
    ) {
        try {
            URI uri = UriComponentsBuilder
                    .fromUriString(kakaoLocalSearchUrl)
                    .queryParam("query", keyword)
                    .queryParam("x", centerLng)
                    .queryParam("y", centerLat)
                    .queryParam("radius", radiusMeters)
                    .queryParam("size", sizePerKeyword)
                    .queryParam("sort", "distance")
                    .queryParam("category_group_code", "FD6")
                    .build()
                    .encode()
                    .toUri();

            String rawResponse = webClient.get()
                    .uri(uri)
                    .header("Authorization", "KakaoAK " + kakaoRestApiKey)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, response ->
                            response.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(body -> Mono.error(new KakaoApiException("카카오 장소 검색 4xx 응답")))
                    )
                    .onStatus(HttpStatusCode::is5xxServerError, response ->
                            response.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(body -> Mono.error(new KakaoApiException("카카오 장소 검색 5xx 응답")))
                    )
                    .bodyToMono(String.class)
                    .timeout(KAKAO_TIMEOUT)
                    .block();

            if (rawResponse == null || rawResponse.isBlank()) {
                throw new KakaoApiException("카카오 장소 검색 응답이 비어 있습니다");
            }
            JsonNode root = objectMapper.readTree(rawResponse);

            JsonNode documents = root.path("documents");
            if (!documents.isArray()) {
                return List.of();
            }

            List<PlaceSearchResponse.PlaceItem> items = new ArrayList<>();
            for (JsonNode doc : documents) {
                items.add(PlaceSearchResponse.PlaceItem.builder()
                        .provider("kakao")
                        .providerPlaceId(doc.path("id").asText(""))
                        .name(doc.path("place_name").asText(""))
                        .category(doc.path("category_name").asText(""))
                        .address(doc.path("address_name").asText(""))
                        .roadAddress(doc.path("road_address_name").asText(""))
                        .phone(doc.path("phone").asText(""))
                        .distanceMeters(parseIntOrNull(doc.path("distance").asText(null)))
                        .latitude(parseDoubleOrNull(doc.path("y").asText(null)))
                        .longitude(parseDoubleOrNull(doc.path("x").asText(null)))
                        .placeUrl(doc.path("place_url").asText(""))
                        .sourceKeyword(keyword)
                        .build());
            }
            return items;
        } catch (KakaoApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("카카오 장소 검색 실패: keyword={}, lat={}, lng={}", keyword, centerLat, centerLng, ex);
            throw new KakaoApiException("카카오 장소 검색 호출 실패", ex);
        }
    }

    private Integer parseIntOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Double parseDoubleOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
