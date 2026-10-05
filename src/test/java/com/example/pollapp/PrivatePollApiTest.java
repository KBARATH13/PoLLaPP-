package com.example.pollapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PrivatePollApiTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Transactional
    @Rollback
    void privatePollIsInviteOnlyCapsVotesAndCanBePublishedWithManagementLink() throws Exception {
        JsonNode created = objectMapper.readTree(mockMvc.perform(post("/polls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"Team lunch?","options":["Noodles","Tacos"],"isPublic":false,"voteLimit":2}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String inviteToken = created.get("inviteToken").asText();
        String managementToken = created.get("managementToken").asText();
        String voterOne = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        String voterTwo = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        String voterThree = "ccccccccccccccccccccccccccccccccccccccccccc";
        assertTrue(inviteToken.matches("[A-Za-z0-9_-]{43}"));
        assertTrue(managementToken.matches("[A-Za-z0-9_-]{43}"));
        assertTrue(created.get("poll").get("totalVotes").isNull());

        JsonNode publicPolls = objectMapper.readTree(mockMvc.perform(get("/polls"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals(0, publicPolls.size());

        JsonNode beforeVote = privatePoll(inviteToken, voterOne);
        assertFalse(beforeVote.get("hasVoted").asBoolean());
        assertTrue(beforeVote.get("options").get(0).get("voteCount").isNull());
        assertTrue(beforeVote.get("totalVotes").isNull());

        mockMvc.perform(post("/polls/private/vote")
                        .header("X-Poll-Invite", inviteToken)
                        .header("X-Poll-Voter", voterOne)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"optionIndex":0}
                                """))
                .andExpect(status().isOk());

        JsonNode afterVote = privatePoll(inviteToken, voterOne);
        assertTrue(afterVote.get("hasVoted").asBoolean());
        assertEquals(1, afterVote.get("totalVotes").asInt());
        assertEquals(100, Math.round(afterVote.get("options").get(0).get("voteCount").asInt() * 100f
                / afterVote.get("totalVotes").asInt()));

        mockMvc.perform(post("/polls/private/vote")
                        .header("X-Poll-Invite", inviteToken)
                        .header("X-Poll-Voter", voterOne)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"optionIndex":1}
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/polls/private/vote")
                        .header("X-Poll-Invite", inviteToken)
                        .header("X-Poll-Voter", voterTwo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"optionIndex":1}
                                """))
                .andExpect(status().isOk());

        JsonNode capped = privatePoll(inviteToken, voterThree);
        assertTrue(capped.get("limitReached").asBoolean());
        assertTrue(capped.get("totalVotes").isNull());
        mockMvc.perform(post("/polls/private/vote")
                        .header("X-Poll-Invite", inviteToken)
                        .header("X-Poll-Voter", voterThree)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"optionIndex":0}
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/polls/manage")
                        .header("X-Poll-Manage", managementToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isPublic":true,"voteLimit":null}
                                """))
                .andExpect(status().isOk());
        JsonNode published = objectMapper.readTree(mockMvc.perform(get("/polls"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals(1, published.size());
        assertEquals(2, published.get(0).get("totalVotes").asInt());

        mockMvc.perform(delete("/polls/manage")
                        .header("X-Poll-Manage", managementToken))
                .andExpect(status().isNoContent());
        assertEquals(0, objectMapper.readTree(mockMvc.perform(get("/polls"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).size());
    }

    private JsonNode privatePoll(String inviteToken, String voterToken) throws Exception {
        String body = mockMvc.perform(get("/polls/private")
                        .header("X-Poll-Invite", inviteToken)
                        .header("X-Poll-Voter", voterToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

}
