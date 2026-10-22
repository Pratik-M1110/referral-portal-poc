package com.referralportal.controller;

import com.referralportal.service.ConversationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static com.referralportal.dto.ConversationDtos.ConversationView;
import static com.referralportal.dto.ConversationDtos.MeetRequest;
import static com.referralportal.dto.ConversationDtos.MessageInput;
import static com.referralportal.dto.ConversationDtos.ThreadView;

@RestController
public class ConversationController {

    @Autowired
    private ConversationService conversations;

    @GetMapping("/api/conversations")
    public List<ConversationView> list(Authentication auth) {
        return conversations.list(auth);
    }

    @GetMapping("/api/conversations/{id}/messages")
    public ThreadView thread(Authentication auth, @PathVariable long id) {
        return conversations.thread(auth, id);
    }

    @PostMapping("/api/conversations/{id}/messages")
    public Map<String, Object> send(Authentication auth, @PathVariable long id, @Valid @RequestBody MessageInput input) {
        return conversations.send(auth, id, input);
    }

    @PostMapping("/api/conversations")
    public Map<String, Object> request(Authentication auth, @Valid @RequestBody MeetRequest input) {
        return conversations.request(auth, input);
    }

    @PostMapping("/api/conversations/{id}/accept")
    public Map<String, Object> accept(Authentication auth, @PathVariable long id) {
        return conversations.accept(auth, id);
    }

    @PostMapping("/api/conversations/{id}/decline")
    public Map<String, Object> decline(Authentication auth, @PathVariable long id) {
        return conversations.decline(auth, id);
    }
}
