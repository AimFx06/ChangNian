package com.changnian.websocket;

import com.changnian.auth.CurrentUser;
import com.changnian.auth.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SocketHub {
    private final ObjectMapper objectMapper;
    private final Map<Long, Map<Role, Set<WebSocketSession>>> sessions = new ConcurrentHashMap<>();

    public SocketHub(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void register(WebSocketSession session, CurrentUser user) {
        session.getAttributes().put("user", user);
        sessions
                .computeIfAbsent(user.familyId(), key -> new ConcurrentHashMap<>())
                .computeIfAbsent(user.role(), key -> ConcurrentHashMap.newKeySet())
                .add(session);
    }

    public void unregister(WebSocketSession session) {
        CurrentUser user = (CurrentUser) session.getAttributes().get("user");
        if (user == null) {
            return;
        }
        Map<Role, Set<WebSocketSession>> familySessions = sessions.get(user.familyId());
        if (familySessions == null) {
            return;
        }
        Set<WebSocketSession> roleSessions = familySessions.get(user.role());
        if (roleSessions != null) {
            roleSessions.remove(session);
        }
    }

    public void sendToFamilyRole(Long familyId, Role role, SocketMessage message) {
        Map<Role, Set<WebSocketSession>> familySessions = sessions.get(familyId);
        if (familySessions == null) {
            return;
        }
        Set<WebSocketSession> roleSessions = familySessions.get(role);
        if (roleSessions == null) {
            return;
        }
        roleSessions.removeIf(session -> !session.isOpen());
        roleSessions.forEach(session -> send(session, message));
    }

    public void sendToUser(Long familyId, Long userId, Role role, SocketMessage message) {
        Map<Role, Set<WebSocketSession>> familySessions = sessions.get(familyId);
        if (familySessions == null) {
            return;
        }
        Set<WebSocketSession> roleSessions = familySessions.get(role);
        if (roleSessions == null) {
            return;
        }
        roleSessions.removeIf(session -> !session.isOpen());
        roleSessions.stream()
                .filter(session -> {
                    CurrentUser user = (CurrentUser) session.getAttributes().get("user");
                    return user != null && userId.equals(user.id());
                })
                .forEach(session -> send(session, message));
    }

    private void send(WebSocketSession session, SocketMessage message) {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
        } catch (Exception ignored) {
        }
    }
}
