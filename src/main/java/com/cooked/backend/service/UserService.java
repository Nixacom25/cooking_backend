package com.cooked.backend.service;

import com.cooked.backend.entity.User;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface UserService {
    List<User> getAllUsers(Pageable pageable);
}
