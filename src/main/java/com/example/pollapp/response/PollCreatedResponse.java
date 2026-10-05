package com.example.pollapp.response;

public record PollCreatedResponse(PollResponse poll, String inviteToken, String managementToken) {
}
