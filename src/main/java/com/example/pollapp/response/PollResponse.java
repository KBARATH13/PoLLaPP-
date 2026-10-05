package com.example.pollapp.response;

import java.util.List;

public record PollResponse(
        Long id,
        String question,
        List<PollOptionResponse> options,
        boolean isPublic,
        Integer voteLimit,
        Long totalVotes,
        boolean hasVoted,
        boolean limitReached
) {
}
