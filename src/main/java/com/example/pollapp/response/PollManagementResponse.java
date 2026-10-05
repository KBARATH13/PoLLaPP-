package com.example.pollapp.response;

public record PollManagementResponse(
        Long id,
        String question,
        boolean isPublic,
        Integer voteLimit,
        long privateVotes
) {
}
