package com.cooked.backend.repository;

import com.cooked.backend.entity.ProviderDailyCost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ProviderDailyCostRepository extends JpaRepository<ProviderDailyCost, UUID> {

    interface ProviderDayAmount {
        String getProvider();
        LocalDate getDay();
        BigDecimal getAmount();
    }

    interface LabelAmount {
        String getLabel();
        BigDecimal getAmount();
    }

    @Query("select d.provider as provider, d.costDay as day, sum(d.amountUsd) as amount from ProviderDailyCost d "
            + "where d.costDay >= :from and d.costDay <= :to group by d.provider, d.costDay")
    List<ProviderDayAmount> sumByProviderAndDay(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select d.lineItem as label, sum(d.amountUsd) as amount from ProviderDailyCost d "
            + "where lower(d.provider) = lower(:provider) and d.costDay >= :from and d.costDay <= :to "
            + "group by d.lineItem order by sum(d.amountUsd) desc")
    List<LabelAmount> sumByLineItem(@Param("provider") String provider, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select max(d.costDay) from ProviderDailyCost d")
    LocalDate lastDay();

    @Modifying
    @Query("delete from ProviderDailyCost d where d.provider = :provider and d.costDay >= :from")
    int deleteFrom(@Param("provider") String provider, @Param("from") LocalDate from);
}
