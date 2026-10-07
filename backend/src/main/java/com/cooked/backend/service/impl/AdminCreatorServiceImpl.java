package com.cooked.backend.service.impl;

import com.cooked.backend.dto.response.AcquisitionResponse;
import com.cooked.backend.dto.response.CreatorDetailResponse;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.AmbassadorRepository;
import com.cooked.backend.repository.CreatorStatsRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.AdminCreatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminCreatorServiceImpl implements AdminCreatorService {

    static final int CHART_DAYS = 14;

    private final UserRepository users;
    private final CreatorStatsRepository stats;
    private final AmbassadorRepository ambassadors;

    @Override
    public CreatorDetailResponse detail(UUID userId) {
        User u = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Creator not found"));
        LocalDateTime now = LocalDateTime.now();
        LocalDate first = LocalDate.now().minusDays(CHART_DAYS - 1L);
        Map<LocalDate, Long> byDay = new HashMap<>();
        stats.publishedDaily(userId, first.atStartOfDay()).forEach(d -> byDay.merge(d.getDay(), d.getTotal() == null ? 0 : d.getTotal(), Long::sum));
        List<AcquisitionResponse.DayCount> daily = new ArrayList<>();
        for (int i = 0; i < CHART_DAYS; i++) {
            LocalDate d = first.plusDays(i);
            daily.add(new AcquisitionResponse.DayCount(d.toString(), byDay.getOrDefault(d, 0L)));
        }
        var amb = u.getEmail() == null ? Optional.<com.cooked.backend.entity.Ambassador>empty() : ambassadors.findFirstByEmailIgnoreCase(u.getEmail());
        String name = ((u.getFirstname() == null ? "" : u.getFirstname()) + " " + (u.getLastname() == null ? "" : u.getLastname())).trim();
        return CreatorDetailResponse.builder()
                .id(u.getId()).name(name.isEmpty() ? u.getEmail() : name).email(u.getEmail()).photo(u.getPhoto())
                .role(u.getRole() == null ? null : u.getRole().name()).joined(u.getCreatedAt())
                .publicRecipes(stats.countPublic(userId))
                .published30d(stats.countPublicBetween(userId, now.minusDays(30), now.plusDays(1)))
                .publishedPrev30d(stats.countPublicBetween(userId, now.minusDays(60), now.minusDays(30)))
                .uses(stats.totalUses(userId))
                .publishedDaily(daily)
                .topRecipes(stats.topRecipes(userId, PageRequest.of(0, 8)).stream()
                        .map(r -> new CreatorDetailResponse.Recipe(r.getId(), r.getName(), r.getImage(), r.getUses() == null ? 0 : r.getUses(), r.getCreatedAt())).toList())
                .ambassadorId(amb.map(com.cooked.backend.entity.Ambassador::getId).orElse(null))
                .ambassadorCode(amb.map(com.cooked.backend.entity.Ambassador::getCode).orElse(null))
                .build();
    }
}
