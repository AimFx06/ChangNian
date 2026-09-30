package com.changnian.websocket;

import com.changnian.auth.CurrentUser;
import com.changnian.auth.TokenService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class AppWebSocketHandler extends TextWebSocketHandler {
    private final TokenService tokenService;
    private final SocketHub socketHub;

    public AppWebSocketHandler(TokenService tokenService, SocketHub socketHub) {
        this.tokenService = tokenService;
        this.socketHub = socketHub;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = UriComponentsBuilder.fromUri(session.getUri()).build()
                .getQueryParams()
                .getFirst("token");
        if (token == null || token.isBlank()) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("missing token"));
            return;
        }
        CurrentUser user = tokenService.parse(token);
        socketHub.register(session, user);
        session.sendMessage(new TextMessage("{\"type\":\"CONNECTED\",\"payload\":{\"role\":\"" + user.role().name() + "\"}}"));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        socketHub.unregister(session);
    }
}
