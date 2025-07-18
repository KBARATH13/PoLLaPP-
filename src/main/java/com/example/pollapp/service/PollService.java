package com.example.pollapp.service;

import com.example.pollapp.model.Poll;
import com.example.pollapp.model.VoteOption;
import com.example.pollapp.repository.PollRepo;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PollService {

    @Autowired
    private PollRepo pollRepo;

    public Poll createPoll(Poll poll){
        return pollRepo.save(poll);
    }

    public List<Poll> getAllPolls(){
        return pollRepo.findAllWithOptions();
    }
    @Transactional
    public void vote(Long pollId, Long optionIndex) {
        Poll poll = pollRepo.findById(pollId)
                .orElseThrow(() -> new RuntimeException("Poll not found"));
        System.out.println("Fetched poll: " + poll);

        List<VoteOption> options = poll.getOptions();
        if (optionIndex < 0 || optionIndex >= options.size()) {
            throw new IllegalArgumentException("Invalid option index");
        }

        VoteOption selectedOption = options.get(optionIndex.intValue());
        System.out.println("Selected option before voting: " + selectedOption);
        selectedOption.setVoteCount(selectedOption.getVoteCount() + 1);
        System.out.println("Selected option after voting: " + selectedOption);

        pollRepo.save(poll);
        System.out.println("Poll saved successfully");
    }
    // Update a poll
    public Poll updatePoll(Long pollId, Poll updatedPoll) {
        Poll existingPoll = pollRepo.findById(pollId)
                .orElseThrow(() -> new RuntimeException("Poll not found"));

        existingPoll.setQuestion(updatedPoll.getQuestion());
        existingPoll.setOptions(updatedPoll.getOptions());
        return pollRepo.save(existingPoll);
    }

    // Delete a poll
    public void deletePoll(Long pollId) {
        if (!pollRepo.existsById(pollId)) {
            throw new RuntimeException("Poll not found");
        }
        pollRepo.deleteById(pollId);
    }
}
