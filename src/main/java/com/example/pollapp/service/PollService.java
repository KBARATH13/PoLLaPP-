package com.example.pollapp.service;

import com.example.pollapp.model.Poll;
import com.example.pollapp.model.PollVote;
import com.example.pollapp.model.VoteOption;
import com.example.pollapp.repository.PollRepo;
import com.example.pollapp.repository.PollVoteRepo;
import com.example.pollapp.request.PollCreateRequest;
import com.example.pollapp.request.PollSettingsRequest;
import com.example.pollapp.response.PollCreatedResponse;
import com.example.pollapp.response.PollManagementResponse;
import com.example.pollapp.response.PollOptionResponse;
import com.example.pollapp.response.PollResponse;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
public class PollService {
    private final PollRepo pollRepo;
    private final PollVoteRepo pollVoteRepo;
    private final PollTokenService tokenService;

    public PollService(PollRepo pollRepo, PollVoteRepo pollVoteRepo, PollTokenService tokenService) {
        this.pollRepo = pollRepo;
        this.pollVoteRepo = pollVoteRepo;
        this.tokenService = tokenService;
    }

    @Transactional
    public PollCreatedResponse createPoll(PollCreateRequest request) {
        validateQuestionAndOptions(request.question(), request.options());
        if (!request.isPublic() && !isValidLimit(request.voteLimit())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Private polls need a vote limit greater than zero.");
        }

        String managementToken = tokenService.createToken();
        String inviteToken = request.isPublic() ? null : tokenService.createToken();
        Poll poll = new Poll();
        poll.setQuestion(request.question().trim());
        poll.setPublic(request.isPublic());
        poll.setVoteLimit(request.isPublic() ? null : request.voteLimit());
        poll.setManagementTokenHash(tokenService.hash(managementToken));
        poll.setInviteTokenHash(inviteToken == null ? null : tokenService.hash(inviteToken));
        poll.setOptions(new ArrayList<>(request.options().stream()
                .map(option -> new VoteOption(option.trim(), 0L))
                .toList()));
        pollRepo.saveAndFlush(poll);

        return new PollCreatedResponse(toResponse(poll, false), inviteToken, managementToken);
    }

    @Transactional
    public List<PollResponse> getPublicPolls() {
        return pollRepo.findAllPublicWithOptions().stream()
                .map(poll -> toResponse(poll, false))
                .toList();
    }

    @Transactional
    public PollResponse getPrivatePoll(String inviteToken, String voterToken) {
        Poll poll = pollRepo.findPrivateWithOptionsByInviteHash(hashRequiredToken(inviteToken))
                .orElseThrow(() -> notFound());
        String voterTokenHash = optionalVoterHash(voterToken);
        boolean hasVoted = voterTokenHash != null
                && pollVoteRepo.existsByPoll_IdAndVoterTokenHash(poll.getId(), voterTokenHash);
        return toResponse(poll, hasVoted);
    }

    @Transactional
    public void votePublic(Long pollId, Long optionIndex) {
        Poll poll = pollRepo.findByIdForUpdate(pollId).orElseThrow(() -> notFound());
        if (!poll.isPublic()) throw notFound();
        VoteOption selected = selectedOption(poll, optionIndex);
        selected.setVoteCount((selected.getVoteCount() == null ? 0 : selected.getVoteCount()) + 1);
        pollRepo.save(poll);
    }

    @Transactional
    public void votePrivate(String inviteToken, String voterToken, Long optionIndex) {
        String voterTokenHash = hashRequiredToken(voterToken);
        Poll poll = pollRepo.findPrivateByInviteHashForUpdate(hashRequiredToken(inviteToken))
                .orElseThrow(() -> notFound());
        VoteOption selected = selectedOption(poll, optionIndex);
        long voteCount = pollVoteRepo.countByPoll_Id(poll.getId());
        if (poll.getVoteLimit() == null || voteCount >= poll.getVoteLimit()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Voting limit reached.");
        }
        if (pollVoteRepo.existsByPoll_IdAndVoterTokenHash(poll.getId(), voterTokenHash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This browser has already voted in this poll.");
        }
        try {
            pollVoteRepo.saveAndFlush(new PollVote(poll, voterTokenHash, optionIndex));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This browser has already voted in this poll.");
        }
    }

    @Transactional
    public PollManagementResponse getManagementDetails(String managementToken) {
        Poll poll = pollRepo.findByManagementTokenHash(hashRequiredToken(managementToken))
                .orElseThrow(() -> notFound());
        return toManagementResponse(poll);
    }

    @Transactional
    public PollManagementResponse updateSettings(String managementToken, PollSettingsRequest settings) {
        Poll poll = findManagedPollForUpdate(managementToken);
        long privateVotes = pollVoteRepo.countByPoll_Id(poll.getId());
        if (!settings.isPublic() && !isValidLimit(settings.voteLimit())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Private polls need a vote limit greater than zero.");
        }
        if (!settings.isPublic() && settings.voteLimit() < privateVotes) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The vote limit cannot be lower than the number of votes already accepted.");
        }

        if (!poll.isPublic() && settings.isPublic()) {
            for (int index = 0; index < poll.getOptions().size(); index++) {
                VoteOption option = poll.getOptions().get(index);
                long privateOptionVotes = pollVoteRepo.countByPoll_IdAndOptionIndex(poll.getId(), (long) index);
                option.setVoteCount((option.getVoteCount() == null ? 0 : option.getVoteCount()) + privateOptionVotes);
            }
            pollVoteRepo.deleteByPoll_Id(poll.getId());
        } else if (poll.isPublic() && !settings.isPublic()) {
            poll.setInviteTokenHash(tokenService.hash(tokenService.createToken()));
        }
        poll.setPublic(settings.isPublic());
        poll.setVoteLimit(settings.isPublic() ? null : settings.voteLimit());
        pollRepo.saveAndFlush(poll);
        return toManagementResponse(poll);
    }

    @Transactional
    public String rotateInviteToken(String managementToken) {
        Poll poll = findManagedPollForUpdate(managementToken);
        if (poll.isPublic()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Switch this poll to private before creating an invite link.");
        }
        String inviteToken = tokenService.createToken();
        poll.setInviteTokenHash(tokenService.hash(inviteToken));
        pollRepo.saveAndFlush(poll);
        return inviteToken;
    }

    @Transactional
    public String rotateManagementToken(String managementToken) {
        Poll poll = findManagedPollForUpdate(managementToken);
        String nextToken = tokenService.createToken();
        poll.setManagementTokenHash(tokenService.hash(nextToken));
        pollRepo.saveAndFlush(poll);
        return nextToken;
    }

    @Transactional
    public void deletePoll(String managementToken) {
        Poll poll = findManagedPollForUpdate(managementToken);
        pollVoteRepo.deleteByPoll_Id(poll.getId());
        pollRepo.delete(poll);
    }

    private PollResponse toResponse(Poll poll, boolean hasVoted) {
        boolean resultsVisible = poll.isPublic() || hasVoted;
        List<PollOptionResponse> options = new ArrayList<>();
        long totalVotes = 0;
        for (int index = 0; index < poll.getOptions().size(); index++) {
            VoteOption option = poll.getOptions().get(index);
            long voteCount = option.getVoteCount() == null ? 0 : option.getVoteCount();
            if (!poll.isPublic()) {
                voteCount += pollVoteRepo.countByPoll_IdAndOptionIndex(poll.getId(), (long) index);
            }
            totalVotes += voteCount;
            options.add(new PollOptionResponse(option.getVoteoption(), resultsVisible ? voteCount : null));
        }
        long privateVotes = poll.isPublic() ? 0 : pollVoteRepo.countByPoll_Id(poll.getId());
        boolean limitReached = !poll.isPublic() && poll.getVoteLimit() != null
                && privateVotes >= poll.getVoteLimit();
        return new PollResponse(
                poll.getId(),
                poll.getQuestion(),
                options,
                poll.isPublic(),
                poll.getVoteLimit(),
                resultsVisible ? totalVotes : null,
                hasVoted,
                limitReached
        );
    }

    private PollManagementResponse toManagementResponse(Poll poll) {
        return new PollManagementResponse(
                poll.getId(),
                poll.getQuestion(),
                poll.isPublic(),
                poll.getVoteLimit(),
                pollVoteRepo.countByPoll_Id(poll.getId())
        );
    }

    private Poll findManagedPollForUpdate(String managementToken) {
        return pollRepo.findByManagementTokenHashForUpdate(hashRequiredToken(managementToken))
                .orElseThrow(() -> notFound());
    }

    private VoteOption selectedOption(Poll poll, Long optionIndex) {
        if (optionIndex == null || optionIndex < 0 || optionIndex >= poll.getOptions().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid poll option.");
        }
        return poll.getOptions().get(optionIndex.intValue());
    }

    private void validateQuestionAndOptions(String question, List<String> options) {
        if (question == null || question.isBlank() || question.trim().length() > 250
                || options == null || options.size() < 2 || options.size() > 5
                || options.stream().anyMatch(option -> option == null || option.isBlank() || option.trim().length() > 120)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Polls need a question and between 2 and 5 non-empty options.");
        }
    }

    private boolean isValidLimit(Integer voteLimit) {
        return voteLimit != null && voteLimit > 0;
    }

    private String optionalVoterHash(String token) {
        return token == null || token.isBlank() ? null : tokenService.hash(validToken(token));
    }

    private String hashRequiredToken(String token) {
        return tokenService.hash(validToken(token));
    }

    private String validToken(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw notFound();
        }
        return token;
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Poll or link not found.");
    }
}
