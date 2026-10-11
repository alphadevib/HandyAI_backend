package com.handyai.build.repository;

import com.handyai.build.domain.Favorite;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    Optional<Favorite> findByUserIdAndToolId(Long userId, Long toolId);

    boolean existsByUserIdAndToolId(Long userId, Long toolId);

    @Query("select f.tool.id from Favorite f where f.user.id = :userId order by f.createdAt desc")
    List<Long> findToolIdsByUser(@Param("userId") Long userId);

    @Query("select f.tool.category.id from Favorite f where f.user.id = :userId")
    List<Long> findFavoriteCategoryIds(@Param("userId") Long userId);

    long countByToolId(Long toolId);
}
