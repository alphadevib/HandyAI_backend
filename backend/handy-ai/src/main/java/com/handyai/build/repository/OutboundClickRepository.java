package com.handyai.build.repository;

import com.handyai.build.domain.OutboundClick;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboundClickRepository extends JpaRepository<OutboundClick, Long> {

    long countByCreatedAtAfter(Instant since);

    long countByUserIdIsNotNull();

    /** [tool name, tool slug, clicks], busiest first. */
    @Query("""
            select c.tool.name, c.tool.slug, count(c) from OutboundClick c
            where c.createdAt >= :since
            group by c.tool.name, c.tool.slug
            order by count(c) desc
            """)
    List<Object[]> topTools(@Param("since") Instant since, Pageable pageable);

    @EntityGraph(attributePaths = "tool")
    List<OutboundClick> findTop10ByOrderByCreatedAtDesc();
}
