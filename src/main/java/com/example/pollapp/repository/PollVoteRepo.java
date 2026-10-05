package com.example.pollapp.repository;

import com.example.pollapp.model.PollVote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PollVoteRepo extends JpaRepository<PollVote, Long> {
    boolean existsByPoll_IdAndVoterTokenHash(Long pollId, String voterTokenHash);
    long countByPoll_Id(Long pollId);
    long countByPoll_IdAndOptionIndex(Long pollId, Long optionIndex);
    void deleteByPoll_Id(Long pollId);
}
