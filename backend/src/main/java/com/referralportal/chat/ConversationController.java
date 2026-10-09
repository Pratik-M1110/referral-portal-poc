package com.referralportal.chat;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static com.referralportal.chat.ConversationDtos.ConversationView;
import static com.referralportal.chat.ConversationDtos.MeetRequest;
import static com.referralportal.chat.ConversationDtos.MessageInput;
import static com.referralportal.chat.ConversationDtos.ThreadView;

@RestController
class ConversationController {

    private final ConversationService conversations;

    ConversationController(ConversationService conversations) {
        this.conversations = conversations;
    }

    @GetMapping("/api/conversations")
    List<ConversationView> list(Authentication auth) {
        return conversations.list(auth);
    }

    @GetMapping("/api/conversations/{id}/messages")
    ThreadView thread(Authentication auth, @PathVariable long id) {
        return conversations.thread(auth, id);
    }

    @PostMapping("/api/conversations/{id}/messages")
    Map<String, Object> send(Authentication auth, @PathVariable long id, @Valid @RequestBody MessageInput input) {
        return conversations.send(auth, id, input);
    }

    @PostMapping("/api/conversations")
    Map<String, Object> request(Authentication auth, @Valid @RequestBody MeetRequest input) {
        return conversations.request(auth, input);
    }

    @PostMapping("/api/conversations/{id}/accept")
    Map<String, Object> accept(Authentication auth, @PathVariable long id) {
        return conversations.accept(auth, id);
    }

    @PostMapping("/api/conversations/{id}/decline")
    Map<String, Object> decline(Authentication auth, @PathVariable long id) {
        return conversations.decline(auth, id);
    }
}
