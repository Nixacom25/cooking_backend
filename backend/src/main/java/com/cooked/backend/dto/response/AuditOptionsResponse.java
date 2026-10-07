package com.cooked.backend.dto.response;

import java.util.List;

/** Values the Audit log filters can take (people, areas, actions that exist). */
public record AuditOptionsResponse(List<Person> people, List<String> areas, List<String> actions) {

    public record Person(String email, String name) {
    }
}
