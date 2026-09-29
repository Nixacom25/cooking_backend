package com.cooked.backend.repository;

import com.cooked.backend.entity.GiftCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GiftCodeRepository extends JpaRepository<GiftCode, UUID> {

    boolean existsByPurchaseRef(String purchaseRef);

    boolean existsByCode(String code);

    List<GiftCode> findAllByPurchaserIdOrderByCreatedAtDesc(UUID purchaserId);

    List<GiftCode> findAllByTransactionId(String transactionId);

    /** Row lock so two concurrent redeems of the same code can't both succeed. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM GiftCode g WHERE g.code = :code")
    Optional<GiftCode> findByCodeForUpdate(@Param("code") String code);
}
