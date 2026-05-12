package com.citytrip.controller;

import com.citytrip.annotation.LoginRequired;
import com.citytrip.common.SystemBusyException;
import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.vo.ChatSkillPayloadVO;
import com.citytrip.model.vo.ChatVO;
import com.citytrip.service.ChatService;
import com.citytrip.service.guard.AiRequestGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/chat/messages")
public class ChatController {
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final long STREAM_TIMEOUT_MILLIS = 60_000L;

    private final ChatService chatService;
    private final TaskExecutor chatStreamExecutor;
    private final AiRequestGuard aiRequestGuard;

    public ChatController(ChatService chatService,
                          @Qualifier("chatStreamExecutor") TaskExecutor chatStreamExecutor,
                          AiRequestGuard aiRequestGuard) {
        this.chatService = chatService;
        this.chatStreamExecutor = chatStreamExecutor;
        this.aiRequestGuard = aiRequestGuard;
    }

    @LoginRequired
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamQuestion(@Valid @RequestBody ChatReqDTO req, HttpServletRequest request) {
        bindCurrentUser(req, currentUserId(request));
        log.info("Received streaming chat request. questionLength={}, hasContext={}", questionLength(req), hasContext(req));
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        AiRequestGuard.GuardPermit guardPermit = aiRequestGuard.acquire("stream", guardSubject(request));
        AtomicBoolean connectionOpen = new AtomicBoolean(true);
        emitter.onCompletion(() -> {
            connectionOpen.set(false);
            log.debug("Streaming chat request completed.");
        });
        emitter.onTimeout(() -> {
            connectionOpen.set(false);
            log.warn("Streaming chat request timed out.");
            emitter.complete();
        });
        emitter.onError(ex -> {
            connectionOpen.set(false);
            log.debug("Streaming chat connection closed. reason={}", ex == null ? "unknown" : ex.getMessage());
        });

        try {
            chatStreamExecutor.execute(() -> handleStreamingRequest(req, emitter, connectionOpen, guardPermit));
        } catch (TaskRejectedException ex) {
            guardPermit.close();
            log.warn("Streaming chat request rejected because the executor is saturated.");
            throw new SystemBusyException("\u5f53\u524d\u804a\u5929\u8bf7\u6c42\u8f83\u591a\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5");
        }
        return emitter;
    }

    private void handleStreamingRequest(ChatReqDTO req,
                                        SseEmitter emitter,
                                        AtomicBoolean connectionOpen,
                                        AiRequestGuard.GuardPermit guardPermit) {
        try {
            AtomicBoolean emittedAnyToken = new AtomicBoolean(false);
            ChatVO result = chatService.streamAnswer(req, token -> {
                if (sendEvent(emitter, tokenEvent(token), connectionOpen)) {
                    emittedAnyToken.set(true);
                }
            });
            if (!connectionOpen.get()) {
                emitter.complete();
                return;
            }
            if (!emittedAnyToken.get() && result != null && result.getAnswer() != null && !result.getAnswer().trim().isEmpty()) {
                sendEvent(emitter, tokenEvent(result.getAnswer()), connectionOpen);
            }
            sendEvent(emitter, metaEvent(
                    result == null ? List.of() : result.getRelatedTips(),
                    result == null ? List.of() : result.getEvidence(),
                    result == null ? null : result.getSkillPayload()
            ), connectionOpen);
            sendEvent(emitter, doneEvent(), connectionOpen);
            emitter.complete();
        } catch (Exception ex) {
            if (!connectionOpen.get()) {
                log.debug("Streaming chat background task stopped after the client disconnected.");
                emitter.complete();
                return;
            }
            log.warn("Streaming chat request failed. reason={}", ex.getMessage(), ex);
            sendEvent(emitter, errorEvent(ex.getMessage()), connectionOpen);
            emitter.complete();
        } finally {
            guardPermit.close();
        }
    }

    private boolean sendEvent(SseEmitter emitter, Map<String, Object> payload, AtomicBoolean connectionOpen) {
        if (!connectionOpen.get()) {
            return false;
        }
        try {
            emitter.send(SseEmitter.event().data(payload, MediaType.APPLICATION_JSON));
            return true;
        } catch (IOException | IllegalStateException ex) {
            connectionOpen.set(false);
            log.debug("Failed to send SSE event because the connection is no longer writable. reason={}", ex.getMessage());
            return false;
        }
    }

    private Map<String, Object> tokenEvent(String token) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "token");
        payload.put("content", token);
        return payload;
    }

    private Map<String, Object> metaEvent(List<String> relatedTips, List<String> evidence, ChatSkillPayloadVO skillPayload) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "meta");
        payload.put("relatedTips", relatedTips == null ? List.of() : relatedTips);
        payload.put("evidence", evidence == null ? List.of() : evidence);
        payload.put("skillPayload", skillPayload);
        return payload;
    }

    private Map<String, Object> doneEvent() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "done");
        return payload;
    }

    private Map<String, Object> errorEvent(String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "error");
        payload.put("message", sanitizeErrorMessage(message));
        return payload;
    }

    private String sanitizeErrorMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "刚才没有拿到有效回答，请稍后重试或换个说法继续。";
        }
        String value = message.trim();
        if (value.contains("Model request failed")
                || value.contains("OpenAI message content is empty")
                || value.contains("endpoint=")
                || value.contains("OPENAI_")) {
            return "刚才没有拿到有效回答，请稍后重试或换个说法继续。";
        }
        return value;
    }

    private int questionLength(ChatReqDTO req) {
        if (req == null || req.getQuestion() == null) {
            return 0;
        }
        return req.getQuestion().trim().length();
    }

    private boolean hasContext(ChatReqDTO req) {
        return req != null && req.getContext() != null;
    }

    private void bindCurrentUser(ChatReqDTO req, Long currentUserId) {
        if (req == null || currentUserId == null) {
            return;
        }
        if (req.getContext() == null) {
            req.setContext(new ChatReqDTO.ChatContext());
        }
        req.getContext().setCurrentUserId(currentUserId);
    }

    private Long currentUserId(HttpServletRequest request) {
        return request == null ? null : (Long) request.getAttribute(com.citytrip.common.AuthConstants.LOGIN_USER_ID);
    }

    private String guardSubject(HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId != null) {
            return "user:" + userId;
        }
        String remoteAddr = request == null ? null : request.getRemoteAddr();
        return remoteAddr == null || remoteAddr.trim().isEmpty() ? "anonymous" : "anon:" + remoteAddr.trim();
    }
}

