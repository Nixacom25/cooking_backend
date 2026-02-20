package com.cooked.backend.service.impl;

import com.cooked.backend.entity.User;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    public List<User> getAllUsers(Pageable pageable) {
        return userRepository.findAll();
    }
}
