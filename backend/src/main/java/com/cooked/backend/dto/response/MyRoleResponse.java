package com.cooked.backend.dto.response;

import java.util.Map;

/** What the signed-in staff member may see (custom = false: full admin or intern, no restriction from a custom role). */
public record MyRoleResponse(boolean custom, String name, Map<String, String> permissions) {
}
