package com.cooked.backend.mapper;

import com.cooked.backend.dto.response.UserResponse;
import com.cooked.backend.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserResponse toResponse(User user);
}
