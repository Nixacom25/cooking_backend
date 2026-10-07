package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.AdminUserFilter;
import com.cooked.backend.dto.response.UserResponse;
import com.cooked.backend.mapper.UserMapper;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.repository.spec.AdminUserSpecs;
import com.cooked.backend.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    public Page<UserResponse> search(AdminUserFilter filter, Pageable pageable) {
        return userRepository.findAll(AdminUserSpecs.of(filter, LocalDateTime.now()), pageable).map(userMapper::toResponse);
    }

    @Override
    public List<String> sources() {
        return userRepository.findDistinctDiscoverySources();
    }
}
