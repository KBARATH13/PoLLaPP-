package com.example.pollapp.repository;

import com.example.pollapp.model.Poll;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PollRepo extends JpaRepository<Poll, Long> {
    @Query("SELECT DISTINCT p FROM Poll p LEFT JOIN FETCH p.options WHERE p.isPublic = true")
    List<Poll> findAllPublicWithOptions();

    @Query("SELECT DISTINCT p FROM Poll p LEFT JOIN FETCH p.options WHERE p.inviteTokenHash = :tokenHash AND p.isPublic = false")
    Optional<Poll> findPrivateWithOptionsByInviteHash(@Param("tokenHash") String tokenHash);

    Optional<Poll> findByManagementTokenHash(String managementTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Poll p WHERE p.id = :pollId")
    Optional<Poll> findByIdForUpdate(@Param("pollId") Long pollId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Poll p WHERE p.managementTokenHash = :tokenHash")
    Optional<Poll> findByManagementTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Poll p WHERE p.inviteTokenHash = :tokenHash AND p.isPublic = false")
    Optional<Poll> findPrivateByInviteHashForUpdate(@Param("tokenHash") String tokenHash);

    List<Poll> findAllByManagementTokenHashIsNull();
}
