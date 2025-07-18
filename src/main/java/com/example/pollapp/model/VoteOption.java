package com.example.pollapp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@Embeddable
@AllArgsConstructor

public class VoteOption {
    @Column(name = "option_text") // Avoid reserved keyword

    private String voteoption;
    private Long voteCount=0L;
}
