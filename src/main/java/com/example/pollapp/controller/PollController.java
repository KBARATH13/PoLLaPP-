package com.example.pollapp.controller;

import com.example.pollapp.service.PollService;
import com.example.pollapp.model.Poll;
import com.example.pollapp.request.Vote;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/polls")
public class PollController {

    @Autowired
    private PollService pollService;

    @PostMapping
    public Poll createPoll(@RequestBody Poll poll) {
        return pollService.createPoll(poll);
    }
    @GetMapping
    public List<Poll> getAllPolls() {
        List<Poll> polls = pollService.getAllPolls();
        System.out.println("Fetched polls: " + polls); // Debugging log
        return polls;
    }
    @PostMapping("/vote")
    public void vote(@Validated @RequestBody Vote vote){
        System.out.println("Received vote request: " + vote);
        pollService.vote(vote.getPollId(), vote.getOptionIndex());
    }
    @PutMapping("/{pollId}")
    public Poll updatePoll(@PathVariable Long pollId, @RequestBody Poll updatedPoll) {
        return pollService.updatePoll(pollId, updatedPoll);
    }

    // Delete a poll
    @DeleteMapping("/{pollId}")
    public void deletePoll(@PathVariable Long pollId) {
        pollService.deletePoll(pollId);
    }
}
