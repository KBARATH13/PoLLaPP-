package com.example.pollapp.service;

import com.example.pollapp.model.Poll;
import com.example.pollapp.model.PollVote;
import com.example.pollapp.model.VoteOption;
import com.example.pollapp.repository.PollRepo;
import com.example.pollapp.repository.PollVoteRepo;
import com.example.pollapp.request.PollCreateRequest;
import com.example.pollapp.request.PollSettingsRequest;
import com.example.pollapp.response.PollCreatedResponse;
import com.example.pollapp.response.PollResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PollServicePrivacyTest {
    @Mock
    private PollRepo pollRepo;
    @Mock
    private PollVoteRepo pollVoteRepo;

    private PollTokenService tokenService;
    private PollService pollService;
    private Poll poll;

    @BeforeEach
    void setUp() {
        tokenService = new PollTokenService();
        pollService = new PollService(pollRepo, pollVoteRepo, tokenService);
        poll = new Poll();
        poll.setId(12L);
        poll.setQuestion("Choose a time");
        poll.setOptions(List.of(new VoteOption("Morning", 0L), new VoteOption("Evening", 0L)));
    }

    @Test
    void privateCreationRequiresPositiveVoteLimit() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> pollService.createPoll(new PollCreateRequest("Question?", List.of("A", "B"), false, 0)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(pollRepo, never()).saveAndFlush(any());
    }

    @Test
    void privateCreationReturnsSeparateSecretsAndStoresOnlyTheirHashes() {
        when(pollRepo.saveAndFlush(any(Poll.class))).thenAnswer(invocation -> {
            Poll saved = invocation.getArgument(0);
            saved.setId(14L);
            return saved;
        });

        PollCreatedResponse created = pollService.createPoll(
                new PollCreateRequest("Question?", List.of("A", "B"), false, 5));

        assertNotNull(created.inviteToken());
        assertNotNull(created.managementToken());
        assertFalse(created.inviteToken().equals(created.managementToken()));
        Poll savedPoll = captureSavedPoll();
        assertEquals(tokenService.hash(created.inviteToken()), savedPoll.getInviteTokenHash());
        assertEquals(tokenService.hash(created.managementToken()), savedPoll.getManagementTokenHash());
        assertFalse(created.poll().isPublic());
        assertNull(created.poll().totalVotes());
    }

    @Test
    void privatePollIsHiddenFromGlobalList() {
        when(pollRepo.findAllPublicWithOptions()).thenReturn(List.of());

        assertTrue(pollService.getPublicPolls().isEmpty());
        verify(pollRepo).findAllPublicWithOptions();
    }

    @Test
    void privateResultsAreHiddenUntilTheBrowserHasVoted() {
        poll.setPublic(false);
        poll.setVoteLimit(4);
        when(pollRepo.findPrivateWithOptionsByInviteHash(tokenService.hash(TOKEN)))
                .thenReturn(Optional.of(poll));
        when(pollVoteRepo.existsByPoll_IdAndVoterTokenHash(12L, tokenService.hash(VOTER_TOKEN)))
                .thenReturn(false);

        PollResponse result = pollService.getPrivatePoll(TOKEN, VOTER_TOKEN);

        assertFalse(result.hasVoted());
        assertNull(result.totalVotes());
        assertNull(result.options().get(0).voteCount());
        assertFalse(result.limitReached());
    }

    @Test
    void privateResultsAreVisibleToTheBrowserThatVoted() {
        poll.setPublic(false);
        poll.setVoteLimit(4);
        when(pollRepo.findPrivateWithOptionsByInviteHash(tokenService.hash(TOKEN)))
                .thenReturn(Optional.of(poll));
        when(pollVoteRepo.existsByPoll_IdAndVoterTokenHash(12L, tokenService.hash(VOTER_TOKEN)))
                .thenReturn(true);
        when(pollVoteRepo.countByPoll_IdAndOptionIndex(12L, 0L)).thenReturn(2L);
        when(pollVoteRepo.countByPoll_IdAndOptionIndex(12L, 1L)).thenReturn(1L);
        when(pollVoteRepo.countByPoll_Id(12L)).thenReturn(3L);

        PollResponse result = pollService.getPrivatePoll(TOKEN, VOTER_TOKEN);

        assertTrue(result.hasVoted());
        assertEquals(3L, result.totalVotes());
        assertEquals(2L, result.options().get(0).voteCount());
        assertEquals(1L, result.options().get(1).voteCount());
    }

    @Test
    void privateVoteStopsAtConfiguredCap() {
        poll.setPublic(false);
        poll.setVoteLimit(1);
        when(pollRepo.findPrivateByInviteHashForUpdate(tokenService.hash(TOKEN)))
                .thenReturn(Optional.of(poll));
        when(pollVoteRepo.countByPoll_Id(12L)).thenReturn(1L);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> pollService.votePrivate(TOKEN, VOTER_TOKEN, 0L));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("Voting limit reached.", exception.getReason());
        verify(pollVoteRepo, never()).saveAndFlush(any(PollVote.class));
    }

    @Test
    void privateVoteStoresOnlyTheBrowserTokenHash() {
        poll.setPublic(false);
        poll.setVoteLimit(3);
        when(pollRepo.findPrivateByInviteHashForUpdate(tokenService.hash(TOKEN)))
                .thenReturn(Optional.of(poll));
        when(pollVoteRepo.countByPoll_Id(12L)).thenReturn(0L);
        when(pollVoteRepo.existsByPoll_IdAndVoterTokenHash(12L, tokenService.hash(VOTER_TOKEN)))
                .thenReturn(false);

        pollService.votePrivate(TOKEN, VOTER_TOKEN, 1L);

        verify(pollVoteRepo).saveAndFlush(org.mockito.ArgumentMatchers.argThat(vote ->
                tokenService.hash(VOTER_TOKEN).equals(vote.getVoterTokenHash())
                        && !VOTER_TOKEN.equals(vote.getVoterTokenHash())
                        && vote.getOptionIndex().equals(1L)));
    }

    @Test
    void changingPrivatePollToPublicMakesResultsPublicAndRemovesBrowserTokens() {
        poll.setPublic(false);
        poll.setVoteLimit(4);
        poll.setInviteTokenHash(tokenService.hash(TOKEN));
        poll.setManagementTokenHash(tokenService.hash(MANAGEMENT_TOKEN));
        when(pollRepo.findByManagementTokenHashForUpdate(tokenService.hash(MANAGEMENT_TOKEN)))
                .thenReturn(Optional.of(poll));
        when(pollVoteRepo.countByPoll_Id(12L)).thenReturn(1L);
        when(pollVoteRepo.countByPoll_IdAndOptionIndex(12L, 0L)).thenReturn(1L);
        when(pollVoteRepo.countByPoll_IdAndOptionIndex(12L, 1L)).thenReturn(0L);

        pollService.updateSettings(MANAGEMENT_TOKEN, new PollSettingsRequest(true, null));

        assertTrue(poll.isPublic());
        assertEquals(1L, poll.getOptions().get(0).getVoteCount());
        assertNull(poll.getVoteLimit());
        verify(pollVoteRepo).deleteByPoll_Id(12L);
    }

    @Test
    void privateVoteCapCannotBeLoweredBelowAcceptedVotes() {
        poll.setPublic(false);
        poll.setVoteLimit(10);
        when(pollRepo.findByManagementTokenHashForUpdate(tokenService.hash(MANAGEMENT_TOKEN)))
                .thenReturn(Optional.of(poll));
        when(pollVoteRepo.countByPoll_Id(12L)).thenReturn(4L);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> pollService.updateSettings(MANAGEMENT_TOKEN, new PollSettingsRequest(false, 3)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(pollRepo, never()).saveAndFlush(any(Poll.class));
    }

    @Test
    void malformedSecretIsRejectedWithoutLookingUpPoll() {
        assertThrows(ResponseStatusException.class, () -> pollService.getManagementDetails("guess"));
        verify(pollRepo, never()).findByManagementTokenHash(any());
    }

    private Poll captureSavedPoll() {
        var captor = org.mockito.ArgumentCaptor.forClass(Poll.class);
        verify(pollRepo).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private static final String TOKEN = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String VOTER_TOKEN = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String MANAGEMENT_TOKEN = "ccccccccccccccccccccccccccccccccccccccccccc";
}
