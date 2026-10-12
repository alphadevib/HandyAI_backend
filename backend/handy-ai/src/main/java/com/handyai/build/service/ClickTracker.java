package com.handyai.build.service;

import com.handyai.build.config.AsyncConfig;
import com.handyai.build.domain.OutboundClick;
import com.handyai.build.repository.AiToolRepository;
import com.handyai.build.repository.OutboundClickRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores outbound clicks off the request thread, so the redirect to the vendor never waits for a
 * database write. Takes plain ids, not entities: a lazy entity from the request's session cannot be
 * used safely on another thread.
 */
@Service
public class ClickTracker {

    private final AiToolRepository toolRepository;
    private final OutboundClickRepository clickRepository;

    public ClickTracker(AiToolRepository toolRepository, OutboundClickRepository clickRepository) {
        this.toolRepository = toolRepository;
        this.clickRepository = clickRepository;
    }

    @Async(AsyncConfig.TRACKING_EXECUTOR)
    @Transactional
    public void record(Long toolId, String source, Long userId) {
        OutboundClick click = new OutboundClick();
        click.setTool(toolRepository.getReferenceById(toolId));
        click.setSource(source);
        click.setUserId(userId);
        clickRepository.save(click);
    }
}
