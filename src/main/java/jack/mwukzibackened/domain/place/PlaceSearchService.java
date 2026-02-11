package jack.mwukzibackened.domain.place;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jack.mwukzibackened.common.exception.BadRequestException;
import jack.mwukzibackened.common.exception.KakaoApiException;
import jack.mwukzibackened.common.exception.NotFoundException;
import jack.mwukzibackened.domain.ai.AiRecommendationService;
import jack.mwukzibackened.domain.ai.dto.MenuRecommendationResponse;
import jack.mwukzibackened.domain.place.dto.PlaceDetailRequest;
import jack.mwukzibackened.domain.place.dto.PlaceDetailResponse;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaceSearchService {

    private static final Duration KAKAO_TIMEOUT = Duration.ofSeconds(4);
    private static final int DEFAULT_SIZE_PER_KEYWORD = 5;
    private static final int DEFAULT_MAX_KEYWORDS = 5;
    private static final int MAX_RESULT_SIZE = 30;
    private static final Pattern OG_IMAGE_PATTERN =
            Pattern.compile("<meta[^>]*property=[\"']og:image[\"'][^>]*content=[\"']([^\"']+)[\"'][^>]*>",
                    Pattern.CASE_INSENSITIVE);

    private final RoomRepository roomRepository;
    private final AiRecommendationService aiRecommendationService;
    private final WebClient webClient = WebClient.builder().build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${kakao.rest-api-key:}")
    private String kakaoRestApiKey;

    @Value("${kakao.local-search-url:https://dapi.kakao.com/v2/local/search/keyword.json}")
    private String kakaoLocalSearchUrl;

    @Value("${kakao.image-search-url:https://dapi.kakao.com/v2/search/image}")
    private String kakaoImageSearchUrl;

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

    public PlaceDetailResponse getPlaceDetail(UUID roomId, PlaceDetailRequest request) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("방을 찾을 수 없습니다"));
        if (kakaoRestApiKey == null || kakaoRestApiKey.isBlank()) {
            throw new BadRequestException("KAKAO_REST_API_KEY가 설정되지 않았습니다");
        }

        // 상세 조회의 거리 기준은 항상 방 중심 좌표로 고정해 0m 오표시를 방지합니다.
        double centerLat = room.getCenterLat().doubleValue();
        double centerLng = room.getCenterLng().doubleValue();
        List<PlaceSearchResponse.PlaceItem> candidates = callKakaoKeywordSearch(
                request.getPlaceName().trim(),
                centerLat,
                centerLng,
                room.getRadiusMeters(),
                15
        );
        if (candidates.isEmpty()) {
            throw new NotFoundException("선택한 식당의 상세 정보를 찾을 수 없습니다");
        }

        PlaceSearchResponse.PlaceItem matched = matchCandidate(candidates, request);
        Integer distanceMeters = matched.getDistanceMeters();
        if (distanceMeters == null || distanceMeters <= 0) {
            distanceMeters = estimateDistanceMeters(
                    centerLat,
                    centerLng,
                    matched.getLatitude(),
                    matched.getLongitude()
            );
        }
        List<String> imageUrls = fetchImageUrls(
                matched.getName(),
                matched.getRoadAddress(),
                matched.getAddress(),
                matched.getPlaceUrl()
        );

        return PlaceDetailResponse.builder()
                .provider(matched.getProvider())
                .providerPlaceId(matched.getProviderPlaceId())
                .name(matched.getName())
                .category(matched.getCategory())
                .address(matched.getAddress())
                .roadAddress(matched.getRoadAddress())
                .phone(matched.getPhone())
                .distanceMeters(distanceMeters)
                .latitude(matched.getLatitude())
                .longitude(matched.getLongitude())
                .placeUrl(matched.getPlaceUrl())
                .sourceKeyword(matched.getSourceKeyword())
                .imageUrl(imageUrls.isEmpty() ? "" : imageUrls.get(0))
                .imageUrls(imageUrls)
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
                items.add(toPlaceItem(doc, keyword, centerLat, centerLng));
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

    private PlaceSearchResponse.PlaceItem toPlaceItem(
            JsonNode doc,
            String keyword,
            double centerLat,
            double centerLng
    ) {
        Double lat = parseDoubleOrNull(doc.path("y").asText(null));
        Double lng = parseDoubleOrNull(doc.path("x").asText(null));
        Integer distanceMeters = parseIntOrNull(doc.path("distance").asText(null));
        if (distanceMeters == null && lat != null && lng != null) {
            distanceMeters = estimateDistanceMeters(centerLat, centerLng, lat, lng);
        }
        return PlaceSearchResponse.PlaceItem.builder()
                .provider("kakao")
                .providerPlaceId(doc.path("id").asText(""))
                .name(doc.path("place_name").asText(""))
                .category(doc.path("category_name").asText(""))
                .address(doc.path("address_name").asText(""))
                .roadAddress(doc.path("road_address_name").asText(""))
                .phone(doc.path("phone").asText(""))
                .distanceMeters(distanceMeters)
                .latitude(lat)
                .longitude(lng)
                .placeUrl(doc.path("place_url").asText(""))
                .sourceKeyword(keyword)
                .build();
    }

    private PlaceSearchResponse.PlaceItem matchCandidate(
            List<PlaceSearchResponse.PlaceItem> candidates,
            PlaceDetailRequest request
    ) {
        if (request.getProviderPlaceId() != null && !request.getProviderPlaceId().isBlank()) {
            for (PlaceSearchResponse.PlaceItem candidate : candidates) {
                if (request.getProviderPlaceId().equals(candidate.getProviderPlaceId())) {
                    return candidate;
                }
            }
        }
        String requestedName = request.getPlaceName().trim();
        for (PlaceSearchResponse.PlaceItem candidate : candidates) {
            if (requestedName.equals(candidate.getName())) {
                return candidate;
            }
        }
        return candidates.get(0);
    }

    private Integer estimateDistanceMeters(double centerLat, double centerLng, Double placeLat, Double placeLng) {
        if (placeLat == null || placeLng == null) {
            return null;
        }
        return (int) Math.round(haversineMeters(centerLat, centerLng, placeLat, placeLng));
    }

    private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        final double earthRadius = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }

    private List<String> fetchImageUrls(String name, String roadAddress, String address, String placeUrl) {
        List<String> queries = new ArrayList<>();
        queries.add(name + " 음식점");
        queries.add(name + " 맛집");
        if (roadAddress != null && !roadAddress.isBlank()) {
            queries.add(name + " " + roadAddress);
        } else if (address != null && !address.isBlank()) {
            queries.add(name + " " + address);
        }

        LinkedHashSet<String> merged = new LinkedHashSet<>();
        for (String query : queries) {
            merged.addAll(searchImageUrlsByQuery(query));
            if (merged.size() >= 5) {
                break;
            }
        }

        if ((placeUrl != null && !placeUrl.isBlank()) && merged.isEmpty()) {
            String ogImage = fetchOgImageFromPlaceUrl(placeUrl);
            if (!ogImage.isBlank()) {
                merged.add(ogImage);
            }
        }
        return new ArrayList<>(merged);
    }

    private List<String> searchImageUrlsByQuery(String query) {
        try {
            URI uri = UriComponentsBuilder
                    .fromUriString(kakaoImageSearchUrl)
                    .queryParam("query", query)
                    .queryParam("size", 10)
                    .queryParam("sort", "accuracy")
                    .build()
                    .encode()
                    .toUri();

            String raw = webClient.get()
                    .uri(uri)
                    .header("Authorization", "KakaoAK " + kakaoRestApiKey)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(body -> Mono.error(new KakaoApiException("카카오 이미지 검색 실패"))))
                    .bodyToMono(String.class)
                    .timeout(KAKAO_TIMEOUT)
                    .block();
            if (raw == null || raw.isBlank()) {
                return List.of();
            }
            JsonNode root = objectMapper.readTree(raw);
            JsonNode documents = root.path("documents");
            if (!documents.isArray()) {
                return List.of();
            }
            List<String> urls = new ArrayList<>();
            for (JsonNode doc : documents) {
                String imageUrl = normalizeImageUrl(doc.path("image_url").asText(""));
                if (imageUrl.isBlank()) {
                    imageUrl = normalizeImageUrl(doc.path("thumbnail_url").asText(""));
                }
                if (!imageUrl.isBlank() && !urls.contains(imageUrl)) {
                    urls.add(imageUrl);
                }
                if (urls.size() >= 5) {
                    break;
                }
            }
            return urls;
        } catch (Exception ex) {
            log.debug("이미지 검색 실패: query={}", query);
            return List.of();
        }
    }

    private String fetchOgImageFromPlaceUrl(String placeUrl) {
        try {
            String html = webClient.get()
                    .uri(placeUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(KAKAO_TIMEOUT)
                    .block();
            if (html == null || html.isBlank()) {
                return "";
            }
            Matcher matcher = OG_IMAGE_PATTERN.matcher(html);
            if (!matcher.find()) {
                return "";
            }
            return normalizeImageUrl(matcher.group(1));
        } catch (Exception ex) {
            log.debug("place og:image 조회 실패: url={}", placeUrl);
            return "";
        }
    }

    private String normalizeImageUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String normalized = url.trim();
        if (normalized.startsWith("//")) {
            return "https:" + normalized;
        }
        if (normalized.startsWith("http://")) {
            return "https://" + normalized.substring("http://".length());
        }
        return normalized;
    }
}
