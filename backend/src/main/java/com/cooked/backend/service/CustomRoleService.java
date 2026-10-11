package com.cooked.backend.service;

import com.cooked.backend.dto.request.CustomRoleRequest;
import com.cooked.backend.dto.response.CustomRoleResponse;
import com.cooked.backend.dto.response.MyRoleResponse;

import java.util.List;
import java.util.UUID;

/**
 * Custom roles (Settings → Permissions). Only full admins (no custom role)
 * can create, change or assign them, so nobody can widen their own access.
 */
public interface CustomRoleService {

    List<CustomRoleResponse> list();

    CustomRoleResponse create(CustomRoleRequest request, String adminEmail);

    CustomRoleResponse update(UUID id, CustomRoleRequest request, String adminEmail);

    /** Only a role without members can be deleted. */
    void delete(UUID id, String adminEmail);

    CustomRoleResponse addMember(UUID roleId, UUID userId, String adminEmail);

    CustomRoleResponse removeMember(UUID roleId, UUID userId, String adminEmail);

    MyRoleResponse me(String email);
}
