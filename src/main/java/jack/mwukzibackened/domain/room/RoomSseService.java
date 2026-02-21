package jack.mwukzibackened.domain.room;

import jack.mwukzibackened.domain.ai.dto.MenuRecommendationResponse;
import jack.mwukzibackened.domain.room.dto.RoomParticipantResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
public class RoomSseService {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String inviteCode) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(inviteCode, key -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(inviteCode, emitter));
        emitter.onTimeout(() -> removeEmitter(inviteCode, emitter));
        emitter.onError((ex) -> removeEmitter(inviteCode, emitter));

        return emitter;
    }

    public void sendParticipants(String inviteCode, List<RoomParticipantResponse> participants) {
        send(inviteCode, "participants", participants);
    }

    public void sendParticipantsToEmitter(
            String inviteCode,
            SseEmitter emitter,
            List<RoomParticipantResponse> participants
    ) {
        sendToEmitter(inviteCode, emitter, "participants", participants);
    }

    public void sendRecommendation(String inviteCode, MenuRecommendationResponse recommendation) {
        send(inviteCode, "recommendation", recommendation);
    }

    public void closeRoom(String inviteCode) {
        send(inviteCode, "room_closed", List.of());
        CopyOnWriteArrayList<SseEmitter> list = emitters.remove(inviteCode);
        if (list != null) {
            list.forEach(SseEmitter::complete);
        }
    }

    private void send(String inviteCode, String event, Object data) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(inviteCode);
        if (list == null || list.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : list) {
            sendToEmitter(inviteCode, emitter, event, data);
        }
    }

    private void sendToEmitter(String inviteCode, SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (Exception ex) {
            // IOException 외 IllegalStateException 등도 발생할 수 있어 안전하게 정리합니다.
            log.debug("SSE 전송 실패: inviteCode={}, event={}", inviteCode, event, ex);
            removeEmitter(inviteCode, emitter);
        }
    }

    private void removeEmitter(String inviteCode, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(inviteCode);
        if (list == null) {
            return;
        }
        list.remove(emitter);
        if (list.isEmpty()) {
            emitters.remove(inviteCode);
        }
    }
}
