package com.example.pollapp.repository;

import com.example.pollapp.model.Poll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PollRepo extends JpaRepository<Poll, Long> {
    @Query("SELECT p FROM Poll p JOIN FETCH p.options")
    List<Poll> findAllWithOptions();
}
