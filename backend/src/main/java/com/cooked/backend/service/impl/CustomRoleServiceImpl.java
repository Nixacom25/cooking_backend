package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.CustomRoleRequest;
import com.cooked.backend.dto.response.CustomRoleResponse;
import com.cooked.backend.dto.response.MyRoleResponse;
import com.cooked.backend.entity.CustomRole;
import com.cooked.backend.entity.Role;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.CustomRoleRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.security.CustomRoleFilter;
import com.cooked.backend.security.RolePolicy;
import com.cooked.backend.service.ActivityLogService;
import com.cooked.backend.service.CustomRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomRoleServiceImpl implements CustomRoleService {

    private final CustomRoleRepository roles;
    private final UserRepository users;
    private final ActivityLogService activityLog;
    private final CustomRoleFilter filter;

    @Override
    @Transactional(readOnly = true)
    public List<CustomRoleResponse> list() {
        return roles.findAllByOrderByNameAsc().stream().map(this::view).toList();
    }

    @Override
    @Transactional
    public CustomRoleResponse create(CustomRoleRequest r, String adminEmail) {
        User admin = fullAdmin(adminEmail);
        String name = r.name().trim();
        if (name.equalsIgnoreCase("Admin") || name.equalsIgnoreCase("Data Intern")) throw new BadRequestException("This name is already a built-in role.");
        if (roles.existsByNameIgnoreCase(name)) throw new BadRequestException("A role with this name already exists.");
        CustomRole role = roles.save(CustomRole.builder().name(name).description(trim(r.description()))
                .permissions(RolePolicy.format(permissions(r.permissions()))).createdBy(adminEmail).build());
        activityLog.logActivity(admin, "Custom role created", name + " · " + role.getPermissions(), role.getId(), "CUSTOM_ROLE");
        return view(role);
    }

    @Override
    @Transactional
    public CustomRoleResponse update(UUID id, CustomRoleRequest r, String adminEmail) {
        User admin = fullAdmin(adminEmail);
        CustomRole role = find(id);
        String name = r.name().trim();
        if (!name.equalsIgnoreCase(role.getName()) && roles.existsByNameIgnoreCase(name)) throw new BadRequestException("A role with this name already exists.");
        role.setName(name);
        role.setDescription(trim(r.description()));
        role.setPermissions(RolePolicy.format(permissions(r.permissions())));
        roles.save(role);
        filter.evict(id);
        activityLog.logActivity(admin, "Custom role changed", name + " · " + role.getPermissions(), id, "CUSTOM_ROLE");
        return view(role);
    }

    @Override
    @Transactional
    public void delete(UUID id, String adminEmail) {
        User admin = fullAdmin(adminEmail);
        CustomRole role = find(id);
        if (users.countByCustomRoleId(id) > 0) throw new BadRequestException("Remove the members of this role first.");
        roles.delete(role);
        filter.evict(id);
        activityLog.logActivity(admin, "Custom role deleted", role.getName(), id, "CUSTOM_ROLE");
    }

    @Override
    @Transactional
    public CustomRoleResponse addMember(UUID roleId, UUID userId, String adminEmail) {
        User admin = fullAdmin(adminEmail);
        CustomRole role = find(roleId);
        User member = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Team member not found"));
        if (member.getRole() != Role.ADMIN) throw new BadRequestException("Only admin team members can get a custom role.");
        if (member.getId().equals(admin.getId())) throw new BadRequestException("You can't change your own role.");
        if (member.getCustomRoleId() == null && users.countByRoleAndCustomRoleIdIsNull(Role.ADMIN) <= 1) {
            throw new BadRequestException("At least one admin must keep full access.");
        }
        member.setCustomRoleId(roleId);
        users.save(member);
        activityLog.logActivity(admin, "Custom role assigned", role.getName() + " → " + member.getEmail(), roleId, "CUSTOM_ROLE");
        return view(role);
    }

    @Override
    @Transactional
    public CustomRoleResponse removeMember(UUID roleId, UUID userId, String adminEmail) {
        User admin = fullAdmin(adminEmail);
        CustomRole role = find(roleId);
        User member = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Team member not found"));
        if (!roleId.equals(member.getCustomRoleId())) throw new BadRequestException("This person doesn't have this role.");
        member.setCustomRoleId(null); // back to full admin
        users.save(member);
        activityLog.logActivity(admin, "Custom role removed", role.getName() + " → " + member.getEmail() + " (full admin again)", roleId, "CUSTOM_ROLE");
        return view(role);
    }

    @Override
    @Transactional(readOnly = true)
    public MyRoleResponse me(String email) {
        User u = users.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (u.getRole() != Role.ADMIN || u.getCustomRoleId() == null) return new MyRoleResponse(false, null, null);
        return roles.findById(u.getCustomRoleId())
                .map(r -> new MyRoleResponse(true, r.getName(), names(RolePolicy.parse(r.getPermissions()))))
                .orElseGet(() -> new MyRoleResponse(true, "Removed role", names(RolePolicy.parse(null))));
    }

    /** Roles can only be managed by full admins, so a role member can't widen their own access. */
    private User fullAdmin(String email) {
        User u = users.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (u.getRole() != Role.ADMIN || u.getCustomRoleId() != null) throw new AccessDeniedException("Only full admins can manage roles.");
        return u;
    }

    private static Map<String, RolePolicy.Level> permissions(Map<String, String> in) {
        Map<String, RolePolicy.Level> out = new LinkedHashMap<>();
        for (String a : RolePolicy.AREAS) {
            String v = in == null ? null : in.get(a);
            try {
                out.put(a, v == null ? RolePolicy.Level.NONE : RolePolicy.Level.valueOf(v.toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Unknown permission “" + v + "” for " + a + ".");
            }
        }
        return out;
    }

    private static Map<String, String> names(Map<String, RolePolicy.Level> perms) {
        Map<String, String> out = new LinkedHashMap<>();
        perms.forEach((k, v) -> out.put(k, v.name()));
        return out;
    }

    private CustomRoleResponse view(CustomRole r) {
        List<CustomRoleResponse.Member> members = users.findAllByCustomRoleId(r.getId()).stream()
                .map(u -> new CustomRoleResponse.Member(u.getId(),
                        ((u.getFirstname() == null ? "" : u.getFirstname()) + " " + (u.getLastname() == null ? "" : u.getLastname())).trim(), u.getEmail()))
                .toList();
        return new CustomRoleResponse(r.getId(), r.getName(), r.getDescription(), names(RolePolicy.parse(r.getPermissions())), members, r.getCreatedBy(), r.getCreatedAt());
    }

    private CustomRole find(UUID id) {
        return roles.findById(id).orElseThrow(() -> new ResourceNotFoundException("Role not found"));
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
