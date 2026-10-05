package com.example.pollapp.controller;

import com.example.pollapp.request.PollCreateRequest;
import com.example.pollapp.request.PollSettingsRequest;
import com.example.pollapp.request.Vote;
import com.example.pollapp.response.PollCreatedResponse;
import com.example.pollapp.response.PollManagementResponse;
import com.example.pollapp.response.PollResponse;
import com.example.pollapp.service.PollService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/polls")
public class PollController {
    private final PollService pollService;

    public PollController(PollService pollService) {
        this.pollService = pollService;
    }

    @PostMapping
    public PollCreatedResponse createPoll(@RequestBody PollCreateRequest request) {
        return pollService.createPoll(request);
    }

    @GetMapping
    public List<PollResponse> getPublicPolls() {
        return pollService.getPublicPolls();
    }

    @GetMapping("/private")
    public PollResponse getPrivatePoll(
            @RequestHeader("X-Poll-Invite") String inviteToken,
            @RequestHeader(value = "X-Poll-Voter", required = false) String voterToken
    ) {
        return pollService.getPrivatePoll(inviteToken, voterToken);
    }

    @PostMapping("/vote")
    public void votePublic(@RequestBody Vote vote) {
        pollService.votePublic(vote.getPollId(), vote.getOptionIndex());
    }

    @PostMapping("/private/vote")
    public void votePrivate(
            @RequestHeader("X-Poll-Invite") String inviteToken,
            @RequestHeader("X-Poll-Voter") String voterToken,
            @RequestBody Vote vote
    ) {
        pollService.votePrivate(inviteToken, voterToken, vote.getOptionIndex());
    }

    @GetMapping("/manage")
    public PollManagementResponse getManagementDetails(@RequestHeader("X-Poll-Manage") String managementToken) {
        return pollService.getManagementDetails(managementToken);
    }

    @PutMapping("/manage")
    public PollManagementResponse updateSettings(
            @RequestHeader("X-Poll-Manage") String managementToken,
            @RequestBody PollSettingsRequest settings
    ) {
        return pollService.updateSettings(managementToken, settings);
    }

    @PostMapping("/manage/invite")
    public Map<String, String> rotateInviteToken(@RequestHeader("X-Poll-Manage") String managementToken) {
        return Map.of("inviteToken", pollService.rotateInviteToken(managementToken));
    }

    @PostMapping("/manage/rotate")
    public Map<String, String> rotateManagementToken(@RequestHeader("X-Poll-Manage") String managementToken) {
        return Map.of("managementToken", pollService.rotateManagementToken(managementToken));
    }

    @DeleteMapping("/manage")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePoll(@RequestHeader("X-Poll-Manage") String managementToken) {
        pollService.deletePoll(managementToken);
    }
}

@RestControllerAdvice
class PollApiExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<Map<String, String>> responseStatus(ResponseStatusException exception) {
        return org.springframework.http.ResponseEntity.status(exception.getStatusCode())
                .body(Map.of("message", exception.getReason() == null ? "Request failed." : exception.getReason()));
    }
}
