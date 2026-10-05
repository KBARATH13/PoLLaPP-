package com.example.pollapp.request;

public record PollSettingsRequest(boolean isPublic, Integer voteLimit) {
}
