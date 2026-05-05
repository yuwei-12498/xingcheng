# Result Chat Itinerary Generate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users on the result page discuss preferences with the AI assistant, receive a structured route-generation summary, and generate a new route only after clicking a confirmation button.

**Architecture:** Extend the existing chat skill workflow. The frontend sends recent chat turns plus the current itinerary's `originalReq`; a new backend skill turns that conversation into a `GenerateReqDTO` draft; the existing frontend workflow action dispatcher calls the existing itinerary generation API and saves the returned snapshot so the result page refreshes through the existing `citytrip:itinerary-updated` event.

**Tech Stack:** Spring Boot, Java DTO/services, Vue 3, Vite/Vitest, existing `/api/chat/messages/stream`, existing `/api/itineraries`, existing chat `skillPayload.actions`.

---

## File Structure

- Modify `F:\dachuang\backend\src\main\java\com\citytrip\model\dto\ChatReqDTO.java`
  - Add top-level `recentMessages`.
  - Add `ChatContext.originalReq`.
- Modify `F:\dachuang\backend\src\main\java\com\citytrip\model\vo\ChatSkillPayloadVO.java`
  - Add `GenerateReqDTO generateDraft`.
  - Add `List<String> generateSummary`.
- Create `F:\dachuang\backend\src\main\java\com\citytrip\service\application\itinerary\ChatItineraryGenerateDraftService.java`
  - Builds extraction text from recent messages and current request.
  - Calls `SmartFillUseCase`.
  - Merges extracted fields onto the current `originalReq`.
  - Returns a workflow payload with `生成路线` / `继续补充`.
- Create `F:\dachuang\backend\src\main\java\com\citytrip\service\skill\ItineraryGenerateFromChatSkill.java`
  - Detects result-page generation intent.
- Modify `F:\dachuang\frontend\src\utils\chatContext.js`
  - Add sanitized `originalReq` to chat context.
- Modify `F:\dachuang\frontend\src\store\chat.js`
  - Send last 12 chat turns in `recentMessages`.
- Modify `F:\dachuang\frontend\src\utils\chatWorkflowActions.js`
  - Handle `confirm_itinerary_generate` and `continue_itinerary_generate`.
- Tests:
  - `F:\dachuang\backend\src\test\java\com\citytrip\service\application\itinerary\ChatItineraryGenerateDraftServiceTest.java`
  - `F:\dachuang\backend\src\test\java\com\citytrip\service\skill\ItineraryGenerateFromChatSkillTest.java`
  - `F:\dachuang\frontend\src\utils\__tests__\chatContext.test.js`
  - `F:\dachuang\frontend\src\store\__tests__\chat.test.js`
  - `F:\dachuang\frontend\src\utils\__tests__\chatWorkflowActions.test.js`

---

### Task 1: Add chat request context needed for conversation summarization

**Files:**
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\model\dto\ChatReqDTO.java`
- Modify: `F:\dachuang\frontend\src\utils\chatContext.js`
- Modify: `F:\dachuang\frontend\src\store\chat.js`
- Test: `F:\dachuang\frontend\src\utils\__tests__\chatContext.test.js`
- Test: `F:\dachuang\frontend\src\store\__tests__\chat.test.js`

- [ ] **Step 1: Write failing test for result-page `originalReq` context**

Append to `F:\dachuang\frontend\src\utils\__tests__\chatContext.test.js`:

```js
it('includes originalReq for result-page chat workflows', () => {
  window.localStorage.setItem('citytrip/current-itinerary-v2', JSON.stringify({
    id: 77,
    originalReq: {
      cityCode: 'CD',
      cityName: '成都',
      tripDays: 1,
      tripDate: '2026-05-02',
      startTime: '09:00',
      endTime: '18:00',
      budgetLevel: '中',
      themes: ['文化'],
      isRainy: false,
      isNight: true,
      walkingLevel: '中',
      companionType: '朋友',
      mustVisitPoiNames: ['杜甫草堂'],
      departurePlaceName: '春熙路',
      departureLatitude: 30.657,
      departureLongitude: 104.08
    },
    nodes: [{ poiName: '杜甫草堂' }]
  }))

  const context = buildSharedChatContext({ pageType: 'result' })

  expect(context.originalReq).toMatchObject({
    cityCode: 'CD',
    cityName: '成都',
    tripDate: '2026-05-02',
    startTime: '09:00',
    endTime: '18:00',
    walkingLevel: '中'
  })
  expect(context.originalReq.themes).toEqual(['文化'])
  expect(context.originalReq.mustVisitPoiNames).toEqual(['杜甫草堂'])
})
```

- [ ] **Step 2: Write failing test for `recentMessages`**

Append to `F:\dachuang\frontend\src\store\__tests__\chat.test.js`:

```js
it('sends recent chat turns with the current question', async () => {
  reqStreamChat.mockResolvedValue({
    answer: '可以，我先记下你的偏好。',
    relatedTips: [],
    evidence: [],
    skillPayload: null
  })

  await askChatQuestion('我喜欢美食和轻松一点', { pageType: 'result', cityName: '成都' })
  await askChatQuestion('就按刚才说的生成路线', { pageType: 'result', cityName: '成都' })

  const secondRequest = reqStreamChat.mock.calls[1][0]
  const recentText = secondRequest.recentMessages.map(item => item.content).join('\n')
  expect(recentText).toContain('我喜欢美食和轻松一点')
  expect(recentText).toContain('就按刚才说的生成路线')
})
```

- [ ] **Step 3: Run failing frontend tests**

Run:

```powershell
cd F:\dachuang\frontend
npm run test:unit -- --run src/utils/__tests__/chatContext.test.js src/store/__tests__/chat.test.js
```

Expected: FAIL because `originalReq` and `recentMessages` are absent.

- [ ] **Step 4: Update backend DTO**

In `F:\dachuang\backend\src\main\java\com\citytrip\model\dto\ChatReqDTO.java`, add near existing top-level fields:

```java
    @Valid
    @Size(max = 20, message = "recentMessages must contain at most 20 items")
    private List<ChatMessage> recentMessages;
```

Inside `ChatContext`, add:

```java
        @Valid
        private GenerateReqDTO originalReq;
```

Before `ChatAction`, add:

```java
    @Data
    public static class ChatMessage {
        @Size(max = 16, message = "message role must be at most 16 characters")
        private String role;
        @Size(max = 800, message = "message content must be at most 800 characters")
        private String content;
    }
```

- [ ] **Step 5: Update `chatContext.js`**

Add this helper near `toStringArray`:

```js
const normalizeGenerateRequest = (form) => {
  const source = form && typeof form === 'object' ? form : {}
  return {
    cityCode: safeString(source.cityCode),
    cityName: safeString(source.cityName),
    tripDays: numberOrNull(source.tripDays),
    tripDate: safeString(source.tripDate),
    startTime: safeString(source.startTime),
    endTime: safeString(source.endTime),
    budgetLevel: safeString(source.budgetLevel),
    themes: toStringArray(source.themes),
    isRainy: source.isRainy === undefined ? null : boolOrDefault(source.isRainy, false),
    isNight: source.isNight === undefined ? null : boolOrDefault(source.isNight, false),
    walkingLevel: safeString(source.walkingLevel),
    companionType: safeString(source.companionType),
    mustVisitPoiNames: toStringArray(source.mustVisitPoiNames),
    departurePlaceName: safeString(source.departurePlaceName),
    departureLatitude: numberOrNull(source.departureLatitude),
    departureLongitude: numberOrNull(source.departureLongitude)
  }
}
```

Add this property in `buildSharedChatContext` return object:

```js
    originalReq: normalizeGenerateRequest(baseForm),
```

- [ ] **Step 6: Update `chat.js`**

Add constants near `storagePrefix`:

```js
const maxRecentMessageCount = 12
const maxRecentMessageChars = 500
```

Add helper after `normalizeMessage`:

```js
function buildRecentMessages() {
  return chatState.messages
    .filter(message => message && (message.role === 'user' || message.role === 'assistant'))
    .map(message => ({
      role: message.role,
      content: String(message.content || '').trim().slice(0, maxRecentMessageChars)
    }))
    .filter(message => message.content)
    .slice(-maxRecentMessageCount)
}
```

In `askChatQuestion`, include it in the `reqStreamChat` payload:

```js
        question: value,
        context,
        recentMessages: buildRecentMessages(),
        ...(normalizedAction ? { action: normalizedAction } : {})
```

- [ ] **Step 7: Run Task 1 tests**

Run:

```powershell
cd F:\dachuang\frontend
npm run test:unit -- --run src/utils/__tests__/chatContext.test.js src/store/__tests__/chat.test.js
```

Expected: PASS.

- [ ] **Step 8: Commit Task 1**

```powershell
git add backend/src/main/java/com/citytrip/model/dto/ChatReqDTO.java frontend/src/utils/chatContext.js frontend/src/store/chat.js frontend/src/utils/__tests__/chatContext.test.js frontend/src/store/__tests__/chat.test.js
git commit -m "feat: send result chat route context"
```

---

### Task 2: Build backend draft payload from the result-page conversation

**Files:**
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\model\vo\ChatSkillPayloadVO.java`
- Create: `F:\dachuang\backend\src\main\java\com\citytrip\service\application\itinerary\ChatItineraryGenerateDraftService.java`
- Test: `F:\dachuang\backend\src\test\java\com\citytrip\service\application\itinerary\ChatItineraryGenerateDraftServiceTest.java`

- [ ] **Step 1: Write failing backend draft service test**

Create `F:\dachuang\backend\src\test\java\com\citytrip\service\application\itinerary\ChatItineraryGenerateDraftServiceTest.java`:

```java
package com.citytrip.service.application.itinerary;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.vo.ChatSkillPayloadVO;
import com.citytrip.model.vo.SmartFillVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatItineraryGenerateDraftServiceTest {
    @Test
    void buildsGenerateWorkflowPayloadFromConversation() {
        SmartFillUseCase smartFillUseCase = mock(SmartFillUseCase.class);
        SmartFillVO parsed = new SmartFillVO();
        parsed.setThemes(List.of("美食", "休闲"));
        parsed.setWalkingLevel("低");
        parsed.setMustVisitPoiNames(List.of("宽窄巷子"));
        parsed.setSummary(List.of("少走路", "美食"));
        when(smartFillUseCase.parse(argThat(req -> req != null && req.getText().contains("少走路"))))
                .thenReturn(parsed);

        ChatItineraryGenerateDraftService service = new ChatItineraryGenerateDraftService(smartFillUseCase);
        ChatSkillPayloadVO payload = service.buildDraft(buildRequest());

        assertThat(payload.getSkillName()).isEqualTo("itinerary_generate");
        assertThat(payload.getMessageType()).isEqualTo("workflow");
        assertThat(payload.getWorkflowType()).isEqualTo("itinerary_generate");
        assertThat(payload.getWorkflowState()).isEqualTo("proposal_ready");
        assertThat(payload.getGenerateDraft().getCityName()).isEqualTo("成都");
        assertThat(payload.getGenerateDraft().getTripDate()).isEqualTo("2026-05-02");
        assertThat(payload.getGenerateDraft().getThemes()).containsExactly("美食", "休闲");
        assertThat(payload.getGenerateDraft().getWalkingLevel()).isEqualTo("低");
        assertThat(payload.getGenerateDraft().getMustVisitPoiNames()).containsExactly("宽窄巷子");
        assertThat(payload.getActions()).extracting(ChatSkillPayloadVO.ActionItem::getKey)
                .containsExactly("confirm_itinerary_generate", "continue_itinerary_generate");
        assertThat(payload.getFallbackMessage()).contains("我把这次对话整理成");
    }

    @Test
    void asksForMoreInformationWhenBaseRequestIsMissing() {
        ChatItineraryGenerateDraftService service = new ChatItineraryGenerateDraftService(mock(SmartFillUseCase.class));
        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion("按刚才说的生成路线");
        req.setContext(new ChatReqDTO.ChatContext());

        ChatSkillPayloadVO payload = service.buildDraft(req);

        assertThat(payload.getStatus()).isEqualTo("clarification_required");
        assertThat(payload.getGenerateDraft()).isNull();
        assertThat(payload.getFallbackMessage()).contains("当前路线缺少基础参数");
    }

    private ChatReqDTO buildRequest() {
        GenerateReqDTO originalReq = new GenerateReqDTO();
        originalReq.setCityCode("CD");
        originalReq.setCityName("成都");
        originalReq.setTripDays(1.0D);
        originalReq.setTripDate("2026-05-02");
        originalReq.setStartTime("09:00");
        originalReq.setEndTime("18:00");
        originalReq.setBudgetLevel("中");
        originalReq.setThemes(List.of("文化"));
        originalReq.setWalkingLevel("中");
        originalReq.setCompanionType("朋友");
        originalReq.setDeparturePlaceName("春熙路");

        ChatReqDTO.ChatContext context = new ChatReqDTO.ChatContext();
        context.setPageType("result");
        context.setCityCode("CD");
        context.setCityName("成都");
        context.setOriginalReq(originalReq);

        ChatReqDTO.ChatMessage turn = new ChatReqDTO.ChatMessage();
        turn.setRole("user");
        turn.setContent("我喜欢美食，想轻松一点，少走路，宽窄巷子保留");

        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion("就按刚才说的生成路线");
        req.setContext(context);
        req.setRecentMessages(List.of(turn));
        return req;
    }
}
```

- [ ] **Step 2: Run failing backend test**

```powershell
cd F:\dachuang\backend
mvn test -Dtest=ChatItineraryGenerateDraftServiceTest
```

Expected: FAIL because the service and payload fields are not present.

- [ ] **Step 3: Add generate fields to `ChatSkillPayloadVO`**

Add import:

```java
import com.citytrip.model.dto.GenerateReqDTO;
```

Add fields:

```java
    private GenerateReqDTO generateDraft;
    private List<String> generateSummary = new ArrayList<>();
```

Add setter:

```java
    public void setGenerateSummary(List<String> generateSummary) {
        this.generateSummary = generateSummary == null ? new ArrayList<>() : new ArrayList<>(generateSummary);
    }
```

- [ ] **Step 4: Create draft service**

Create `F:\dachuang\backend\src\main\java\com\citytrip\service\application\itinerary\ChatItineraryGenerateDraftService.java` with this structure:

```java
package com.citytrip.service.application.itinerary;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.dto.SmartFillReqDTO;
import com.citytrip.model.vo.ChatSkillPayloadVO;
import com.citytrip.model.vo.SmartFillVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class ChatItineraryGenerateDraftService {
    private static final int MAX_EXTRACTION_TEXT_CHARS = 1600;
    private final SmartFillUseCase smartFillUseCase;

    public ChatItineraryGenerateDraftService(SmartFillUseCase smartFillUseCase) {
        this.smartFillUseCase = smartFillUseCase;
    }

    public ChatSkillPayloadVO buildDraft(ChatReqDTO req) {
        GenerateReqDTO base = req == null || req.getContext() == null ? null : req.getContext().getOriginalReq();
        if (!hasUsableBaseRequest(base)) {
            return clarificationPayload("当前路线缺少基础参数，请先生成一条路线，或补充城市、日期和出行时间后再让我整理。");
        }
        SmartFillVO parsed = parseConversation(req, base);
        GenerateReqDTO draft = merge(base, parsed);
        List<String> summary = buildSummary(draft, parsed);

        ChatSkillPayloadVO payload = new ChatSkillPayloadVO();
        payload.setSkillName("itinerary_generate");
        payload.setStatus("ok");
        payload.setIntent("generate_itinerary_from_chat");
        payload.setMessageType("workflow");
        payload.setWorkflowType("itinerary_generate");
        payload.setWorkflowState("proposal_ready");
        payload.setSource("chat-summary");
        payload.setGenerateDraft(draft);
        payload.setGenerateSummary(summary);
        payload.setFallbackMessage("我把这次对话整理成：" + String.join("、", summary) + "。确认后我会直接在结果页生成新路线。");
        payload.setActions(List.of(action("confirm_itinerary_generate", "生成路线", "primary"), action("continue_itinerary_generate", "继续补充", "secondary")));
        payload.setEvidence(List.of("skill:itinerary_generate", "source=chat-summary"));
        return payload;
    }

    private SmartFillVO parseConversation(ChatReqDTO req, GenerateReqDTO base) {
        SmartFillReqDTO smartFillReq = new SmartFillReqDTO();
        smartFillReq.setText(buildExtractionText(req, base));
        SmartFillVO parsed = smartFillUseCase.parse(smartFillReq);
        return parsed == null ? new SmartFillVO() : parsed;
    }

    private String buildExtractionText(ChatReqDTO req, GenerateReqDTO base) {
        StringBuilder builder = new StringBuilder();
        builder.append("当前路线基础参数：")
                .append(safe(base.getCityName())).append(" ")
                .append(safe(base.getTripDate())).append(" ")
                .append(safe(base.getStartTime())).append("-").append(safe(base.getEndTime())).append(" ")
                .append("预算").append(safe(base.getBudgetLevel())).append(" ")
                .append("主题").append(base.getThemes() == null ? List.of() : base.getThemes()).append("。\n");
        if (req != null && req.getRecentMessages() != null) {
            for (ChatReqDTO.ChatMessage message : req.getRecentMessages()) {
                if (message != null && StringUtils.hasText(message.getContent())) {
                    builder.append("用户：").append(message.getContent().trim()).append("\n");
                }
            }
        }
        if (req != null && StringUtils.hasText(req.getQuestion())) builder.append("当前用户请求：").append(req.getQuestion().trim());
        String text = builder.toString().trim();
        return text.length() <= MAX_EXTRACTION_TEXT_CHARS ? text : text.substring(text.length() - MAX_EXTRACTION_TEXT_CHARS);
    }

    private GenerateReqDTO merge(GenerateReqDTO base, SmartFillVO parsed) {
        GenerateReqDTO draft = copy(base);
        if (parsed == null) return draft;
        if (parsed.getTripDays() != null) draft.setTripDays(parsed.getTripDays());
        if (StringUtils.hasText(parsed.getTripDate())) draft.setTripDate(parsed.getTripDate().trim());
        if (StringUtils.hasText(parsed.getStartTime())) draft.setStartTime(parsed.getStartTime().trim());
        if (StringUtils.hasText(parsed.getEndTime())) draft.setEndTime(parsed.getEndTime().trim());
        if (StringUtils.hasText(parsed.getBudgetLevel())) draft.setBudgetLevel(parsed.getBudgetLevel().trim());
        if (parsed.getThemes() != null && !parsed.getThemes().isEmpty()) draft.setThemes(dedup(parsed.getThemes()));
        if (parsed.getIsRainy() != null) draft.setIsRainy(parsed.getIsRainy());
        if (parsed.getIsNight() != null) draft.setIsNight(parsed.getIsNight());
        if (StringUtils.hasText(parsed.getWalkingLevel())) draft.setWalkingLevel(parsed.getWalkingLevel().trim());
        if (StringUtils.hasText(parsed.getCompanionType())) draft.setCompanionType(parsed.getCompanionType().trim());
        if (parsed.getMustVisitPoiNames() != null && !parsed.getMustVisitPoiNames().isEmpty()) draft.setMustVisitPoiNames(dedup(parsed.getMustVisitPoiNames()));
        if (StringUtils.hasText(parsed.getDepartureText())) draft.setDeparturePlaceName(parsed.getDepartureText().trim());
        return draft;
    }

    private GenerateReqDTO copy(GenerateReqDTO source) {
        GenerateReqDTO draft = new GenerateReqDTO();
        draft.setCityCode(source.getCityCode());
        draft.setCityName(source.getCityName());
        draft.setTripDays(source.getTripDays());
        draft.setTripDate(source.getTripDate());
        draft.setTotalBudget(source.getTotalBudget());
        draft.setBudgetLevel(source.getBudgetLevel());
        draft.setThemes(source.getThemes() == null ? List.of() : new ArrayList<>(source.getThemes()));
        draft.setIsRainy(source.getIsRainy());
        draft.setIsNight(source.getIsNight());
        draft.setWalkingLevel(source.getWalkingLevel());
        draft.setCompanionType(source.getCompanionType());
        draft.setStartTime(source.getStartTime());
        draft.setEndTime(source.getEndTime());
        draft.setMustVisitPoiNames(source.getMustVisitPoiNames() == null ? List.of() : new ArrayList<>(source.getMustVisitPoiNames()));
        draft.setDeparturePlaceName(source.getDeparturePlaceName());
        draft.setDepartureLatitude(source.getDepartureLatitude());
        draft.setDepartureLongitude(source.getDepartureLongitude());
        return draft;
    }

    private List<String> buildSummary(GenerateReqDTO draft, SmartFillVO parsed) {
        List<String> summary = new ArrayList<>();
        add(summary, draft.getCityName());
        add(summary, safe(draft.getTripDate()) + " " + safe(draft.getStartTime()) + "-" + safe(draft.getEndTime()));
        if (draft.getThemes() != null && !draft.getThemes().isEmpty()) add(summary, "主题：" + String.join("/", draft.getThemes()));
        add(summary, StringUtils.hasText(draft.getBudgetLevel()) ? "预算：" + draft.getBudgetLevel() : null);
        add(summary, StringUtils.hasText(draft.getWalkingLevel()) ? "步行：" + draft.getWalkingLevel() : null);
        if (draft.getMustVisitPoiNames() != null && !draft.getMustVisitPoiNames().isEmpty()) add(summary, "必去：" + String.join("/", draft.getMustVisitPoiNames()));
        if (parsed != null && parsed.getSummary() != null) parsed.getSummary().forEach(item -> add(summary, item));
        return dedup(summary).stream().limit(8).toList();
    }

    private boolean hasUsableBaseRequest(GenerateReqDTO base) {
        return base != null && StringUtils.hasText(base.getCityName()) && StringUtils.hasText(base.getTripDate()) && StringUtils.hasText(base.getStartTime()) && StringUtils.hasText(base.getEndTime());
    }

    private ChatSkillPayloadVO clarificationPayload(String message) {
        ChatSkillPayloadVO payload = new ChatSkillPayloadVO();
        payload.setSkillName("itinerary_generate");
        payload.setStatus("clarification_required");
        payload.setIntent("generate_itinerary_from_chat");
        payload.setMessageType("workflow");
        payload.setWorkflowType("itinerary_generate");
        payload.setWorkflowState("clarification_required");
        payload.setSource("chat-summary");
        payload.setFallbackMessage(message);
        payload.setEvidence(List.of("skill:itinerary_generate"));
        return payload;
    }

    private ChatSkillPayloadVO.ActionItem action(String key, String label, String style) {
        ChatSkillPayloadVO.ActionItem item = new ChatSkillPayloadVO.ActionItem();
        item.setKey(key);
        item.setLabel(label);
        item.setStyle(style);
        return item;
    }

    private List<String> dedup(List<String> source) {
        if (source == null || source.isEmpty()) return List.of();
        Set<String> values = new LinkedHashSet<>();
        for (String item : source) if (StringUtils.hasText(item)) values.add(item.trim());
        return new ArrayList<>(values);
    }

    private void add(List<String> target, String value) {
        if (StringUtils.hasText(value)) target.add(value.trim());
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
```

- [ ] **Step 5: Run Task 2 backend tests**

```powershell
cd F:\dachuang\backend
mvn test -Dtest=ChatItineraryGenerateDraftServiceTest
```

Expected: PASS.

- [ ] **Step 6: Commit Task 2**

```powershell
git add backend/src/main/java/com/citytrip/model/vo/ChatSkillPayloadVO.java backend/src/main/java/com/citytrip/service/application/itinerary/ChatItineraryGenerateDraftService.java backend/src/test/java/com/citytrip/service/application/itinerary/ChatItineraryGenerateDraftServiceTest.java
git commit -m "feat: summarize result chat into itinerary draft"
```

---

### Task 3: Register a result-page generate skill

**Files:**
- Create: `F:\dachuang\backend\src\main\java\com\citytrip\service\skill\ItineraryGenerateFromChatSkill.java`
- Test: `F:\dachuang\backend\src\test\java\com\citytrip\service\skill\ItineraryGenerateFromChatSkillTest.java`

- [ ] **Step 1: Write failing skill test**

Create `F:\dachuang\backend\src\test\java\com\citytrip\service\skill\ItineraryGenerateFromChatSkillTest.java`:

```java
package com.citytrip.service.skill;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.vo.ChatSkillPayloadVO;
import com.citytrip.service.application.itinerary.ChatItineraryGenerateDraftService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItineraryGenerateFromChatSkillTest {
    @Test
    void supportsResultPageGenerateIntent() {
        ItineraryGenerateFromChatSkill skill = new ItineraryGenerateFromChatSkill(mock(ChatItineraryGenerateDraftService.class));
        assertThat(skill.supports(request("result", "就按刚才聊的偏好生成路线"))).isTrue();
    }

    @Test
    void rejectsHomePageGenerateIntent() {
        ItineraryGenerateFromChatSkill skill = new ItineraryGenerateFromChatSkill(mock(ChatItineraryGenerateDraftService.class));
        assertThat(skill.supports(request("home", "就按刚才聊的偏好生成路线"))).isFalse();
    }

    @Test
    void delegatesToDraftService() {
        ChatItineraryGenerateDraftService draftService = mock(ChatItineraryGenerateDraftService.class);
        ChatReqDTO req = request("result", "按这个生成路线");
        ChatSkillPayloadVO payload = new ChatSkillPayloadVO();
        payload.setSkillName("itinerary_generate");
        when(draftService.buildDraft(req)).thenReturn(payload);
        ItineraryGenerateFromChatSkill skill = new ItineraryGenerateFromChatSkill(draftService);

        assertThat(skill.execute(req)).isSameAs(payload);
        verify(draftService).buildDraft(req);
    }

    private ChatReqDTO request(String pageType, String question) {
        ChatReqDTO.ChatContext context = new ChatReqDTO.ChatContext();
        context.setPageType(pageType);
        ChatReqDTO req = new ChatReqDTO();
        req.setContext(context);
        req.setQuestion(question);
        return req;
    }
}
```

- [ ] **Step 2: Run failing skill test**

```powershell
cd F:\dachuang\backend
mvn test -Dtest=ItineraryGenerateFromChatSkillTest
```

Expected: FAIL because the skill does not exist.

- [ ] **Step 3: Create skill handler**

Create `F:\dachuang\backend\src\main\java\com\citytrip\service\skill\ItineraryGenerateFromChatSkill.java`:

```java
package com.citytrip.service.skill;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.vo.ChatSkillPayloadVO;
import com.citytrip.service.application.itinerary.ChatItineraryGenerateDraftService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Order(8)
public class ItineraryGenerateFromChatSkill implements ChatSkillHandler {
    private final ChatItineraryGenerateDraftService draftService;

    public ItineraryGenerateFromChatSkill(ChatItineraryGenerateDraftService draftService) {
        this.draftService = draftService;
    }

    @Override
    public String skillName() {
        return "itinerary_generate";
    }

    @Override
    public boolean supports(ChatReqDTO req) {
        if (req == null || req.getContext() == null || !StringUtils.hasText(req.getQuestion())) return false;
        String pageType = req.getContext().getPageType();
        if (!"result".equalsIgnoreCase(pageType == null ? "" : pageType.trim())) return false;
        String question = req.getQuestion().trim();
        boolean explicitGenerate = containsAny(question, "生成路线", "生成一条", "规划路线", "生成行程", "重新生成", "换一条路线", "来一条路线");
        boolean confirmationGenerate = containsAny(question, "按刚才", "按这个", "就这样", "照这个", "用这个偏好")
                && containsAny(question, "生成", "规划", "安排", "路线", "行程");
        return explicitGenerate || confirmationGenerate;
    }

    @Override
    public ChatSkillPayloadVO execute(ChatReqDTO req) {
        return draftService.buildDraft(req);
    }

    private boolean containsAny(String text, String... words) {
        if (!StringUtils.hasText(text)) return false;
        for (String word : words) if (StringUtils.hasText(word) && text.contains(word)) return true;
        return false;
    }
}
```

- [ ] **Step 4: Run Task 3 backend tests**

```powershell
cd F:\dachuang\backend
mvn test -Dtest=ItineraryGenerateFromChatSkillTest,ChatItineraryGenerateDraftServiceTest
```

Expected: PASS.

- [ ] **Step 5: Commit Task 3**

```powershell
git add backend/src/main/java/com/citytrip/service/skill/ItineraryGenerateFromChatSkill.java backend/src/test/java/com/citytrip/service/skill/ItineraryGenerateFromChatSkillTest.java
git commit -m "feat: route result chat generate intent"
```

---

### Task 4: Generate a new itinerary when user confirms the chat summary

**Files:**
- Modify: `F:\dachuang\frontend\src\utils\chatWorkflowActions.js`
- Test: `F:\dachuang\frontend\src\utils\__tests__\chatWorkflowActions.test.js`

- [ ] **Step 1: Write failing workflow action tests**

In `F:\dachuang\frontend\src\utils\__tests__\chatWorkflowActions.test.js`, add `reqGenerateItinerary` to the itinerary API mock and import. Then append:

```js
it('generates a new itinerary from a confirmed chat summary draft', async () => {
  const draft = {
    cityCode: 'CD',
    cityName: '成都',
    tripDate: '2026-05-02',
    startTime: '09:00',
    endTime: '18:00',
    budgetLevel: '中',
    themes: ['美食'],
    walkingLevel: '低'
  }
  reqGenerateItinerary.mockResolvedValue({ id: 88, originalReq: draft, nodes: [{ poiName: '宽窄巷子' }] })

  const handled = await handleChatWorkflowAction({
    action: { key: 'confirm_itinerary_generate' },
    message: { meta: { skillPayload: { workflowType: 'itinerary_generate', generateDraft: draft } } },
    buildContext: () => ({})
  })

  expect(handled).toBe(true)
  expect(reqGenerateItinerary).toHaveBeenCalledWith(draft)
  expect(saveItinerarySnapshot).toHaveBeenCalledWith(expect.objectContaining({ id: 88 }))
  expect(appendAssistantMessage).toHaveBeenCalledWith(
    '已按这次对话总结生成新路线，并同步刷新结果页。',
    expect.objectContaining({ skillPayload: expect.objectContaining({ workflowType: 'itinerary_generate', workflowState: 'applied' }) })
  )
  expect(ElMessage.success).toHaveBeenCalledWith('已生成新路线')
})

it('keeps chatting when user chooses to continue supplementing preferences', async () => {
  const handled = await handleChatWorkflowAction({
    action: { key: 'continue_itinerary_generate' },
    message: null,
    buildContext: () => ({})
  })

  expect(handled).toBe(true)
  expect(reqGenerateItinerary).not.toHaveBeenCalled()
  expect(appendAssistantMessage).toHaveBeenCalledWith('好的，你可以继续补充偏好；说完后我再帮你整理并生成路线。')
})
```

- [ ] **Step 2: Run failing workflow tests**

```powershell
cd F:\dachuang\frontend
npm run test:unit -- --run src/utils/__tests__/chatWorkflowActions.test.js
```

Expected: FAIL because the new action keys are not handled.

- [ ] **Step 3: Implement workflow action**

In `F:\dachuang\frontend\src\utils\chatWorkflowActions.js`, import `reqGenerateItinerary` from `@/api/itinerary`.

Add before `applyReplacement`:

```js
const GENERATE_WORKFLOW_TYPE = 'itinerary_generate'

const buildGenerateWorkflowMeta = workflowState => ({
  skillPayload: {
    skillName: GENERATE_WORKFLOW_TYPE,
    messageType: 'workflow',
    workflowType: GENERATE_WORKFLOW_TYPE,
    workflowState,
    source: 'local-workflow',
    actions: []
  }
})

const applyItineraryGenerate = async ({ message }) => {
  const draft = message?.meta?.skillPayload?.generateDraft
  if (!draft || typeof draft !== 'object') {
    throw new Error('当前没有可生成路线的需求摘要，请继续补充你的路线偏好。')
  }

  const nextSnapshotRaw = await reqGenerateItinerary(draft)
  const nextSnapshot = normalizeItinerarySnapshot(nextSnapshotRaw)
  saveItinerarySnapshot(nextSnapshot)

  appendAssistantMessage('已按这次对话总结生成新路线，并同步刷新结果页。', buildGenerateWorkflowMeta('applied'))
  ElMessage.success('已生成新路线')
  return true
}

const continueItineraryGenerate = () => {
  appendAssistantMessage('好的，你可以继续补充偏好；说完后我再帮你整理并生成路线。')
  return true
}
```

Add to `handleChatWorkflowAction` before `return false`:

```js
  if (actionKey === 'confirm_itinerary_generate') {
    return applyItineraryGenerate({ message })
  }
  if (actionKey === 'continue_itinerary_generate') {
    return continueItineraryGenerate()
  }
```

- [ ] **Step 4: Run Task 4 frontend tests**

```powershell
cd F:\dachuang\frontend
npm run test:unit -- --run src/utils/__tests__/chatWorkflowActions.test.js
```

Expected: PASS.

- [ ] **Step 5: Commit Task 4**

```powershell
git add frontend/src/utils/chatWorkflowActions.js frontend/src/utils/__tests__/chatWorkflowActions.test.js
git commit -m "feat: generate itinerary from chat confirmation"
```

---

### Task 5: Verify the focused workflow

**Files:**
- No new files.

- [ ] **Step 1: Run focused backend tests**

```powershell
cd F:\dachuang\backend
mvn test -Dtest=ChatItineraryGenerateDraftServiceTest,ItineraryGenerateFromChatSkillTest
```

Expected: PASS.

- [ ] **Step 2: Run focused frontend tests**

```powershell
cd F:\dachuang\frontend
npm run test:unit -- --run src/utils/__tests__/chatContext.test.js src/store/__tests__/chat.test.js src/utils/__tests__/chatWorkflowActions.test.js
```

Expected: PASS.

- [ ] **Step 3: Run chat regression tests**

```powershell
cd F:\dachuang\backend
mvn test -Dtest=RealChatGatewayServiceTest,RoutingChatServiceImplTest,SafePromptBuilderTest
```

Expected: PASS.

- [ ] **Step 4: Run frontend build**

```powershell
cd F:\dachuang\frontend
npm run build
```

Expected: PASS.

- [ ] **Step 5: Manual result-page verification**

1. Generate any route and enter `/result`.
2. Open `行程助手`.
3. Send `我想要更轻松一点，多安排美食，宽窄巷子保留。`
4. Send `就按刚才说的生成路线。`
5. Expected: assistant says `我把这次对话整理成：...` and shows `生成路线`.
6. Click `生成路线`.
7. Expected: the result page refreshes in place and assistant says `已按这次对话总结生成新路线，并同步刷新结果页。`

---

## Self-Review

- Spec coverage: result-page-only generation, conversation summarization, user confirmation, existing itinerary generation API reuse, and result-page refresh are all covered.
- Placeholder scan: no unresolved placeholder sections remain.
- Type consistency: action keys are consistently `confirm_itinerary_generate` and `continue_itinerary_generate`; payload field is consistently `generateDraft`.
- Scope check: this is one coherent workflow; no subsystem split is needed.
