package com.changnian.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.changnian.auth.CurrentUser;
import com.changnian.auth.Role;
import com.changnian.auth.UserContext;
import com.changnian.common.BizException;
import com.changnian.domain.entity.ElderResponseEntity;
import com.changnian.domain.entity.MicrophoneRequestEntity;
import com.changnian.domain.entity.TopicCardEntity;
import com.changnian.domain.entity.UserEntity;
import com.changnian.domain.mapper.ElderResponseMapper;
import com.changnian.domain.mapper.MicrophoneRequestMapper;
import com.changnian.domain.mapper.TopicCardMapper;
import com.changnian.domain.mapper.UserMapper;
import com.changnian.websocket.SocketHub;
import com.changnian.websocket.SocketMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class InteractionService {
    private final TopicCardMapper topicCardMapper;
    private final MicrophoneRequestMapper microphoneRequestMapper;
    private final ElderResponseMapper elderResponseMapper;
    private final HomeService homeService;
    private final UserMapper userMapper;
    private final SocketHub socketHub;
    private final MemoryService memoryService;
    private final AiService aiService;

    public InteractionService(
            TopicCardMapper topicCardMapper,
            MicrophoneRequestMapper microphoneRequestMapper,
            ElderResponseMapper elderResponseMapper,
            HomeService homeService,
            UserMapper userMapper,
            SocketHub socketHub,
            MemoryService memoryService,
            AiService aiService
    ) {
        this.topicCardMapper = topicCardMapper;
        this.microphoneRequestMapper = microphoneRequestMapper;
        this.elderResponseMapper = elderResponseMapper;
        this.homeService = homeService;
        this.userMapper = userMapper;
        this.socketHub = socketHub;
        this.memoryService = memoryService;
        this.aiService = aiService;
    }

    @Transactional
    public HomeService.PendingRequestView createMicrophoneRequest(Long topicId, String elderUsername) {
        CurrentUser user = requireRole(Role.CHILD);
        TopicCardEntity topic = topicCardMapper.selectById(topicId);
        if (topic == null || !topic.getFamilyId().equals(user.familyId())) {
            throw new BizException("话题不存在");
        }
        UserEntity elder = resolveElder(user.familyId(), elderUsername);
        if (topic.getElderId() != null && !topic.getElderId().equals(elder.getId())) {
            throw new BizException("这张话题卡不属于当前选择的家人");
        }
        MicrophoneRequestEntity request = new MicrophoneRequestEntity();
        request.setFamilyId(user.familyId());
        request.setTopicId(topic.getId());
        request.setChildId(user.id());
        request.setElderId(elder.getId());
        UserEntity child = userMapper.selectById(user.id());
        request.setPromptText(aiService.generateElderPrompt(child == null ? user.displayName() : child.getDisplayName(), elder.getDisplayName(), topic).text());
        request.setStatus("PENDING");
        request.setCreatedAt(LocalDateTime.now());
        microphoneRequestMapper.insert(request);
        HomeService.PendingRequestView view = new HomeService.PendingRequestView(request, topic, HomeService.MemberView.from(child));
        socketHub.sendToUser(user.familyId(), elder.getId(), Role.ELDER, new SocketMessage("MIC_REQUEST_CREATED", view));
        return view;
    }

    @Transactional
    public HomeService.ResponseView respond(Long requestId, String content, String audioUrl) {
        CurrentUser user = requireRole(Role.ELDER);
        MicrophoneRequestEntity request = microphoneRequestMapper.selectById(requestId);
        if (request == null || !request.getFamilyId().equals(user.familyId()) || !request.getElderId().equals(user.id())) {
            throw new BizException("递话筒消息不存在");
        }
        if (!"PENDING".equals(request.getStatus())) {
            throw new BizException("这条消息已经回应过了");
        }
        if ((content == null || content.isBlank()) && (audioUrl == null || audioUrl.isBlank())) {
            throw new BizException("请填写文字或上传录音");
        }
        ElderResponseEntity response = new ElderResponseEntity();
        response.setFamilyId(user.familyId());
        response.setRequestId(request.getId());
        response.setElderId(user.id());
        response.setSourceType("MICROPHONE");
        response.setContent(content);
        response.setAudioUrl(audioUrl);
        response.setCreatedAt(LocalDateTime.now());
        elderResponseMapper.insert(response);

        request.setStatus("RESPONDED");
        request.setRespondedAt(LocalDateTime.now());
        microphoneRequestMapper.updateById(request);

        TopicCardEntity topic = request.getTopicId() == null ? null : topicCardMapper.selectById(request.getTopicId());
        memoryService.generateFromResponse(response, topic);
        HomeService.ResponseView view = new HomeService.ResponseView(response, request, topic);
        socketHub.sendToFamilyRole(user.familyId(), Role.CHILD, new SocketMessage("ELDER_RESPONSE_CREATED", view));
        return view;
    }

    @Transactional
    public HomeService.ResponseView createDirectResponse(String content, String audioUrl) {
        CurrentUser user = requireRole(Role.ELDER);
        if ((content == null || content.isBlank()) && (audioUrl == null || audioUrl.isBlank())) {
            throw new BizException("请填写文字或上传录音");
        }
        ElderResponseEntity response = new ElderResponseEntity();
        response.setFamilyId(user.familyId());
        response.setRequestId(null);
        response.setElderId(user.id());
        response.setSourceType("DIRECT");
        response.setContent(content);
        response.setAudioUrl(audioUrl);
        response.setCreatedAt(LocalDateTime.now());
        elderResponseMapper.insert(response);

        memoryService.generateFromResponse(response, null);
        HomeService.ResponseView view = new HomeService.ResponseView(response, null, null);
        socketHub.sendToFamilyRole(user.familyId(), Role.CHILD, new SocketMessage("ELDER_RESPONSE_CREATED", view));
        return view;
    }

    private CurrentUser requireRole(Role role) {
        CurrentUser user = UserContext.get();
        if (user.role() != role) {
            throw new BizException("当前账号不能执行该操作");
        }
        return user;
    }

    private UserEntity resolveElder(Long familyId, String elderUsername) {
        if (elderUsername == null || elderUsername.isBlank()) {
            return homeService.firstElder(familyId);
        }
        UserEntity elder = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getFamilyId, familyId)
                .eq(UserEntity::getUsername, elderUsername.trim())
                .eq(UserEntity::getRole, Role.ELDER.name())
                .last("LIMIT 1"));
        if (elder == null) {
            throw new BizException("选择的老人账号不存在");
        }
        return elder;
    }

}
