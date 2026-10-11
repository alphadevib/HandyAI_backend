package com.handyai.build.controller;

import com.handyai.build.domain.PriceBook;
import com.handyai.build.domain.ProfessionCatalog;
import com.handyai.build.dto.StatsResponse;
import com.handyai.build.service.ToolService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MetaController {

    private final ToolService toolService;

    public MetaController(ToolService toolService) {
        this.toolService = toolService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "HandyAI");
    }

    @GetMapping("/stats")
    public StatsResponse stats() {
        return toolService.stats();
    }

    /** The choices for the profession (individuals) and industry (organisations) boxes. */
    @GetMapping("/professions")
    public Map<String, List<ProfessionCatalog.Entry>> professions() {
        return Map.of("professions", ProfessionCatalog.professions(),
                "industries", ProfessionCatalog.industries());
    }

    /** How rupee prices are derived, so the UI can say so next to every converted figure. */
    @GetMapping("/currency")
    public Map<String, Object> currency() {
        return Map.of("base", "USD", "usdToInr", PriceBook.USD_TO_INR,
                "note", "Indicative list prices of each tool's entry paid plan. Rupee amounts are "
                        + "converted at a fixed rate, so check the vendor's site before buying.");
    }
}
