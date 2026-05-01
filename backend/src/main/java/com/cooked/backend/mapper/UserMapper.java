package com.cooked.backend.mapper;

import com.cooked.backend.dto.response.UserResponse;
import com.cooked.backend.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {
    @Mapping(source = "photo", target = "profilePictureUrl")
    UserResponse toResponse(User user);
}
