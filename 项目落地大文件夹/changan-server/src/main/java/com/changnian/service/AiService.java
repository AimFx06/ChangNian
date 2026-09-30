package com.changnian.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.changnian.auth.CurrentUser;
import com.changnian.auth.Role;
import com.changnian.auth.UserContext;
import com.changnian.common.BizException;
import com.changnian.domain.entity.TopicCardEntity;
import com.changnian.domain.mapper.TopicCardMapper;
import com.changnian.domain.entity.UserEntity;
import com.changnian.domain.mapper.UserMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiService {
    private final RestTemplate restTemplate;
    private final TopicCardMapper topicCardMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public AiService(
            RestTemplate restTemplate,
            TopicCardMapper topicCardMapper,
            UserMapper userMapper,
            ObjectMapper objectMapper,
            @Value("${app.deepseek.api-key}") String apiKey,
            @Value("${app.deepseek.base-url}") String baseUrl,
            @Value("${app.deepseek.model}") String model
    ) {
        this.restTemplate = restTemplate;
        this.topicCardMapper = topicCardMapper;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
    }

    public boolean isDeepSeekConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public AiTextResult generateWeeklyReport(List<String> signals) {
        if (!isDeepSeekConfigured()) {
            return new AiTextResult(localWeekly(signals), true);
        }
        String prompt = """
                你是“常念”的家庭叙事提词器。请根据老人最近的真实信号生成一段给子女看的周报。
                要求：
                1. 不要医疗判断，不要吓人；
                2. 不要代替家人说话；
                3. 用中文家庭语境，像家书一样温和；
                4. 只基于输入信号，不编造。

                信号：
                """ + String.join("\n", safeSignals(signals));
        return new AiTextResult(callDeepSeek(prompt), false);
    }

    public AiTextResult generateTopics(List<String> signals) {
        if (!isDeepSeekConfigured()) {
            return new AiTextResult(localTopics(), true);
        }
        String prompt = """
                你是“常念”的话题卡生成器。请根据老人最近信号生成 5 个子女可以主动聊起的话题。
                每个话题包含：类别、标题、为什么可以聊。禁止输出“建议你发送”这类代写消息。
                AI 只做提词器，不做代言人。

                信号：
                """ + String.join("\n", safeSignals(signals));
        return new AiTextResult(callDeepSeek(prompt), false);
    }

    public AiWeeklyReportResult generateWeeklyReportCard(String elderDisplayName, List<String> signals) {
        if (!isDeepSeekConfigured()) {
            return localWeeklyReportCard(elderDisplayName, signals);
        }
        String prompt = """
                你是“常念”的家庭周报整理器。请根据老人最近 7 天的真实数据，生成一张给子女看的周报卡片。
                规则：
                1. 只基于输入信号整理，不编造没有出现的人、地点、病情、检查结果或承诺；
                2. 不做诊断，不制造焦虑，不评价子女做得好坏；
                3. summary 像家里人之间的周小结，温和、具体、有生活感，控制在 130 到 190 个汉字；
                4. title 控制在 18 个汉字以内；keywords 输出 3 到 5 个逗号分隔关键词；
                5. quoteText 只能引用或转述输入中老人真实说过的话；没有可引用内容时写“暂无可引用的话”；
                6. 输出必须是合法 json，不要 markdown，不要解释。

                json 格式：
                {
                  "title": "妈妈这周惦记家里小事",
                  "summary": "这周妈妈提到晚饭、天气和家里人，整体语气平稳。她说天气热，提醒蓝心下班路上记得喝水，也聊到晚上准备蒸包子。用药记录显示本周已按时确认两次，后续可以继续轻轻问问身体感受。",
                  "keywords": "晚饭,天气,用药,家里人",
                  "quoteText": "她说：今天外头热，你下班路上别急，记得喝水。"
                }

                老人称呼：%s
                信号：
                %s
                """.formatted(cleanText(elderDisplayName, "家人", 40), String.join("\n", safeSignals(signals)));
        WeeklyReportSuggestion suggestion = parseWeeklyReport(callDeepSeek(prompt, true, 1200));
        return new AiWeeklyReportResult(
                cleanText(suggestion.title(), cleanText(elderDisplayName, "家人", 20) + "这周的近况", 60),
                cleanText(suggestion.summary(), localWeekly(signals), 420),
                cleanText(suggestion.keywords(), "日常,近况,陪伴", 120),
                cleanText(suggestion.quoteText(), "暂无可引用的话", 180),
                false
        );
    }

    public AiDailyStatusResult generateDailyStatuses(String elderDisplayName, LocalDate startDate, LocalDate endDate, List<String> signals) {
        if (!isDeepSeekConfigured()) {
            return localDailyStatuses(elderDisplayName, startDate, endDate, signals);
        }
        String prompt = """
                你是“常念”的每日近况整理器。请根据老人最近的真实数据，为指定日期范围整理每日近况。
                规则：
                1. 日期范围从 %s 到 %s，每天最多一条；
                2. 只基于输入信号，不编造没有出现的人、地点、活动、病情和就诊结论；
                3. 如果某天没有足够信号，可以用“今天暂无新的长留言，已有记录显示状态平稳”这类克制表达，不要编故事；
                4. content 控制在 45 到 85 个汉字，适合放在近况卡片里；
                5. tone 只能是 good、normal、watch 三者之一：积极轻松用 good，普通平稳用 normal，需要子女温和留意用 watch；
                6. 输出必须是合法 json，不要 markdown，不要解释。

                json 格式：
                {
                  "statuses": [
                    {
                      "statusDate": "2026-07-05",
                      "title": "今天",
                      "content": "妈妈今天说外头热，提醒蓝心下班路上别急着赶路，还说自己这边都挺好，让家里不用惦记。",
                      "tone": "normal"
                    }
                  ]
                }

                老人称呼：%s
                信号：
                %s
                """.formatted(startDate, endDate, cleanText(elderDisplayName, "家人", 40), String.join("\n", safeSignals(signals)));
        DailyStatusPayload payload = parseDailyStatuses(callDeepSeek(prompt, true, 1800));
        List<AiDailyStatus> statuses = payload.statuses().stream()
                .filter(item -> item.statusDate() != null && !item.statusDate().isBlank())
                .map(item -> new AiDailyStatus(
                        cleanText(item.statusDate(), startDate.toString(), 20),
                        cleanText(item.title(), "近况", 40),
                        cleanText(item.content(), "今天暂无新的长留言，已有记录显示状态平稳。", 180),
                        normalizeTone(item.tone())
                ))
                .limit(8)
                .toList();
        if (statuses.isEmpty()) {
            throw new BizException("DeepSeek 返回的每日近况为空");
        }
        return new AiDailyStatusResult(statuses, false);
    }

    public TopicRefreshResult refreshTopicCards(List<String> signals, String elderUsername) {
        CurrentUser user = UserContext.get();
        if (user.role() != Role.CHILD) {
            throw new BizException("只有子女端可以生成话题卡");
        }
        UserEntity elder = resolveElder(user.familyId(), elderUsername);

        TopicGenerationResult generated = generateTopicSuggestions(signals);
        topicCardMapper.update(null, Wrappers.<TopicCardEntity>lambdaUpdate()
                .eq(TopicCardEntity::getFamilyId, user.familyId())
                .eq(TopicCardEntity::getElderId, elder.getId())
                .eq(TopicCardEntity::getStatus, "ACTIVE")
                .set(TopicCardEntity::getStatus, "ARCHIVED"));

        List<TopicCardEntity> topics = new ArrayList<>();
        for (TopicSuggestion suggestion : generated.topics()) {
            TopicCardEntity topic = new TopicCardEntity();
            topic.setFamilyId(user.familyId());
            topic.setElderId(elder.getId());
            topic.setCategory(cleanText(suggestion.category(), "日常唠嗑", 20));
            topic.setTitle(cleanText(suggestion.title(), "今天聊点家里的小事", 60));
            topic.setContent(cleanText(suggestion.content(), "从最近的近况记录自然开口，轻轻问一句。", 240));
            String sourcePrefix = generated.mocked() ? "DeepSeek 本地兜底" : "DeepSeek";
            topic.setSourceText(sourcePrefix + " · " + cleanText(suggestion.sourceText(), "根据最近近况生成", 80));
            topic.setStatus("ACTIVE");
            topic.setCreatedAt(LocalDateTime.now());
            topicCardMapper.insert(topic);
            topics.add(topic);
        }
        return new TopicRefreshResult(topics, generated.mocked());
    }

    private UserEntity resolveElder(Long familyId, String elderUsername) {
        UserEntity elder = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getFamilyId, familyId)
                .eq(UserEntity::getRole, Role.ELDER.name())
                .eq(elderUsername != null && !elderUsername.isBlank(), UserEntity::getUsername, elderUsername == null ? "" : elderUsername.trim())
                .orderByAsc(UserEntity::getId)
                .last("LIMIT 1"));
        if (elder == null) {
            throw new BizException("选择的老人账号不存在");
        }
        return elder;
    }

    public AiTextResult generateElderPrompt(String childName, String elderName, TopicCardEntity topic) {
        String fallback = localElderPrompt(childName, topic);
        if (!isDeepSeekConfigured()) {
            return new AiTextResult(fallback, true);
        }
        String prompt = """
                你是“常念”的递话筒转述器。请把子女端的话题卡改写成老人端能直接听懂、并且容易开口回复的一句话。
                要求：
                1. 像家人递过来的一句话，不要像系统提示；
                2. 只保留一个清楚的问题或话头，让老人能马上回答；
                3. 不要说“话题卡、AI、建议、生成、用户”等产品词；
                4. 不做医疗判断，不制造焦虑，不编造输入没有的信息；
                5. 注意人称转换：原话题里的“妈妈、爸爸、她、他”在老人端都要改成“你”；
                6. 例如“小外孙照片她看了吗”必须改成“小外孙照片你看了吗”；
                7. 控制在 45 个汉字以内，只输出这一句话。

                子女姓名：%s
                老人称呼：%s
                话题类别：%s
                话题标题：%s
                话题内容：%s
                """.formatted(
                cleanText(childName, "孩子", 40),
                cleanText(elderName, "你", 40),
                cleanText(topic == null ? "" : topic.getCategory(), "日常聊天", 40),
                cleanText(topic == null ? "" : topic.getTitle(), "", 100),
                cleanText(topic == null ? "" : topic.getContent(), "", 300)
        );
        try {
            return new AiTextResult(normalizeElderPronouns(cleanText(callDeepSeek(prompt, false, 220), fallback, 90)), false);
        } catch (BizException exception) {
            return new AiTextResult(fallback, true);
        }
    }

    public AiMemoryResult generateMemory(String elderDisplayName, String responseContent, String topicTitle) {
        return generateMemoryInternal(elderDisplayName, responseContent, topicTitle, true);
    }

    public AiMemoryResult generateMemoryStrict(String elderDisplayName, String responseContent, String topicTitle) {
        return generateMemoryInternal(elderDisplayName, responseContent, topicTitle, false);
    }

    private AiMemoryResult generateMemoryInternal(String elderDisplayName, String responseContent, String topicTitle, boolean allowLocalFallback) {
        String content = cleanText(responseContent, "", 500);
        if (content.isBlank()) {
            return new AiMemoryResult(false, "", "", "LOCAL", "寒暄短句不记录");
        }
        if (!isDeepSeekConfigured()) {
            if (!allowLocalFallback) {
                throw new BizException("未配置 DeepSeek API Key，无法真实生成说过的事");
            }
            return localMemory(elderDisplayName, content, topicTitle);
        }
        String prompt = """
                你是“常念”的家庭记忆整理器。请判断老人这条回应是否值得整理成“说起过的事”。
                规则：
                1. 只记录具体事情、近况、人物、地点、身体感受、回忆、计划或家里小事；
                2. “晚上好、你好、好的、嗯、谢谢、收到、晚安”这类寒暄或纯回应不要记录；
                3. 不编造输入里没有的信息；
                4. 标题控制在 8 个汉字以内，内容控制在 80 个汉字以内；
                5. 输出必须是合法 json，不要 markdown，不要解释。

                json 格式：
                {
                  "keep": true,
                  "title": "日常小事",
                  "content": "妈妈说今天中午做了糖醋排骨，还特意把盘子摆正。",
                  "reason": "包含具体生活事件"
                }

                老人：%s
                相关话题：%s
                老人回应：%s
                """.formatted(cleanText(elderDisplayName, "家人", 40), cleanText(topicTitle, "无", 80), content);
        try {
            MemorySuggestion suggestion = parseMemorySuggestion(callDeepSeek(prompt, true, 800));
            if (!suggestion.keep()) {
                return new AiMemoryResult(false, "", "", "AI", suggestion.reason());
            }
            String title = cleanText(suggestion.title(), "说起过的事", 20);
            String memoryContent = cleanText(suggestion.content(), content, 120);
            if (title.isBlank() || memoryContent.isBlank()) {
                return localMemory(elderDisplayName, content, topicTitle);
            }
            return new AiMemoryResult(true, title, memoryContent, "AI", suggestion.reason());
        } catch (BizException exception) {
            if (!allowLocalFallback) {
                throw exception;
            }
            return localMemory(elderDisplayName, content, topicTitle);
        }
    }

    private String callDeepSeek(String prompt) {
        return callDeepSeek(prompt, false, 900);
    }

    private String callDeepSeek(String prompt, boolean jsonMode, int maxTokens) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("temperature", 0.6);
            body.put("max_tokens", maxTokens);
            body.put("stream", false);
            body.put("thinking", Map.of("type", "disabled"));
            body.put("messages", List.of(
                    Map.of("role", "system", "content", "你是常念，一个克制的家庭关系提词器。"),
                    Map.of("role", "user", "content", prompt)
            ));
            if (jsonMode) {
                body.put("response_format", Map.of("type", "json_object"));
            }

            Map<?, ?> response = restTemplate.postForObject(
                    baseUrl + "/chat/completions",
                    new HttpEntity<>(body, headers),
                    Map.class
            );
            if (response == null) {
                throw new BizException("DeepSeek 没有返回内容");
            }
            List<?> choices = (List<?>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new BizException("DeepSeek 返回格式不完整");
            }
            Map<?, ?> choice = (Map<?, ?>) choices.get(0);
            Map<?, ?> message = (Map<?, ?>) choice.get("message");
            Object content = message == null ? null : message.get("content");
            if (content == null || String.valueOf(content).isBlank()) {
                throw new BizException("DeepSeek 返回内容为空");
            }
            return String.valueOf(content);
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException("DeepSeek 调用失败：" + exception.getMessage());
        }
    }

    private TopicGenerationResult generateTopicSuggestions(List<String> signals) {
        if (!isDeepSeekConfigured()) {
            return new TopicGenerationResult(localTopicCards(signals), true);
        }
        String prompt = """
                你是“常念”的话题卡生成器。请根据老人最近信号生成 5 个子女可以主动聊起的话题卡。
                规则：
                1. 只基于输入信号，不编造病情、地点、人物关系、检查结果和老人没说过的话；
                2. 不做医疗判断，不制造焦虑；
                3. 不代替家人说话，只给可以聊的引子；
                4. 优先使用“最近留言”“近况”“用药记录”“就诊记录”“说过的事”等真实历史数据；
                5. 如果输入里有“当前已有话题”，新话题必须避开这些标题和角度；
                6. 每次刷新都要重新组织 5 张更贴合当前历史的新卡片，不要机械重复上一批；
                7. content 要写成子女容易自然开口的聊天引子，控制在 45 到 90 个汉字；
                8. 输出必须是合法 json，不要 markdown，不要解释。

                json 格式示例：
                {
                  "topics": [
                    {
                      "category": "健康关心",
                      "title": "问问腰最近怎么样",
                      "content": "爸爸这周两次提到腰酸，可以从天气变冷聊起，问问晚上睡觉会不会不舒服。",
                      "sourceText": "这周两次提到腰酸"
                    }
                  ]
                }

                信号：
                """ + String.join("\n", safeSignals(signals));
        String content = callDeepSeek(prompt, true, 1400);
        return new TopicGenerationResult(parseTopicSuggestions(content), false);
    }

    private List<TopicSuggestion> parseTopicSuggestions(String content) {
        try {
            Map<String, Object> payload = objectMapper.readValue(content, new TypeReference<>() {
            });
            Object rawTopics = payload.get("topics");
            if (!(rawTopics instanceof List<?> rawList) || rawList.isEmpty()) {
                throw new BizException("DeepSeek 返回的话题卡为空");
            }
            return rawList.stream()
                    .map(item -> objectMapper.convertValue(item, TopicSuggestion.class))
                    .limit(5)
                    .toList();
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException("DeepSeek 返回 JSON 解析失败：" + exception.getMessage());
        }
    }

    private WeeklyReportSuggestion parseWeeklyReport(String content) {
        try {
            return objectMapper.readValue(content, WeeklyReportSuggestion.class);
        } catch (Exception exception) {
            throw new BizException("DeepSeek 返回周报 JSON 解析失败：" + exception.getMessage());
        }
    }

    private DailyStatusPayload parseDailyStatuses(String content) {
        try {
            DailyStatusPayload payload = objectMapper.readValue(content, DailyStatusPayload.class);
            if (payload.statuses() == null || payload.statuses().isEmpty()) {
                throw new BizException("DeepSeek 返回的每日近况为空");
            }
            return payload;
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException("DeepSeek 返回每日近况 JSON 解析失败：" + exception.getMessage());
        }
    }

    private MemorySuggestion parseMemorySuggestion(String content) {
        try {
            return objectMapper.readValue(content, MemorySuggestion.class);
        } catch (Exception exception) {
            throw new BizException("DeepSeek 返回记忆 JSON 解析失败：" + exception.getMessage());
        }
    }

    private AiMemoryResult localMemory(String elderDisplayName, String responseContent, String topicTitle) {
        String content = cleanText(responseContent, "", 500);
        if (isSmallTalk(content)) {
            return new AiMemoryResult(false, "", "", "LOCAL", "寒暄短句不记录");
        }
        return new AiMemoryResult(true, localMemoryTitle(content, topicTitle), localMemoryContent(elderDisplayName, content), "LOCAL", "本地规则生成");
    }

    private String localWeekly(List<String> signals) {
        List<String> safeSignals = safeSignals(signals);
        String basis = safeSignals.isEmpty() ? "这周家里的节奏整体平稳。" : String.join("；", safeSignals);
        return "这是一段本地兜底周报。根据已有信号，" + basis + " 可以从最近的散步、吃药和家里小事聊起，语气轻一点，让爸妈感觉你只是惦记他们。";
    }

    private AiWeeklyReportResult localWeeklyReportCard(String elderDisplayName, List<String> signals) {
        String name = cleanText(elderDisplayName, "家人", 20);
        return new AiWeeklyReportResult(
                name + "这周的近况",
                localWeekly(signals),
                "日常,近况,陪伴",
                firstSignalValue(safeSignals(signals), "最近留言").isBlank() ? "暂无可引用的话" : firstSignalValue(safeSignals(signals), "最近留言"),
                true
        );
    }

    private AiDailyStatusResult localDailyStatuses(String elderDisplayName, LocalDate startDate, LocalDate endDate, List<String> signals) {
        List<String> safeSignals = safeSignals(signals);
        List<AiDailyStatus> statuses = new ArrayList<>();
        LocalDate cursor = endDate;
        while (!cursor.isBefore(startDate) && statuses.size() < 8) {
            LocalDate statusDate = cursor;
            String content = safeSignals.stream()
                    .filter(item -> item.contains(statusDate.toString()))
                    .findFirst()
                    .map(item -> {
                        int index = item.indexOf('：');
                        return index >= 0 && index + 1 < item.length() ? item.substring(index + 1).trim() : item;
                    })
                    .orElse(cleanText(elderDisplayName, "家人", 20) + "这天暂无新的长留言，已有记录显示状态整体平稳。");
            statuses.add(new AiDailyStatus(statusDate.toString(), statusDate.equals(LocalDate.now()) ? "今天" : statusDate.getMonthValue() + "/" + statusDate.getDayOfMonth(), cleanText(content, content, 180), "normal"));
            cursor = cursor.minusDays(1);
        }
        return new AiDailyStatusResult(statuses, true);
    }

    private String localTopics() {
        return "1. 健康关心：问问最近腰酸有没有好一点。\n2. 日常唠嗑：接着聊公园那盘棋。\n3. 回忆唤起：问问老家门口槐树的事。\n4. 承诺回顾：问问微信支付还想不想学。\n5. 关系人询问：问问李叔叔最近怎么样。";
    }

    private String localElderPrompt(String childName, TopicCardEntity topic) {
        String name = cleanText(childName, "孩子", 20);
        String title = cleanText(topic == null ? "" : topic.getTitle(), "", 80);
        String content = cleanText(topic == null ? "" : topic.getContent(), "", 140);
        String source = (title + "，" + content).trim();
        if (source.contains("晚上好")) {
            return name + "想接着和你聊聊：晚上在忙什么呀？";
        }
        if (source.contains("睡") || source.contains("精神")) {
            return name + "想问问你：最近睡得怎么样，精神还好吗？";
        }
        if (source.contains("药")) {
            return name + "想问问你：今天吃药和身体感觉怎么样？";
        }
        if (source.contains("晚饭") || source.contains("吃饭") || source.contains("买菜")) {
            return name + "想听你说说：今天吃了什么，过得怎么样？";
        }
        if (!title.isBlank()) {
            return name + "想听你说说：" + normalizeElderPronouns(cleanText(title, "今天的事", 28)) + "。";
        }
        return name + "想听你说说今天过得怎么样。";
    }

    private String normalizeElderPronouns(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        return text
                .replace("她看了吗", "你看了吗")
                .replace("他看了吗", "你看了吗")
                .replace("她看了没", "你看了没")
                .replace("他看了没", "你看了没")
                .replace("她想不想", "你想不想")
                .replace("他想不想", "你想不想")
                .replace("问问她", "问问你")
                .replace("问问他", "问问你")
                .replace("妈妈最近", "你最近")
                .replace("爸爸最近", "你最近")
                .replace("妈妈这几天", "你这几天")
                .replace("爸爸这几天", "你这几天");
    }

    private List<TopicSuggestion> localTopicCards(List<String> signals) {
        List<String> safeSignals = safeSignals(signals);
        String source = safeSignals.isEmpty() ? "最近历史数据" : safeSignals.get(0);
        String recentReply = firstSignalValue(safeSignals, "最近留言");
        String dailyStatus = firstDailySignalValue(safeSignals);
        String medication = firstSignalValue(safeSignals, "用药提醒");
        int offset = Math.floorMod(LocalDateTime.now().getSecond(), 5);

        List<TopicSuggestion> base = new ArrayList<>(List.of(
                new TopicSuggestion("日常唠嗑", replyTitle(recentReply), replyContent(recentReply), source),
                new TopicSuggestion("近况追问", "顺着最近的状态问一句", dailyStatus.isBlank()
                        ? "从最近的日常记录自然开口，问问今天有没有什么顺心的小事。"
                        : "可以接着这条近况聊：" + dailyStatus + "。先顺着说两句，再轻轻问问细节。", source),
                new TopicSuggestion("健康关心", "问问身体和精神头", medication.isBlank()
                        ? "语气放轻一点，问问最近睡眠、胃口和精神头，不做判断，只是表达惦记。"
                        : "结合用药提醒聊得自然一点：" + medication + "。可以问问今天吃药和身体感受。", source),
                new TopicSuggestion("关系陪伴", "找个轻松话头陪她聊", "不要急着问大事，可以从晚饭、天气、邻里和家里小事切入，让对话更容易继续。", source),
                new TopicSuggestion("回忆唤起", "聊一件以前常说的小事", "如果她愿意多说，可以顺着以前家里的习惯、老朋友或常去的地方聊，不追问，让她慢慢讲。", source)
        ));
        if (offset > 0) {
            List<TopicSuggestion> rotated = new ArrayList<>(base.subList(offset, base.size()));
            rotated.addAll(base.subList(0, offset));
            return rotated;
        }
        return base;
    }

    private String firstSignalValue(List<String> signals, String marker) {
        return signals.stream()
                .filter(item -> item.contains(marker))
                .findFirst()
                .map(item -> {
                    int index = item.indexOf('：');
                    return index >= 0 && index + 1 < item.length() ? item.substring(index + 1).trim() : item;
                })
                .orElse("");
    }

    private String firstDailySignalValue(List<String> signals) {
        return signals.stream()
                .filter(item -> item.contains("："))
                .filter(item -> !item.startsWith("当前对象"))
                .filter(item -> !item.startsWith("最近留言"))
                .filter(item -> !item.startsWith("用药提醒"))
                .filter(item -> !item.startsWith("当前已有话题"))
                .filter(item -> !item.startsWith("生成要求"))
                .findFirst()
                .map(item -> {
                    int index = item.indexOf('：');
                    return index >= 0 && index + 1 < item.length() ? item.substring(index + 1).trim() : item;
                })
                .orElse("");
    }

    private String replyTitle(String recentReply) {
        if (recentReply == null || recentReply.isBlank()) {
            return "问问今天过得怎么样";
        }
        if (recentReply.length() <= 12) {
            return "接着“" + recentReply + "”聊两句";
        }
        return "接着刚才那句话聊";
    }

    private String replyContent(String recentReply) {
        if (recentReply == null || recentReply.isBlank()) {
            return "没有新的长留言时，可以从今天吃了什么、见了谁、心情怎么样自然开口。";
        }
        return "她刚刚说过“" + cleanText(recentReply, "最近这句话", 40) + "”，可以先回应这句话，再问问今天晚上在忙什么。";
    }

    private boolean isSmallTalk(String value) {
        String text = value == null ? "" : value.trim()
                .replaceAll("[\\s，。,.！!？?~～、]", "");
        if (text.length() <= 1) {
            return true;
        }
        List<String> smallTalk = List.of(
                "晚上好", "早上好", "上午好", "中午好", "下午好", "晚安", "你好",
                "好", "好的", "好呀", "好啊", "嗯", "嗯嗯", "哦", "哦哦", "行", "可以",
                "收到", "知道了", "辛苦了", "谢谢", "不用", "没事", "还好"
        );
        if (smallTalk.contains(text)) {
            return true;
        }
        return text.length() <= 4 && text.matches(".*(好|嗯|哦|行|谢|安)$");
    }

    private String normalizeTone(String value) {
        if ("good".equals(value) || "watch".equals(value)) {
            return value;
        }
        return "normal";
    }

    private String localMemoryTitle(String content, String topicTitle) {
        if (topicTitle != null && !topicTitle.isBlank()) {
            return cleanText(topicTitle, "说起过的事", 20);
        }
        if (content.matches(".*(医院|医生|药|睡|疼|腰|腿|胃|血压|身体|不舒服).*")) {
            return "健康近况";
        }
        if (content.matches(".*(买菜|散步|出门|公园|吃饭|晚饭|早饭|午饭|天气|做饭).*")) {
            return "日常小事";
        }
        if (content.matches(".*(朋友|邻居|叔|姨|舅|姑|同事|老伴).*")) {
            return "提到的人";
        }
        if (content.matches(".*(以前|小时候|老家|记得|那年|过去).*")) {
            return "旧事回忆";
        }
        return "说起过的事";
    }

    private String localMemoryContent(String elderDisplayName, String content) {
        String name = cleanText(elderDisplayName, "家人", 20);
        String text = cleanText(content, "", 100);
        if (text.startsWith(name)) {
            return text;
        }
        return name + "说：" + text;
    }

    private List<String> safeSignals(List<String> signals) {
        if (signals == null) {
            return List.of();
        }
        return signals.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .limit(40)
                .toList();
    }

    private String cleanText(String value, String fallback, int maxLength) {
        String text = value == null || value.isBlank() ? fallback : value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    public record AiTextResult(String text, boolean mocked) {
    }

    public record TopicSuggestion(String category, String title, String content, String sourceText) {
    }

    public record TopicRefreshResult(List<TopicCardEntity> topics, boolean mocked) {
    }

    public record AiMemoryResult(boolean keep, String title, String content, String sourceType, String reason) {
    }

    public record AiWeeklyReportResult(String title, String summary, String keywords, String quoteText, boolean mocked) {
    }

    public record AiDailyStatusResult(List<AiDailyStatus> statuses, boolean mocked) {
    }

    public record AiDailyStatus(String statusDate, String title, String content, String tone) {
    }

    private record TopicGenerationResult(List<TopicSuggestion> topics, boolean mocked) {
    }

    private record MemorySuggestion(boolean keep, String title, String content, String reason) {
    }

    private record WeeklyReportSuggestion(String title, String summary, String keywords, String quoteText) {
    }

    private record DailyStatusPayload(List<DailyStatusSuggestion> statuses) {
    }

    private record DailyStatusSuggestion(String statusDate, String title, String content, String tone) {
    }
}
