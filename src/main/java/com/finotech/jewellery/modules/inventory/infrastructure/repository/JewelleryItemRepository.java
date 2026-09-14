package com.finotech.jewellery.modules.inventory.infrastructure.repository;

import com.finotech.jewellery.modules.inventory.domain.entity.JewelleryItem;
import com.finotech.jewellery.modules.inventory.domain.enums.ItemStatus;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JewelleryItemRepository extends JpaRepository<JewelleryItem, UUID> {

    /**
     * Takes a pessimistic write lock on the row. Used by reservation, sale and
     * dispatch so two concurrent transactions cannot both claim the same item.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from JewelleryItem i where i.id = :id")
    Optional<JewelleryItem> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from JewelleryItem i where i.id in :ids order by i.id")
    List<JewelleryItem> findAllByIdForUpdate(@Param("ids") List<UUID> ids);

    Optional<JewelleryItem> findByItemCodeIgnoreCase(String itemCode);

    Optional<JewelleryItem> findByRfidTag(String rfidTag);

    Optional<JewelleryItem> findByBarcode(String barcode);

    Optional<JewelleryItem> findByQrCode(String qrCode);

    boolean existsByItemCodeIgnoreCase(String itemCode);

    boolean existsByRfidTag(String rfidTag);

    boolean existsByBarcode(String barcode);

    boolean existsByQrCode(String qrCode);

    @Query("""
            select i from JewelleryItem i
            where (cast(:search as string) is null
                   or lower(i.itemCode) like lower(concat('%', cast(:search as string), '%'))
                   or lower(i.rfidTag) like lower(concat('%', cast(:search as string), '%'))
                   or lower(i.barcode) like lower(concat('%', cast(:search as string), '%')))
              and (:productId is null or i.productId = :productId)
              and (:status is null or i.status = :status)
              and (:locationId is null or i.currentLocationId = :locationId)
              and (:branchId is null or i.currentBranchId = :branchId)
              and (:companyId is null or i.currentBranchId in
                   (select b.id from com.finotech.jewellery.modules.organization.domain.entity.Branch b
                     where b.company.id = :companyId))
              and (:metalId is null or i.metalId = :metalId)
              and (:purityId is null or i.purityId = :purityId)
              and (:binId is null or i.binId = :binId)
              and (:minPrice is null or i.currentPrice >= :minPrice)
              and (:maxPrice is null or i.currentPrice <= :maxPrice)
            """)
    Page<JewelleryItem> search(@Param("companyId") UUID companyId,
                               @Param("search") String search,
                               @Param("productId") UUID productId,
                               @Param("status") ItemStatus status,
                               @Param("locationId") UUID locationId,
                               @Param("branchId") UUID branchId,
                               @Param("metalId") UUID metalId,
                               @Param("purityId") UUID purityId,
                               @Param("binId") UUID binId,
                               @Param("minPrice") BigDecimal minPrice,
                               @Param("maxPrice") BigDecimal maxPrice,
                               Pageable pageable);

    /**
     * Every item any of the given tags points at, in one query. Item codes are
     * compared case-insensitively, as {@code findByItemCodeIgnoreCase} does;
     * the physical tags are exact, as they are everywhere else.
     */
    @Query("""
            select i from JewelleryItem i
            where i.rfidTag in :tags
               or i.qrCode in :tags
               or i.barcode in :tags
               or upper(i.itemCode) in :upperTags
            """)
    List<JewelleryItem> findAllByAnyTag(@Param("tags") Collection<String> tags,
                                        @Param("upperTags") Collection<String> upperTags);

    /** Item counts of one product per branch and status, for the availability view. */
    @Query("""
            select new com.finotech.jewellery.modules.inventory.infrastructure.repository.BranchStatusCount(
                i.currentBranchId, i.status, count(i))
            from JewelleryItem i
            where i.productId = :productId
              and i.currentBranchId in :branchIds
            group by i.currentBranchId, i.status
            """)
    List<BranchStatusCount> countByBranchAndStatus(@Param("productId") UUID productId,
                                                   @Param("branchIds") Collection<UUID> branchIds);

    long countByCurrentLocationIdAndStatus(UUID locationId, ItemStatus status);
}
