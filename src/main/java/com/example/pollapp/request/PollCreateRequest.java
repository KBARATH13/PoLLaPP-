package com.example.pollapp.request;

import java.util.List;

public record PollCreateRequest(String question, List<String> options, boolean isPublic, Integer voteLimit) {
}
