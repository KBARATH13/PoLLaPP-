package com.example.pollapp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "private_poll_votes", uniqueConstraints = {
        @UniqueConstraint(name = "uk_poll_voter_token", columnNames = {"poll_id", "voter_token_hash"})
})
@Getter
@Setter
@NoArgsConstructor
public class PollVote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "poll_id", nullable = false)
    private Poll poll;

    @Column(name = "voter_token_hash", nullable = false, length = 64)
    private String voterTokenHash;

    @Column(name = "option_index", nullable = false)
    private Long optionIndex;

    public PollVote(Poll poll, String voterTokenHash, Long optionIndex) {
        this.poll = poll;
        this.voterTokenHash = voterTokenHash;
        this.optionIndex = optionIndex;
    }
}
