package com.example.pollapp.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Data
@NoArgsConstructor
public class Poll {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY )
    private Long id;
    private String question;
    @ElementCollection
    @CollectionTable(name = "poll_options", joinColumns = @JoinColumn(name = "poll_id"))
    @OrderColumn(name = "option_position")
    private List<VoteOption> options;

    @Column(nullable = false)
    private boolean isPublic = true;

    private Integer voteLimit;

    @Column(length = 64, unique = true)
    private String inviteTokenHash;

    @Column(length = 64, unique = true)
    private String managementTokenHash;

}
