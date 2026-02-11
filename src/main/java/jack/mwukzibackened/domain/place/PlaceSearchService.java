package jack.mwukzibackened.domain.place;

import com.fasterxml.jackson.annotation.JsonProperty;
import jack.mwukzibackened.common.exception.BadRequestException;
import jack.mwukzibackened.common.exception.NaverApiException;
import jack.mwukzibackened.domain.place.dto.PlaceSearchRequest;
import jack.mwukzibackened.domain.place.dto.PlaceSearchResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PlaceSearchService {

    private static final Logger log = LoggerFactory.getLogger(PlaceSearchService.class);
    private static final Duration NAVER_TIMEOUT = Duration.ofSeconds(5);

    @Value("${naver.search.client-id:}")
    private String naverSearchClientId;

    @Value("${naver.search.client-secret:}")
    private String naverSearchClientSecret;

    @Value("${naver.search.local-search-url:https://openapi.naver.com/v1/search/local.json}")
    private String naverLocalSearchUrl;

    private final WebClient webClient = WebClient.builder().build();

    public PlaceSearchResponse searchNearbyPlaces(PlaceSearchRequest request) {
        if (naverSearchClientId == null || naverSearchClientId.isBlank()
                || naverSearchClientSecret == null || naverSearchClientSecret.isBlank()) {
            throw new BadRequestException("네이버 검색 API 키가 설정되지 않았습니다");
        }

        List<String> menus = normalizeMenus(request.getMenus());
        if (menus.isEmpty()) {
            throw new BadRequestException("유효한 메뉴 정보가 없습니다");
        }

        int maxItems = request.getMaxItems() == null ? 20 : request.getMaxItems();
        Map<String, PlaceSearchResponse.PlaceItem> merged = new LinkedHashMap<>();

        for (String menu : menus.stream().limit(5).toList()) {
            String query = menu + " 맛집";
            List<NaverLocalItem> localItems = callNaverLocalSearch(query, 5);

            for (NaverLocalItem item : localItems) {
                Integer distanceMeters = calculateDistanceMeters(
                        request.getLatitude(),
                        request.getLongitude(),
                        item.latitude(),
                        item.longitude()
                );

                PlaceSearchResponse.PlaceItem mapped = PlaceSearchResponse.PlaceItem.builder()
                        .name(item.normalizedTitle())
                        .category(item.normalizedCategory())
                        .address(item.normalizedAddress())
                        .roadAddress(item.normalizedRoadAddress())
                        .link(item.link())
                        .latitude(item.latitude())
                        .longitude(item.longitude())
                        .distanceMeters(distanceMeters)
                        .matchedMenu(menu)
                        .build();

                String dedupeKey = buildDedupeKey(mapped);
                PlaceSearchResponse.PlaceItem previous = merged.get(dedupeKey);
                if (previous == null || isBetterByDistance(previous, mapped)) {
                    merged.put(dedupeKey, mapped);
                }
            }
        }

        List<PlaceSearchResponse.PlaceItem> sorted = new ArrayList<>(merged.values());
        sorted.sort(Comparator.comparingInt(this::distanceOrMax));

        return PlaceSearchResponse.builder()
                .places(sorted.stream().limit(maxItems).toList())
                .build();
    }

    private List<String> normalizeMenus(List<String> menus) {
        if (menus == null) {
            return List.of();
        }
        Set<String> deduped = new LinkedHashSet<>();
        for (String raw : menus) {
            if (raw == null) {
                continue;
            }
            String normalized = raw.trim();
            if (!normalized.isEmpty()) {
                deduped.add(normalized);
            }
        }
        return new ArrayList<>(deduped);
    }

    private List<NaverLocalItem> callNaverLocalSearch(String query, int display) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String requestUrl = naverLocalSearchUrl
                    + "?query=" + encodedQuery
                    + "&display=" + display
                    + "&start=1&sort=random";

            NaverLocalSearchResponse response = webClient.get()
                    .uri(requestUrl)
                    .header("X-Naver-Client-Id", naverSearchClientId)
                    .header("X-Naver-Client-Secret", naverSearchClientSecret)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(body -> {
                                        log.warn("네이버 검색 4xx: query={}, status={}, body={}",
                                                query, clientResponse.statusCode().value(), body);
                                        return Mono.error(new NaverApiException("네이버 검색 API 요청이 거부되었습니다"));
                                    })
                    )
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(body -> {
                                        log.warn("네이버 검색 5xx: query={}, status={}, body={}",
                                                query, clientResponse.statusCode().value(), body);
                                        return Mono.error(new NaverApiException("네이버 검색 API 서버 오류입니다"));
                                    })
                    )
                    .bodyToMono(NaverLocalSearchResponse.class)
                    .timeout(NAVER_TIMEOUT)
                    .block();

            if (response == null || response.items() == null) {
                return List.of();
            }
            return response.items();
        } catch (NaverApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("네이버 검색 호출 실패: query={}", query, ex);
            throw new NaverApiException("네이버 검색 호출에 실패했습니다", ex);
        }
    }

    private int distanceOrMax(PlaceSearchResponse.PlaceItem item) {
        return item.getDistanceMeters() == null ? Integer.MAX_VALUE : item.getDistanceMeters();
    }

    private boolean isBetterByDistance(
            PlaceSearchResponse.PlaceItem previous,
            PlaceSearchResponse.PlaceItem candidate
    ) {
        return distanceOrMax(candidate) < distanceOrMax(previous);
    }

    private String buildDedupeKey(PlaceSearchResponse.PlaceItem item) {
        String address = item.getRoadAddress() != null && !item.getRoadAddress().isBlank()
                ? item.getRoadAddress()
                : item.getAddress();
        return item.getName() + "|" + (address == null ? "" : address);
    }

    private Integer calculateDistanceMeters(
            Double originLat,
            Double originLng,
            Double targetLat,
            Double targetLng
    ) {
        if (originLat == null || originLng == null || targetLat == null || targetLng == null) {
            return null;
        }

        double earthRadius = 6371000.0;
        double dLat = Math.toRadians(targetLat - originLat);
        double dLng = Math.toRadians(targetLng - originLng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(originLat))
                * Math.cos(Math.toRadians(targetLat))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (int) Math.round(earthRadius * c);
    }

    private record NaverLocalSearchResponse(List<NaverLocalItem> items) {
    }

    private record NaverLocalItem(
            @JsonProperty("title") String title,
            @JsonProperty("category") String category,
            @JsonProperty("address") String address,
            @JsonProperty("roadAddress") String roadAddress,
            @JsonProperty("link") String link,
            @JsonProperty("mapx") String mapx,
            @JsonProperty("mapy") String mapy
    ) {
        String normalizedTitle() {
            return decode(title);
        }

        String normalizedCategory() {
            return decode(category);
        }

        String normalizedAddress() {
            return decode(address);
        }

        String normalizedRoadAddress() {
            return decode(roadAddress);
        }

        Double latitude() {
            return toLatLngValue(mapy);
        }

        Double longitude() {
            return toLatLngValue(mapx);
        }

        private static String decode(String raw) {
            if (raw == null) {
                return "";
            }
            return raw
                    .replaceAll("<[^>]*>", "")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")
                    .replace("&nbsp;", " ")
                    .trim();
        }

        private static Double toLatLngValue(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            try {
                double value = Double.parseDouble(raw);
                if (Math.abs(value) <= 180.0) {
                    return value;
                }
                double scaled = value / 10000000.0;
                if (Math.abs(scaled) <= 180.0) {
                    return scaled;
                }
                return null;
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }
}
