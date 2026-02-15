package jack.mwukzibackened.domain.place;

import jack.mwukzibackened.common.exception.BadRequestException;
import jack.mwukzibackened.common.exception.NotFoundException;
import jack.mwukzibackened.common.exception.UnauthorizedException;
import jack.mwukzibackened.domain.participant.Participant;
import jack.mwukzibackened.domain.participant.ParticipantRepository;
import jack.mwukzibackened.domain.place.dto.PlaceSelectionItemRequest;
import jack.mwukzibackened.domain.place.dto.PlaceSelectionParticipantStatusResponse;
import jack.mwukzibackened.domain.place.dto.PlaceSelectionSummaryResponse;
import jack.mwukzibackened.domain.place.dto.RoulettePickResponse;
import jack.mwukzibackened.domain.room.Room;
import jack.mwukzibackened.domain.room.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PlaceSelectionService {

    private final RoomRepository roomRepository;
    private final ParticipantRepository participantRepository;
    private final PlaceSelectionRepository placeSelectionRepository;
    private final SecureRandom random = new SecureRandom();

    public PlaceSelectionService(
            RoomRepository roomRepository,
            ParticipantRepository participantRepository,
            PlaceSelectionRepository placeSelectionRepository
    ) {
        this.roomRepository = roomRepository;
        this.participantRepository = participantRepository;
        this.placeSelectionRepository = placeSelectionRepository;
    }

    @Transactional
    public PlaceSelectionSummaryResponse submitSelections(
            UUID roomId,
            UUID requesterUserId,
            UUID participantId,
            List<PlaceSelectionItemRequest> places
    ) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("방을 찾을 수 없습니다"));
        Participant actor = resolveActor(room, requesterUserId, participantId);

        List<NormalizedPlaceSelection> selectedPlaces = normalizePlaceSelections(places);
        if (selectedPlaces.isEmpty()) {
            throw new BadRequestException("최소 1개 이상의 식당을 선택해 주세요");
        }

        placeSelectionRepository.deleteByRoomIdAndParticipantId(roomId, actor.getId());
        List<PlaceSelection> rows = selectedPlaces.stream()
                .map(selection -> PlaceSelection.builder()
                        .room(room)
                        .participant(actor)
                        .placeName(selection.placeName())
                        .providerPlaceId(selection.providerPlaceId())
                        .build())
                .toList();
        placeSelectionRepository.saveAll(rows);

        return buildSummary(room, actor.getId());
    }

    public PlaceSelectionSummaryResponse getSummary(
            UUID roomId,
            UUID requesterUserId,
            UUID participantId
    ) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("방을 찾을 수 없습니다"));
        Participant actor = resolveActor(room, requesterUserId, participantId);
        return buildSummary(room, actor.getId());
    }

    public RoulettePickResponse spinRoulette(UUID roomId, UUID requesterUserId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("방을 찾을 수 없습니다"));
        if (requesterUserId == null || !room.getHost().getId().equals(requesterUserId)) {
            throw new UnauthorizedException("방장만 룰렛을 돌릴 수 있습니다");
        }

        List<Participant> participants = participantRepository.findByRoomId(roomId);
        if (participants.isEmpty()) {
            throw new BadRequestException("참가자가 없습니다");
        }

        Map<UUID, List<String>> selectionsByParticipant = buildSelectionMap(roomId);

        List<String> incompleteNames = participants.stream()
                .filter(participant -> {
                    List<String> selected = selectionsByParticipant.get(participant.getId());
                    return selected == null || selected.isEmpty();
                })
                .map(Participant::getDisplayName)
                .toList();

        if (!incompleteNames.isEmpty()) {
            throw new BadRequestException(
                    "아직 선택을 완료하지 않은 참가자가 있습니다: " + String.join(", ", incompleteNames)
            );
        }

        List<String> tickets = new ArrayList<>();
        for (Participant participant : participants) {
            tickets.addAll(selectionsByParticipant.getOrDefault(participant.getId(), List.of()));
        }
        if (tickets.isEmpty()) {
            throw new BadRequestException("룰렛 후보가 없습니다");
        }

        int pickedIndex = random.nextInt(tickets.size());
        String selectedPlace = tickets.get(pickedIndex);

        return RoulettePickResponse.builder()
                .selectedPlaceName(selectedPlace)
                .totalTicketCount(tickets.size())
                .candidateNames(uniqueCandidateNames(tickets))
                .build();
    }

    private PlaceSelectionSummaryResponse buildSummary(Room room, UUID actorParticipantId) {
        List<Participant> participants = participantRepository.findByRoomId(room.getId());
        Map<UUID, List<String>> selectionsByParticipant = buildSelectionMap(room.getId());

        List<PlaceSelectionParticipantStatusResponse> statuses = participants.stream()
                .map(participant -> {
                    List<String> selected = selectionsByParticipant.get(participant.getId());
                    boolean completed = selected != null && !selected.isEmpty();
                    return PlaceSelectionParticipantStatusResponse.builder()
                            .participantId(participant.getId())
                            .displayName(participant.getDisplayName())
                            .completed(completed)
                            .build();
                })
                .toList();

        boolean allCompleted = !statuses.isEmpty()
                && statuses.stream().allMatch(PlaceSelectionParticipantStatusResponse::isCompleted);

        List<String> tickets = new ArrayList<>();
        for (List<String> selected : selectionsByParticipant.values()) {
            tickets.addAll(selected);
        }

        List<String> mySelection = selectionsByParticipant.getOrDefault(actorParticipantId, List.of());
        return PlaceSelectionSummaryResponse.builder()
                .allCompleted(allCompleted)
                .myCompleted(!mySelection.isEmpty())
                .totalSelectedCount(tickets.size())
                .candidateNames(uniqueCandidateNames(tickets))
                .participants(statuses)
                .build();
    }

    private Participant resolveActor(Room room, UUID requesterUserId, UUID participantId) {
        if (requesterUserId != null) {
            if (!room.getHost().getId().equals(requesterUserId)) {
                throw new UnauthorizedException("방장 인증이 올바르지 않습니다");
            }
            return participantRepository.findByRoomIdAndUserId(room.getId(), requesterUserId)
                    .orElseThrow(() -> new BadRequestException("방장 참여 정보가 없습니다"));
        }

        if (participantId == null) {
            throw new BadRequestException("participant_id가 필요합니다");
        }
        Participant participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new NotFoundException("참가자를 찾을 수 없습니다"));
        if (!participant.getRoom().getId().equals(room.getId())) {
            throw new BadRequestException("방 정보가 올바르지 않습니다");
        }
        return participant;
    }

    private Map<UUID, List<String>> buildSelectionMap(UUID roomId) {
        Map<UUID, List<String>> selectionsByParticipant = new HashMap<>();
        for (PlaceSelection row : placeSelectionRepository.findByRoomId(roomId)) {
            UUID participantId = row.getParticipant().getId();
            selectionsByParticipant
                    .computeIfAbsent(participantId, ignored -> new ArrayList<>())
                    .add(row.getPlaceName());
        }
        return selectionsByParticipant;
    }

    private List<NormalizedPlaceSelection> normalizePlaceSelections(List<PlaceSelectionItemRequest> places) {
        if (places == null || places.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> uniqueNames = new LinkedHashSet<>();
        List<NormalizedPlaceSelection> normalizedSelections = new ArrayList<>();
        for (PlaceSelectionItemRequest item : places) {
            if (item == null || item.getPlaceName() == null) {
                continue;
            }
            String placeName = item.getPlaceName().trim();
            if (placeName.isEmpty() || uniqueNames.contains(placeName)) {
                continue;
            }
            String providerPlaceId = item.getProviderPlaceId() == null
                    ? null
                    : item.getProviderPlaceId().trim();
            if (providerPlaceId != null && providerPlaceId.isEmpty()) {
                providerPlaceId = null;
            }
            uniqueNames.add(placeName);
            normalizedSelections.add(new NormalizedPlaceSelection(placeName, providerPlaceId));
        }
        return normalizedSelections;
    }

    private List<String> uniqueCandidateNames(List<String> tickets) {
        return tickets.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private record NormalizedPlaceSelection(String placeName, String providerPlaceId) {
    }
}
