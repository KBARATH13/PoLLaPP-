package com.example.pollapp.service;

import com.example.pollapp.model.Poll;
import com.example.pollapp.repository.PollRepo;
import com.example.pollapp.repository.PollVoteRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class LegacyPollCleanup implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyPollCleanup.class);

    private final PollRepo pollRepo;
    private final PollVoteRepo pollVoteRepo;

    public LegacyPollCleanup(PollRepo pollRepo, PollVoteRepo pollVoteRepo) {
        this.pollRepo = pollRepo;
        this.pollVoteRepo = pollVoteRepo;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Poll> legacyPolls = pollRepo.findAllByManagementTokenHashIsNull();
        if (legacyPolls.isEmpty()) return;
        legacyPolls.forEach(poll -> pollVoteRepo.deleteByPoll_Id(poll.getId()));
        pollRepo.deleteAll(legacyPolls);
        LOGGER.info("Removed {} legacy polls without private management links.", legacyPolls.size());
    }
}
