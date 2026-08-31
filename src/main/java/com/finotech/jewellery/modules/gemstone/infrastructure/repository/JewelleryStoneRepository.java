package com.finotech.jewellery.modules.gemstone.infrastructure.repository;

import com.finotech.jewellery.modules.gemstone.domain.entity.JewelleryStone;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JewelleryStoneRepository extends JpaRepository<JewelleryStone, UUID> {

    List<JewelleryStone> findAllByJewelleryItemId(UUID jewelleryItemId);

    void deleteAllByJewelleryItemId(UUID jewelleryItemId);

    @Query("""
            select coalesce(sum(s.weightGrams), 0) from JewelleryStone s
            where s.jewelleryItemId = :itemId
            """)
    BigDecimal totalStoneWeightGrams(@Param("itemId") UUID itemId);

    @Query("""
            select coalesce(sum(s.stoneValue), 0) from JewelleryStone s
            where s.jewelleryItemId = :itemId
            """)
    BigDecimal totalStoneValue(@Param("itemId") UUID itemId);
}
