package jack.mwukzibackened.domain.place.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RoulettePickResponse {
    private String selectedPlaceName;
    private int totalTicketCount;
    private List<String> candidateNames;
}
