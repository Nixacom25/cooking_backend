package com.cooked.backend.service;

import com.cooked.backend.dto.request.TeamInviteRequest;
import com.cooked.backend.dto.response.TeamActivityResponse;
import com.cooked.backend.dto.response.UserResponse;

import java.util.List;

/** Backoffice team: invitations and activity. */
public interface AdminTeamService {

    /** Creates the account (random password) and emails a link to choose a password. */
    UserResponse invite(TeamInviteRequest request);

    List<TeamActivityResponse> activity();
}
