package com.changnian.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.changnian.domain.entity.ElderResponseEntity;
import com.changnian.domain.entity.FamilyMemoryEntity;
import com.changnian.domain.entity.TopicCardEntity;
import com.changnian.domain.entity.UserEntity;
import com.changnian.domain.mapper.FamilyMemoryMapper;
import com.changnian.domain.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class MemoryService {
    private final FamilyMemoryMapper familyMemoryMapper;
    private final UserMapper userMapper;
    private final AiService aiService;

    public MemoryService(
            FamilyMemoryMapper familyMemoryMapper,
            UserMapper userMapper,
            AiService aiService
    ) {
        this.familyMemoryMapper = familyMemoryMapper;
        this.userMapper = userMapper;
        this.aiService = aiService;
    }

    public void generateFromResponse(ElderResponseEntity response, TopicCardEntity topic) {
        saveFromResponse(response, topic, false);
    }

    public FamilyMemoryEntity regenerateFromResponse(ElderResponseEntity response, TopicCardEntity topic) {
        return saveFromResponse(response, topic, true);
    }

    private FamilyMemoryEntity saveFromResponse(ElderResponseEntity response, TopicCardEntity topic, boolean strict) {
        if (response == null || response.getId() == null || response.getContent() == null || response.getContent().isBlank()) {
            return null;
        }
        FamilyMemoryEntity existing = familyMemoryMapper.selectOne(Wrappers.<FamilyMemoryEntity>lambdaQuery()
                .eq(FamilyMemoryEntity::getResponseId, response.getId())
                .last("LIMIT 1"));

        UserEntity elder = userMapper.selectById(response.getElderId());
        String elderName = elder == null ? "家人" : elder.getDisplayName();
        String topicTitle = topic == null ? "" : topic.getTitle();
        AiService.AiMemoryResult generated = strict
                ? aiService.generateMemoryStrict(elderName, response.getContent(), topicTitle)
                : aiService.generateMemory(elderName, response.getContent(), topicTitle);
        if (!generated.keep()) {
            if (strict && existing != null) {
                familyMemoryMapper.deleteById(existing.getId());
            }
            return null;
        }

        FamilyMemoryEntity memory = existing == null ? new FamilyMemoryEntity() : existing;
        memory.setFamilyId(response.getFamilyId());
        memory.setElderId(response.getElderId());
        memory.setResponseId(response.getId());
        memory.setTitle(generated.title());
        memory.setContent(generated.content());
        memory.setSourceType(generated.sourceType());
        memory.setCreatedAt(response.getCreatedAt() == null ? LocalDateTime.now() : response.getCreatedAt());
        if (existing == null) {
            familyMemoryMapper.insert(memory);
        } else {
            familyMemoryMapper.updateById(memory);
        }
        return memory;
    }
}
