package com.handyai.build.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One visitor sent from HandyAI to a vendor's site to buy or start using a tool.
 *
 * <p>This records the hand-off, not the payment: the purchase itself happens on the vendor's site,
 * which reports nothing back unless an affiliate programme is set up with that vendor.
 */
@Entity
@Table(name = "outbound_clicks", indexes = {
        @Index(name = "idx_outbound_created", columnList = "created_at"),
        @Index(name = "idx_outbound_tool", columnList = "tool_id")
})
public class OutboundClick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tool_id", nullable = false)
    private AiTool tool;

    /** The signed-in user, when the visitor was signed in; null for guests. */
    @Column(name = "user_id")
    private Long userId;

    /** Where on HandyAI the click came from: card, detail, chat. */
    @Column(length = 20)
    private String source;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public AiTool getTool() {
        return tool;
    }

    public void setTool(AiTool tool) {
        this.tool = tool;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
