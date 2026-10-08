package com.cooked.backend.controller;

import com.cooked.backend.dto.request.AlertAckRequest;
import com.cooked.backend.dto.request.SavedViewRequest;
import com.cooked.backend.dto.request.TeamInviteRequest;
import com.cooked.backend.dto.response.SavedViewResponse;
import com.cooked.backend.dto.response.TeamActivityResponse;
import com.cooked.backend.dto.response.UserResponse;
import com.cooked.backend.service.AdminPreferencesService;
import com.cooked.backend.service.AdminTeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Team invitations and activity, alerts read state, saved views. HTTP only. */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Workspace", description = "Team, alert read state and saved views")
public class AdminWorkspaceController {

    private final AdminTeamService team;
    private final AdminPreferencesService prefs;

    @Operation(summary = "Invite an admin or data intern (account + email to choose a password)")
    @PostMapping("/team/invite")
    public ResponseEntity<UserResponse> invite(@Valid @RequestBody TeamInviteRequest body) {
        return ResponseEntity.ok(team.invite(body));
    }

    @Operation(summary = "Last activity of every team member")
    @GetMapping("/team/activity")
    public ResponseEntity<List<TeamActivityResponse>> activity() {
        return ResponseEntity.ok(team.activity());
    }

    @GetMapping("/alerts/read")
    public ResponseEntity<List<String>> readAlerts() {
        return ResponseEntity.ok(prefs.acknowledgedAlerts());
    }

    @Operation(summary = "Mark alerts as read for the whole team")
    @PostMapping("/alerts/read")
    public ResponseEntity<Map<String, Integer>> markRead(@Valid @RequestBody AlertAckRequest body, Authentication auth) {
        return ResponseEntity.ok(Map.of("marked", prefs.acknowledge(body.keys(), auth.getName())));
    }

    @Operation(summary = "My saved views on a screen")
    @GetMapping("/views")
    public ResponseEntity<List<SavedViewResponse>> views(@RequestParam String screen, Authentication auth) {
        return ResponseEntity.ok(prefs.views(auth.getName(), screen));
    }

    @PostMapping("/views")
    public ResponseEntity<SavedViewResponse> saveView(@Valid @RequestBody SavedViewRequest body, Authentication auth) {
        return ResponseEntity.ok(prefs.saveView(body, auth.getName()));
    }

    @DeleteMapping("/views/{id}")
    public ResponseEntity<Void> deleteView(@PathVariable UUID id, Authentication auth) {
        prefs.deleteView(id, auth.getName());
        return ResponseEntity.noContent().build();
    }
}
