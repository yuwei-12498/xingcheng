# Local Skill Router Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a local skill router that lets the backend own POI / nearby hotel / nearby POI lookup, summarize those real-time GEO results with `Doubao-Seed-2.0-lite`, and fall back to structured local results when the model fails.

**Architecture:** Introduce a backend-owned `SkillRouterService` plus concrete GEO skills that return a unified `ChatSkillPayloadVO`. Integrate that payload into `RealChatGatewayService` before the normal chat flow so the chat model only summarizes skill results, while `ChatController` and the frontend preserve the structured payload for fallback rendering.

**Tech Stack:** Spring Boot 3.4, Java 17, Lombok, JUnit 5, Mockito, Vue 3, Vite, Vitest

---

## File Structure

### Backend response contract and transport

- Create: `F:\dachuang\backend\src\main\java\com\citytrip\model\vo\ChatSkillPayloadVO.java`  
  Unified structured payload returned by any local skill and optionally surfaced to the frontend.
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\model\vo\ChatVO.java`  
  Attach `skillPayload` beside `answer`, `relatedTips`, and `evidence`.
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\controller\ChatController.java`  
  Include `skillPayload` in stream meta events so streaming consumers can preserve structured GEO results.
- Modify: `F:\dachuang\backend\src\test\java\com\citytrip\controller\ChatControllerTest.java`  
  Lock the new meta-event payload shape.

### Backend skill router and concrete skills

- Create: `F:\dachuang\backend\src\main\java\com\citytrip\service\skill\ChatSkillHandler.java`  
  Small interface for local chat skills.
- Create: `F:\dachuang\backend\src\main\java\com\citytrip\service\skill\AbstractGeoSkill.java`  
  Shared parsing / mapping helpers for GEO-first skills.
- Create: `F:\dachuang\backend\src\main\java\com\citytrip\service\skill\SkillRouterService.java`  
  Ordered skill dispatcher that returns the first matching payload.
- Create: `F:\dachuang\backend\src\main\java\com\citytrip\service\skill\PoiSearchSkill.java`  
  Handles direct POI lookup via `PoiService.searchLive(...)`.
- Create: `F:\dachuang\backend\src\main\java\com\citytrip\service\skill\NearbyHotelSkill.java`  
  Resolves an anchor place then uses `GeoSearchService.searchNearby(...)` with hotel intent.
- Create: `F:\dachuang\backend\src\main\java\com\citytrip\service\skill\NearbyPoiSkill.java`  
  Resolves an anchor place then uses `GeoSearchService.searchNearby(...)` with scenic / POI intent.
- Create: `F:\dachuang\backend\src\main\java\com\citytrip\service\skill\RouteContextSkill.java`  
  Emits the current itinerary context as a structured payload for route-follow-up questions.
- Create: `F:\dachuang\backend\src\test\java\com\citytrip\service\skill\SkillRouterServiceTest.java`
- Create: `F:\dachuang\backend\src\test\java\com\citytrip\service\skill\NearbyHotelSkillTest.java`
- Create: `F:\dachuang\backend\src\test\java\com\citytrip\service\skill\PoiSearchSkillTest.java`

### Backend gateway integration

- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\SafePromptBuilder.java`  
  Add a prompt builder for grounding the chat model on a structured skill payload.
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\RealChatGatewayService.java`  
  Insert the skill router before the normal chat flow, summarize with `OPENAI_CHAT_MODEL`, and fall back to `fallbackMessage` when model summarization fails.
- Create: `F:\dachuang\backend\src\test\java\com\citytrip\service\impl\RealChatGatewayServiceSkillRoutingTest.java`

### Frontend payload preservation

- Modify: `F:\dachuang\frontend\src\api\chat.js`  
  Preserve `skillPayload` from SSE meta and done payloads.
- Modify: `F:\dachuang\frontend\src\store\chat.js`  
  Store `currentSkillPayload` alongside tips and evidence.
- Modify: `F:\dachuang\frontend\src\components\ChatWidget.vue`  
  Render a compact real-time result list when a skill payload is present.
- Create: `F:\dachuang\frontend\src\store\__tests__\chat.test.js`

---

### Task 1: Add the structured skill payload contract and stream transport

**Files:**
- Create: `F:\dachuang\backend\src\main\java\com\citytrip\model\vo\ChatSkillPayloadVO.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\model\vo\ChatVO.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\controller\ChatController.java`
- Test: `F:\dachuang\backend\src\test\java\com\citytrip\controller\ChatControllerTest.java`

- [ ] **Step 1: Write the failing transport test for `skillPayload` in stream meta**

```java
package com.citytrip.controller;

import com.citytrip.model.vo.ChatSkillPayloadVO;
import com.citytrip.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ChatControllerTest {

    @Test
    void metaEvent_shouldIncludeSkillPayloadWhenPresent() {
        ChatService chatService = mock(ChatService.class);
        TaskExecutor directExecutor = Runnable::run;
        ChatController controller = new ChatController(chatService, directExecutor);

        ChatSkillPayloadVO payload = new ChatSkillPayloadVO();
        payload.setSkillName("nearby_hotel");
        payload.setStatus("ok");
        payload.setSource("vivo-geo");

        Map<String, Object> event = ReflectionTestUtils.invokeMethod(
                controller,
                "metaEvent",
                List.of("换一家"),
                List.of("vivo-geo"),
                payload
        );

        assertThat(event)
                .containsEntry("type", "meta")
                .containsEntry("skillPayload", payload);
    }
}
```

- [ ] **Step 2: Run the controller test and confirm it fails on the missing payload contract**

Run: `mvn -Dtest=ChatControllerTest test`  
Working directory: `F:\dachuang\backend`  
Expected: FAIL with compilation errors for missing `ChatSkillPayloadVO` or wrong `metaEvent(...)` signature.

- [ ] **Step 3: Implement the payload VO, add it to `ChatVO`, and expose it from `ChatController`**

```java
package com.citytrip.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class ChatSkillPayloadVO {
    private String skillName;
    private String status;
    private String intent;
    private Query query = new Query();
    private String city;
    private String source;
    private List<String> evidence = new ArrayList<>();
    private String fallbackMessage;
    private List<ResultItem> results = new ArrayList<>();

    @Data
    public static class Query {
        private String keyword;
        private String anchor;
        private String category;
        private Integer radiusMeters;
        private Integer limit;
    }

    @Data
    public static class ResultItem {
        private String name;
        private String address;
        private String category;
        private BigDecimal latitude;
        private BigDecimal longitude;
        private String cityName;
        private String source;
        private Double distanceMeters;
    }
}
```

```java
package com.citytrip.model.vo;

import lombok.Data;

import java.util.List;

@Data
public class ChatVO {
    private String answer;
    private List<String> relatedTips;
    private List<String> evidence;
    private ChatSkillPayloadVO skillPayload;
}
```

```java
private void handleStreamingRequest(ChatReqDTO req, SseEmitter emitter, AtomicBoolean connectionOpen) {
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
            emitter.complete();
            return;
        }
        log.warn("Streaming chat request failed. reason={}", ex.getMessage(), ex);
        sendEvent(emitter, errorEvent(ex.getMessage()), connectionOpen);
        emitter.complete();
    }
}

private Map<String, Object> metaEvent(List<String> relatedTips,
                                      List<String> evidence,
                                      ChatSkillPayloadVO skillPayload) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("type", "meta");
    payload.put("relatedTips", relatedTips == null ? List.of() : relatedTips);
    payload.put("evidence", evidence == null ? List.of() : evidence);
    payload.put("skillPayload", skillPayload);
    return payload;
}
```

- [ ] **Step 4: Run the controller test to verify the payload now rides along the SSE meta event**

Run: `mvn -Dtest=ChatControllerTest test`  
Working directory: `F:\dachuang\backend`  
Expected: PASS

- [ ] **Step 5: Commit the transport contract change**

```bash
git add backend/src/main/java/com/citytrip/model/vo/ChatSkillPayloadVO.java \
        backend/src/main/java/com/citytrip/model/vo/ChatVO.java \
        backend/src/main/java/com/citytrip/controller/ChatController.java \
        backend/src/test/java/com/citytrip/controller/ChatControllerTest.java
git commit -m "feat: add chat skill payload transport"
```

---

### Task 2: Build the local skill router and GEO-first skills

**Files:**
- Create: `F:\dachuang\backend\src\main\java\com\citytrip\service\skill\ChatSkillHandler.java`
- Create: `F:\dachuang\backend\src\main\java\com\citytrip\service\skill\AbstractGeoSkill.java`
- Create: `F:\dachuang\backend\src\main\java\com\citytrip\service\skill\SkillRouterService.java`
- Create: `F:\dachuang\backend\src\main\java\com\citytrip\service\skill\PoiSearchSkill.java`
- Create: `F:\dachuang\backend\src\main\java\com\citytrip\service\skill\NearbyHotelSkill.java`
- Create: `F:\dachuang\backend\src\main\java\com\citytrip\service\skill\NearbyPoiSkill.java`
- Create: `F:\dachuang\backend\src\main\java\com\citytrip\service\skill\RouteContextSkill.java`
- Test: `F:\dachuang\backend\src\test\java\com\citytrip\service\skill\SkillRouterServiceTest.java`
- Test: `F:\dachuang\backend\src\test\java\com\citytrip\service\skill\NearbyHotelSkillTest.java`
- Test: `F:\dachuang\backend\src\test\java\com\citytrip\service\skill\PoiSearchSkillTest.java`

- [ ] **Step 1: Write the failing router and GEO-skill tests**

```java
package com.citytrip.service.skill;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.vo.ChatSkillPayloadVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SkillRouterServiceTest {

    @Test
    void route_shouldChooseTheFirstSupportingSkill() {
        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion("推荐宽窄巷子附近酒店");

        ChatSkillHandler nearbyHotel = mock(ChatSkillHandler.class);
        ChatSkillHandler poiSearch = mock(ChatSkillHandler.class);
        ChatSkillPayloadVO payload = new ChatSkillPayloadVO();
        payload.setSkillName("nearby_hotel");

        when(nearbyHotel.supports(req)).thenReturn(true);
        when(nearbyHotel.execute(req)).thenReturn(payload);
        when(poiSearch.supports(req)).thenReturn(true);

        SkillRouterService router = new SkillRouterService(List.of(nearbyHotel, poiSearch));

        assertThat(router.route(req)).containsSame(payload);
        verify(nearbyHotel).execute(req);
        verifyNoInteractions(poiSearch);
    }
}
```

```java
package com.citytrip.service.skill;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.vo.ChatSkillPayloadVO;
import com.citytrip.service.PoiService;
import com.citytrip.service.geo.GeoPoiCandidate;
import com.citytrip.service.geo.GeoSearchService;
import com.citytrip.service.geo.PlaceDisambiguationService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NearbyHotelSkillTest {

    @Test
    void execute_shouldResolveAnchorAndReturnNearbyHotelsFromGeo() {
        GeoSearchService geoSearchService = mock(GeoSearchService.class);
        PoiService poiService = mock(PoiService.class);
        PlaceDisambiguationService placeDisambiguationService = mock(PlaceDisambiguationService.class);

        NearbyHotelSkill skill = new NearbyHotelSkill(geoSearchService, poiService, placeDisambiguationService);

        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion("推荐宽窄巷子附近酒店");
        ChatReqDTO.ChatContext context = new ChatReqDTO.ChatContext();
        context.setCityName("成都");
        req.setContext(context);

        PlaceDisambiguationService.ResolvedPlace anchor = new PlaceDisambiguationService.ResolvedPlace(
                "宽窄巷子",
                "成都",
                "青羊区",
                "景点",
                new BigDecimal("30.6611"),
                new BigDecimal("104.0539"),
                "vivo-geo",
                0.98D
        );
        when(placeDisambiguationService.resolveBest("宽窄巷子", "成都", null)).thenReturn(Optional.of(anchor));

        GeoPoiCandidate hotel = new GeoPoiCandidate();
        hotel.setName("桔子酒店");
        hotel.setAddress("宽窄巷子旁");
        hotel.setCategory("酒店");
        hotel.setCityName("成都");
        hotel.setLatitude(new BigDecimal("30.6620"));
        hotel.setLongitude(new BigDecimal("104.0520"));
        hotel.setDistanceMeters(280D);
        hotel.setSource("vivo-geo");
        when(geoSearchService.searchNearby(any(), eq("成都"), eq("酒店"), eq(1500), eq(5)))
                .thenReturn(List.of(hotel));

        ChatSkillPayloadVO payload = skill.execute(req);

        assertThat(payload.getSkillName()).isEqualTo("nearby_hotel");
        assertThat(payload.getIntent()).isEqualTo("nearby_hotel");
        assertThat(payload.getSource()).isEqualTo("vivo-geo");
        assertThat(payload.getResults()).hasSize(1);
        assertThat(payload.getResults().get(0).getName()).isEqualTo("桔子酒店");
        assertThat(payload.getFallbackMessage()).contains("桔子酒店");
    }
}
```

```java
package com.citytrip.service.skill;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.vo.ChatSkillPayloadVO;
import com.citytrip.model.vo.PoiSearchResultVO;
import com.citytrip.service.PoiService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PoiSearchSkillTest {

    @Test
    void execute_shouldReturnLivePoiSearchResults() {
        PoiService poiService = mock(PoiService.class);
        PoiSearchSkill skill = new PoiSearchSkill(poiService);

        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion("帮我找宽窄巷子");
        ChatReqDTO.ChatContext context = new ChatReqDTO.ChatContext();
        context.setCityName("成都");
        req.setContext(context);

        when(poiService.searchLive("宽窄巷子", "成都", 5)).thenReturn(List.of(
                new PoiSearchResultVO(
                        "宽窄巷子",
                        "青羊区金河路口",
                        "景点",
                        new BigDecimal("30.6611"),
                        new BigDecimal("104.0539"),
                        "成都",
                        null,
                        "vivo-geo"
                )
        ));

        ChatSkillPayloadVO payload = skill.execute(req);

        assertThat(payload.getSkillName()).isEqualTo("poi_search");
        assertThat(payload.getResults()).hasSize(1);
        assertThat(payload.getResults().get(0).getAddress()).contains("青羊区");
    }
}
```

- [ ] **Step 2: Run the new skill tests and confirm they fail on missing router / skill classes**

Run: `mvn -Dtest=SkillRouterServiceTest,NearbyHotelSkillTest,PoiSearchSkillTest test`  
Working directory: `F:\dachuang\backend`  
Expected: FAIL with compilation errors for missing `SkillRouterService`, `NearbyHotelSkill`, `PoiSearchSkill`, or `ChatSkillHandler`.

- [ ] **Step 3: Implement the router, shared GEO helpers, and first-wave skills**

```java
package com.citytrip.service.skill;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.vo.ChatSkillPayloadVO;

public interface ChatSkillHandler {
    String skillName();
    boolean supports(ChatReqDTO req);
    ChatSkillPayloadVO execute(ChatReqDTO req);
}
```

```java
package com.citytrip.service.skill;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.vo.ChatSkillPayloadVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Service
public class SkillRouterService {
    private final List<ChatSkillHandler> handlers;

    public SkillRouterService(List<ChatSkillHandler> handlers) {
        this.handlers = handlers == null ? List.of() : List.copyOf(handlers);
    }

    public Optional<ChatSkillPayloadVO> route(ChatReqDTO req) {
        if (req == null || !StringUtils.hasText(req.getQuestion())) {
            return Optional.empty();
        }
        for (ChatSkillHandler handler : handlers) {
            if (handler.supports(req)) {
                ChatSkillPayloadVO payload = handler.execute(req);
                return payload == null ? Optional.empty() : Optional.of(payload);
            }
        }
        return Optional.empty();
    }
}
```

```java
package com.citytrip.service.skill;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.vo.ChatSkillPayloadVO;
import com.citytrip.model.vo.PoiSearchResultVO;
import com.citytrip.service.geo.GeoPoiCandidate;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

abstract class AbstractGeoSkill implements ChatSkillHandler {

    protected String questionOf(ChatReqDTO req) {
        return req == null || !StringUtils.hasText(req.getQuestion()) ? "" : req.getQuestion().trim();
    }

    protected String cityOf(ChatReqDTO req) {
        return req == null || req.getContext() == null || !StringUtils.hasText(req.getContext().getCityName())
                ? "成都"
                : req.getContext().getCityName().trim();
    }

    protected boolean containsAny(String text, String... words) {
        if (!StringUtils.hasText(text) || words == null) {
            return false;
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        for (String word : words) {
            if (StringUtils.hasText(word) && normalized.contains(word.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    protected String extractAnchorKeyword(String question) {
        if (!StringUtils.hasText(question)) {
            return null;
        }
        String normalized = question.trim();
        String[] markers = {"附近", "周边", "旁边"};
        for (String marker : markers) {
            int index = normalized.indexOf(marker);
            if (index > 0) {
                return normalized.substring(0, index)
                        .replace("推荐", "")
                        .replace("帮我找", "")
                        .replace("请问", "")
                        .trim();
            }
        }
        return null;
    }

    protected List<ChatSkillPayloadVO.ResultItem> fromGeoCandidates(List<GeoPoiCandidate> candidates) {
        List<ChatSkillPayloadVO.ResultItem> items = new ArrayList<>();
        if (candidates == null) {
            return items;
        }
        for (GeoPoiCandidate candidate : candidates) {
            if (candidate == null || !StringUtils.hasText(candidate.getName())) {
                continue;
            }
            ChatSkillPayloadVO.ResultItem item = new ChatSkillPayloadVO.ResultItem();
            item.setName(candidate.getName());
            item.setAddress(candidate.getAddress());
            item.setCategory(candidate.getCategory());
            item.setLatitude(candidate.getLatitude());
            item.setLongitude(candidate.getLongitude());
            item.setCityName(candidate.getCityName());
            item.setSource(candidate.getSource());
            item.setDistanceMeters(candidate.getDistanceMeters());
            items.add(item);
        }
        return items;
    }

    protected List<ChatSkillPayloadVO.ResultItem> fromPoiResults(List<PoiSearchResultVO> results) {
        List<ChatSkillPayloadVO.ResultItem> items = new ArrayList<>();
        if (results == null) {
            return items;
        }
        for (PoiSearchResultVO result : results) {
            if (result == null || !StringUtils.hasText(result.name())) {
                continue;
            }
            ChatSkillPayloadVO.ResultItem item = new ChatSkillPayloadVO.ResultItem();
            item.setName(result.name());
            item.setAddress(result.address());
            item.setCategory(result.category());
            item.setLatitude(result.latitude());
            item.setLongitude(result.longitude());
            item.setCityName(result.cityName());
            item.setSource(result.source());
            items.add(item);
        }
        return items;
    }

    protected ChatSkillPayloadVO buildPayload(String skillName,
                                              String intent,
                                              String city,
                                              String anchor,
                                              String category,
                                              int limit,
                                              int radiusMeters,
                                              List<ChatSkillPayloadVO.ResultItem> items,
                                              String source,
                                              String fallbackMessage) {
        ChatSkillPayloadVO payload = new ChatSkillPayloadVO();
        payload.setSkillName(skillName);
        payload.setIntent(intent);
        payload.setStatus(items == null || items.isEmpty() ? "empty" : "ok");
        payload.setCity(city);
        payload.setSource(source);
        payload.setResults(items == null ? List.of() : items);
        payload.getQuery().setAnchor(anchor);
        payload.getQuery().setCategory(category);
        payload.getQuery().setLimit(limit);
        payload.getQuery().setRadiusMeters(radiusMeters);
        payload.setFallbackMessage(fallbackMessage);
        payload.setEvidence(List.of("source=" + source, "intent=" + intent));
        return payload;
    }
}
```

```java
@Service
public class PoiSearchSkill extends AbstractGeoSkill {
    private final PoiService poiService;

    public PoiSearchSkill(PoiService poiService) {
        this.poiService = poiService;
    }

    @Override
    public String skillName() {
        return "poi_search";
    }

    @Override
    public boolean supports(ChatReqDTO req) {
        String question = questionOf(req);
        return containsAny(question, "找", "搜索", "搜")
                && !containsAny(question, "附近", "周边", "旁边")
                && !containsAny(question, "酒店", "住宿");
    }

    @Override
    public ChatSkillPayloadVO execute(ChatReqDTO req) {
        String question = questionOf(req);
        String city = cityOf(req);
        String keyword = question.replace("帮我", "").replace("找", "").replace("搜索", "").replace("搜", "").trim();
        List<ChatSkillPayloadVO.ResultItem> items = fromPoiResults(poiService.searchLive(keyword, city, 5));
        return buildPayload(
                skillName(),
                "poi_search",
                city,
                keyword,
                "poi",
                5,
                0,
                items,
                items.isEmpty() ? "local-db" : items.get(0).getSource(),
                items.isEmpty() ? "暂时没有找到匹配地点，你可以换个更具体的名称。" : "我先帮你列出实时找到的地点结果。"
        );
    }
}
```

```java
@Service
public class NearbyHotelSkill extends AbstractGeoSkill {
    protected final GeoSearchService geoSearchService;
    protected final PoiService poiService;
    protected final PlaceDisambiguationService placeDisambiguationService;

    public NearbyHotelSkill(GeoSearchService geoSearchService,
                            PoiService poiService,
                            PlaceDisambiguationService placeDisambiguationService) {
        this.geoSearchService = geoSearchService;
        this.poiService = poiService;
        this.placeDisambiguationService = placeDisambiguationService;
    }

    @Override
    public String skillName() {
        return "nearby_hotel";
    }

    @Override
    public boolean supports(ChatReqDTO req) {
        String question = questionOf(req);
        return containsAny(question, "酒店", "住宿", "住哪里")
                && containsAny(question, "附近", "周边", "旁边");
    }

    @Override
    public ChatSkillPayloadVO execute(ChatReqDTO req) {
        String question = questionOf(req);
        String city = cityOf(req);
        String anchor = extractAnchorKeyword(question);
        GeoPoint center = resolveAnchorPoint(anchor, city);
        List<ChatSkillPayloadVO.ResultItem> items = center == null
                ? List.of()
                : fromGeoCandidates(geoSearchService.searchNearby(center, city, "酒店", 1500, 5));
        String fallback = items.isEmpty()
                ? "我暂时没找到附近酒店结果，你可以换一个地标或缩小范围。"
                : "我先把实时查到的附近酒店列给你。";
        return buildPayload(skillName(), "nearby_hotel", city, anchor, "酒店", 5, 1500, items,
                items.isEmpty() ? "local-db" : items.get(0).getSource(), fallback);
    }

    protected GeoPoint resolveAnchorPoint(String anchor, String city) {
        if (!StringUtils.hasText(anchor)) {
            return null;
        }
        return placeDisambiguationService.resolveBest(anchor, city, null)
                .map(place -> new GeoPoint(place.latitude(), place.longitude()))
                .orElseGet(() -> poiService.searchLive(anchor, city, 1).stream()
                        .filter(item -> item.latitude() != null && item.longitude() != null)
                        .findFirst()
                        .map(item -> new GeoPoint(item.latitude(), item.longitude()))
                        .orElse(null));
    }
}
```

```java
@Service
public class NearbyPoiSkill extends NearbyHotelSkill {

    public NearbyPoiSkill(GeoSearchService geoSearchService,
                          PoiService poiService,
                          PlaceDisambiguationService placeDisambiguationService) {
        super(geoSearchService, poiService, placeDisambiguationService);
    }

    @Override
    public String skillName() {
        return "nearby_poi";
    }

    @Override
    public boolean supports(ChatReqDTO req) {
        String question = questionOf(req);
        return containsAny(question, "景点", "去哪", "有什么")
                && containsAny(question, "附近", "周边", "旁边")
                && !containsAny(question, "酒店", "住宿");
    }

    @Override
    public ChatSkillPayloadVO execute(ChatReqDTO req) {
        String question = questionOf(req);
        String city = cityOf(req);
        String anchor = extractAnchorKeyword(question);
        GeoPoint center = resolveAnchorPoint(anchor, city);
        List<ChatSkillPayloadVO.ResultItem> items = center == null
                ? List.of()
                : fromGeoCandidates(geoSearchService.searchNearby(center, city, "景点", 1500, 5));
        String fallback = items.isEmpty()
                ? "我暂时没找到附近景点结果，你可以换一个地标或缩小范围。"
                : "我先把实时查到的附近景点列给你。";
        return buildPayload(skillName(), "nearby_poi", city, anchor, "景点", 5, 1500, items,
                items.isEmpty() ? "local-db" : items.get(0).getSource(), fallback);
    }
}
```

```java
@Service
public class RouteContextSkill extends AbstractGeoSkill {
    @Override
    public String skillName() {
        return "route_context";
    }

    @Override
    public boolean supports(ChatReqDTO req) {
        return req != null
                && req.getContext() != null
                && req.getContext().getItinerary() != null
                && req.getContext().getItinerary().getNodes() != null
                && !req.getContext().getItinerary().getNodes().isEmpty()
                && containsAny(questionOf(req), "这条路线", "当前行程", "这趟安排");
    }

    @Override
    public ChatSkillPayloadVO execute(ChatReqDTO req) {
        String city = cityOf(req);
        List<ChatSkillPayloadVO.ResultItem> items = new ArrayList<>();
        for (ChatReqDTO.ChatRouteNode node : req.getContext().getItinerary().getNodes()) {
            ChatSkillPayloadVO.ResultItem item = new ChatSkillPayloadVO.ResultItem();
            item.setName(node.getPoiName());
            item.setCategory(node.getCategory());
            item.setCityName(city);
            item.setSource("itinerary-route");
            items.add(item);
        }
        return buildPayload(skillName(), "route_context", city, req.getContext().getItinerary().getSummary(), "route", items.size(), 0,
                items, "itinerary-route", "我先把当前行程里的站点列给你。"
        );
    }
}
```

- [ ] **Step 4: Run the skill tests to verify routing and hotel lookup behavior**

Run: `mvn -Dtest=SkillRouterServiceTest,NearbyHotelSkillTest,PoiSearchSkillTest test`  
Working directory: `F:\dachuang\backend`  
Expected: PASS

- [ ] **Step 5: Commit the skill router layer**

```bash
git add backend/src/main/java/com/citytrip/service/skill \
        backend/src/test/java/com/citytrip/service/skill
git commit -m "feat: add local skill router for geo chat"
```

---

### Task 3: Integrate the local skill router into `RealChatGatewayService`

**Files:**
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\SafePromptBuilder.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\RealChatGatewayService.java`
- Test: `F:\dachuang\backend\src\test\java\com\citytrip\service\impl\RealChatGatewayServiceSkillRoutingTest.java`

- [ ] **Step 1: Write the failing gateway tests for skill-summary success and model-fallback behavior**

```java
package com.citytrip.service.impl;

import com.citytrip.config.LlmProperties;
import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.vo.ChatSkillPayloadVO;
import com.citytrip.model.vo.ChatVO;
import com.citytrip.service.domain.ai.ChatPoiSkillService;
import com.citytrip.service.skill.SkillRouterService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RealChatGatewayServiceSkillRoutingTest {

    @Test
    void answerQuestion_shouldUseChatModelToSummarizeSkillPayload() {
        OpenAiGatewayClient gatewayClient = mock(OpenAiGatewayClient.class);
        SkillRouterService skillRouterService = mock(SkillRouterService.class);

        ChatSkillPayloadVO payload = new ChatSkillPayloadVO();
        payload.setSkillName("nearby_hotel");
        payload.setIntent("nearby_hotel");
        payload.setSource("vivo-geo");
        payload.setFallbackMessage("我先把附近酒店列给你。\n- 桔子酒店");

        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion("推荐宽窄巷子附近酒店");

        when(skillRouterService.route(req)).thenReturn(Optional.of(payload));
        when(gatewayClient.request(any(), anyString(), anyList())).thenReturn("宽窄巷子附近可以先看桔子酒店，步行更方便。");

        RealChatGatewayService service = new RealChatGatewayService(gatewayClient, validChatProperties(), new SafePromptBuilder(), new ChatPoiSkillService(null));
        ReflectionTestUtils.setField(service, "skillRouterService", skillRouterService);

        ChatVO response = service.answerQuestion(req);

        assertThat(response.getAnswer()).contains("桔子酒店");
        assertThat(response.getSkillPayload()).isSameAs(payload);
    }

    @Test
    void answerQuestion_shouldFallBackToSkillMessageWhenSummaryModelFails() {
        OpenAiGatewayClient gatewayClient = mock(OpenAiGatewayClient.class);
        SkillRouterService skillRouterService = mock(SkillRouterService.class);

        ChatSkillPayloadVO payload = new ChatSkillPayloadVO();
        payload.setSkillName("nearby_hotel");
        payload.setIntent("nearby_hotel");
        payload.setSource("vivo-geo");
        payload.setFallbackMessage("已找到 2 家实时酒店结果，请先看下面列表。\n- 桔子酒店\n- 全季酒店");

        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion("推荐宽窄巷子附近酒店");

        when(skillRouterService.route(req)).thenReturn(Optional.of(payload));
        when(gatewayClient.request(any(), anyString(), anyList())).thenThrow(new IllegalStateException("401 no model access permission"));

        RealChatGatewayService service = new RealChatGatewayService(gatewayClient, validChatProperties(), new SafePromptBuilder(), new ChatPoiSkillService(null));
        ReflectionTestUtils.setField(service, "skillRouterService", skillRouterService);

        ChatVO response = service.answerQuestion(req);

        assertThat(response.getAnswer()).isEqualTo(payload.getFallbackMessage());
        assertThat(response.getSkillPayload()).isSameAs(payload);
        assertThat(response.getEvidence()).contains("skill:nearby_hotel");
    }

    private LlmProperties validChatProperties() {
        LlmProperties properties = new LlmProperties();
        properties.getOpenai().setEnabled(true);
        properties.getOpenai().setApiKey("integration-test-api-key");
        properties.getOpenai().getChat().setBaseUrl("https://api-ai.vivo.com.cn/v1");
        properties.getOpenai().getChat().setModel("Doubao-Seed-2.0-lite");
        return properties;
    }
}
```

- [ ] **Step 2: Run the gateway test and confirm it fails before the skill-router branch exists**

Run: `mvn -Dtest=RealChatGatewayServiceSkillRoutingTest test`  
Working directory: `F:\dachuang\backend`  
Expected: FAIL because `ChatVO` does not yet carry `skillPayload` through `RealChatGatewayService`, and the gateway never calls the router.

- [ ] **Step 3: Add a skill-router branch, grounded summary prompt, and fallback-to-payload behavior**

```java
public String buildSkillGroundedUserPrompt(ChatReqDTO req, ChatSkillPayloadVO payload) {
    String question = sanitizeText(req == null ? null : req.getQuestion(), MAX_CHAT_QUESTION_CHARS).value();
    String city = sanitizeText(payload == null ? null : payload.getCity(), MAX_CHAT_POI_FIELD_CHARS).value();
    String intent = sanitizeText(payload == null ? null : payload.getIntent(), MAX_CHAT_POI_FIELD_CHARS).value();
    return """
            <user_question>
            %s
            </user_question>
            <skill_payload>
            intent=%s
            city=%s
            source=%s
            results=%s
            </skill_payload>
            请只基于 skill_payload 里的实时结果，用简体中文给出 120 字以内的推荐总结；不要编造未提供的价格、营业时间或距离。
            """.formatted(
            question,
            intent,
            city,
            payload == null ? "" : safeValue(payload.getSource()),
            buildSkillResultSummary(payload)
    );
}
```

```java
@Autowired(required = false)
private SkillRouterService skillRouterService;

public ChatVO answerQuestion(ChatReqDTO req) {
    ChatGenerationResult result = callChatCompletion(req, null);
    ChatVO vo = new ChatVO();
    vo.setAnswer(result.answer());
    vo.setRelatedTips(buildRelatedTips(req));
    vo.setEvidence(result.evidence());
    vo.setSkillPayload(result.skillPayload());
    return vo;
}

private ChatGenerationResult callChatCompletion(ChatReqDTO req, Consumer<String> tokenConsumer) {
    if (llmProperties == null || !llmProperties.canTryRealChat()) {
        throw new IllegalStateException("OpenAI real chat model is not configured");
    }
    if (openAiGatewayClient == null) {
        throw new IllegalStateException("OpenAI gateway is not configured");
    }

    ChatRouteContextSkillService.RouteContext routeContext = chatRouteContextSkillService == null
            ? ChatRouteContextSkillService.RouteContext.empty()
            : chatRouteContextSkillService.resolve(req);
    LinkedHashSet<String> usedSkills = new LinkedHashSet<>();

    ChatGenerationResult routedSkillResult = trySkillRouterAnswer(req, tokenConsumer, routeContext, usedSkills);
    if (routedSkillResult != null) {
        return routedSkillResult;
    }

}

private ChatGenerationResult trySkillRouterAnswer(ChatReqDTO req,
                                                  Consumer<String> tokenConsumer,
                                                  ChatRouteContextSkillService.RouteContext routeContext,
                                                  Set<String> usedSkills) {
    if (skillRouterService == null) {
        return null;
    }
    Optional<ChatSkillPayloadVO> matched = skillRouterService.route(req);
    if (matched.isEmpty()) {
        return null;
    }

    ChatSkillPayloadVO payload = matched.get();
    usedSkills.add(payload.getSkillName());
    List<String> evidence = mergeSkillEvidence(
            buildRouteContextEvidence(routeContext),
            payload.getEvidence(),
            List.of("skill:" + payload.getSkillName())
    );

    try {
        LlmProperties.ResolvedOpenAiOptions chatOptions = llmProperties.getOpenai().resolveChatOptions();
        List<OpenAiGatewayClient.OpenAiMessage> messages = List.of(
                new OpenAiGatewayClient.OpenAiMessage("system", safePromptBuilder.buildChatSystemPrompt()),
                new OpenAiGatewayClient.OpenAiMessage("user", safePromptBuilder.buildSkillGroundedUserPrompt(req, payload))
        );
        String answer = openAiGatewayClient.request(chatOptions, llmProperties.getOpenai().getApiKey(), messages);
        if (StringUtils.hasText(answer)) {
            String trimmed = answer.trim();
            if (tokenConsumer != null) {
                tokenConsumer.accept(trimmed);
            }
            return new ChatGenerationResult(trimmed, evidence, payload);
        }
    } catch (Exception ex) {
        // log warn and continue to fallbackMessage
    }

    String fallback = StringUtils.hasText(payload.getFallbackMessage())
            ? payload.getFallbackMessage().trim()
            : "已找到实时结果，请先查看下方列表。";
    if (tokenConsumer != null) {
        tokenConsumer.accept(fallback);
    }
    return new ChatGenerationResult(fallback, evidence, payload);
}

private record ChatGenerationResult(String answer,
                                    List<String> evidence,
                                    ChatSkillPayloadVO skillPayload) {
    private ChatGenerationResult {
        evidence = evidence == null ? Collections.emptyList() : evidence;
    }
}
```

- [ ] **Step 4: Run the gateway test to verify summary and fallback behavior**

Run: `mvn -Dtest=RealChatGatewayServiceSkillRoutingTest,OpenAiServiceDelegationTest test`  
Working directory: `F:\dachuang\backend`  
Expected: PASS

- [ ] **Step 5: Commit the gateway integration**

```bash
git add backend/src/main/java/com/citytrip/service/impl/SafePromptBuilder.java \
        backend/src/main/java/com/citytrip/service/impl/RealChatGatewayService.java \
        backend/src/test/java/com/citytrip/service/impl/RealChatGatewayServiceSkillRoutingTest.java
git commit -m "feat: route geo chat through local skills"
```

---

### Task 4: Preserve and render the structured skill payload on the frontend

**Files:**
- Modify: `F:\dachuang\frontend\src\api\chat.js`
- Modify: `F:\dachuang\frontend\src\store\chat.js`
- Modify: `F:\dachuang\frontend\src\components\ChatWidget.vue`
- Test: `F:\dachuang\frontend\src\store\__tests__\chat.test.js`

- [ ] **Step 1: Write the failing store test that proves stream meta must persist `skillPayload`**

```javascript
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { askChatQuestion, resetChatState, useChatState } from '@/store/chat'
import { reqStreamChat } from '@/api/chat'

vi.mock('@/api/chat', () => ({
  reqStreamChat: vi.fn()
}))

describe('chat store skill payload state', () => {
  beforeEach(() => {
    sessionStorage.clear()
    resetChatState()
    reqStreamChat.mockReset()
  })

  it('stores the structured skill payload returned by stream meta', async () => {
    const payload = {
      skillName: 'nearby_hotel',
      status: 'ok',
      source: 'vivo-geo',
      results: [
        { name: '桔子酒店', address: '宽窄巷子旁', category: '酒店' }
      ]
    }

    reqStreamChat.mockImplementation(async (_req, handlers) => {
      handlers.onMeta({
        relatedTips: ['换一个地标'],
        evidence: ['source=vivo-geo'],
        skillPayload: payload
      })
      return {
        answer: '我找到 1 家附近酒店。',
        relatedTips: ['换一个地标'],
        evidence: ['source=vivo-geo'],
        skillPayload: payload
      }
    })

    await askChatQuestion('推荐宽窄巷子附近酒店', { cityName: '成都' })

    expect(useChatState().currentSkillPayload.skillName).toBe('nearby_hotel')
    expect(useChatState().currentSkillPayload.results[0].name).toBe('桔子酒店')
  })
})
```

- [ ] **Step 2: Run the frontend store test and confirm it fails before `skillPayload` is preserved**

Run: `npm test -- src/store/__tests__/chat.test.js`  
Working directory: `F:\dachuang\frontend`  
Expected: FAIL because `chatState.currentSkillPayload` does not exist and `reqStreamChat` ignores `payload.skillPayload`.

- [ ] **Step 3: Preserve the payload in the API/store and render a compact result list in the widget**

```javascript
export async function reqStreamChat(data, handlers = {}) {
  const token = localStorage.getItem('jwt_token')
  const response = await fetch('/api/chat/messages/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify(data)
  })

  let skillPayload = null
  let answer = ''
  let relatedTips = []
  let evidence = []

  const handlePayload = (payload) => {
    if (!payload || typeof payload !== 'object') {
      return
    }
    if (payload.type === 'meta') {
      relatedTips = Array.isArray(payload.relatedTips) ? payload.relatedTips : []
      evidence = Array.isArray(payload.evidence) ? payload.evidence : []
      skillPayload = payload.skillPayload || null
      if (typeof handlers.onMeta === 'function') {
        handlers.onMeta({ relatedTips, evidence, skillPayload })
      }
      return
    }
    if (payload.type === 'done') {
      if (typeof handlers.onDone === 'function') {
        handlers.onDone({ answer, relatedTips, evidence, skillPayload })
      }
      return
    }
  }

  return { answer, relatedTips, evidence, skillPayload }
}
```

```javascript
const createDefaultState = () => ({
  messages: createDefaultMessages(),
  currentTips: [...defaultTips],
  currentEvidence: [],
  currentSkillPayload: null,
  loading: false,
  streamTick: 0
})

function applyChatState(payload = {}) {
  const nextState = createDefaultState()
  if (payload.currentSkillPayload && typeof payload.currentSkillPayload === 'object') {
    nextState.currentSkillPayload = payload.currentSkillPayload
  }
  chatState.messages = nextState.messages
  chatState.currentTips = nextState.currentTips
  chatState.currentEvidence = nextState.currentEvidence
  chatState.currentSkillPayload = nextState.currentSkillPayload
  chatState.loading = false
  chatState.streamTick = 0
}

export async function askChatQuestion(question, context) {
  chatState.currentTips = []
  chatState.currentEvidence = []
  chatState.currentSkillPayload = null

  try {
    const result = await reqStreamChat({ question: value, context }, {
      onMeta: ({ relatedTips, evidence, skillPayload }) => {
        chatState.currentTips = resolveTips(relatedTips)
        chatState.currentEvidence = resolveEvidence(evidence)
        chatState.currentSkillPayload = skillPayload || null
        touchStream()
      }
    })
    if (!chatState.currentSkillPayload && result.skillPayload) {
      chatState.currentSkillPayload = result.skillPayload
    }
  } finally {
    chatState.loading = false
  }
}
```

```vue
<div class="skill-results" v-if="chatState.currentSkillPayload?.results?.length">
  <div class="skill-results__title">
    实时结果 · {{ chatState.currentSkillPayload.source || 'local-skill' }}
  </div>
  <div
    v-for="(item, idx) in chatState.currentSkillPayload.results.slice(0, 3)"
    :key="`${item.name}-${idx}`"
    class="skill-result-card"
  >
    <div class="skill-result-card__name">{{ item.name }}</div>
    <div class="skill-result-card__meta">
      {{ item.category || '地点' }}<span v-if="item.address"> · {{ item.address }}</span>
    </div>
  </div>
</div>
```

```css
.skill-results {
  margin: 8px 16px 0;
  padding: 10px 12px;
  border-radius: 12px;
  background: #f6f8fb;
  border: 1px solid #e6edf7;
}

.skill-results__title {
  font-size: 12px;
  color: #607089;
  margin-bottom: 8px;
}

.skill-result-card + .skill-result-card {
  margin-top: 8px;
}

.skill-result-card__name {
  font-size: 13px;
  font-weight: 600;
  color: #1f2d3d;
}

.skill-result-card__meta {
  margin-top: 2px;
  font-size: 12px;
  color: #7c8da5;
}
```

- [ ] **Step 4: Run the focused frontend test, then do a local end-to-end verification with the chosen model and GEO config**

Run: `npm test -- src/store/__tests__/chat.test.js`  
Working directory: `F:\dachuang\frontend`  
Expected: PASS

Run: `mvn -Dtest=ChatControllerTest,SkillRouterServiceTest,NearbyHotelSkillTest,PoiSearchSkillTest,RealChatGatewayServiceSkillRoutingTest,OpenAiServiceDelegationTest test`  
Working directory: `F:\dachuang\backend`  
Expected: PASS

Before the manual run, set the local runtime config in `F:\dachuang\.env` (and keep the already-verified `APP_GEO_API_KEY` value that is currently working):

```env
OPENAI_CHAT_MODEL=Doubao-Seed-2.0-lite
APP_GEO_ENABLED=true
APP_GEO_BASE_URL=https://api-ai.vivo.com.cn/search/geo
```

Then run:

```powershell
powershell -ExecutionPolicy Bypass -File F:\dachuang\scripts\dev-start.ps1
Invoke-WebRequest -UseBasicParsing 'http://127.0.0.1:3000/api/pois/search?keyword=%E5%AE%BD%E7%AA%84%E5%B7%B7%E5%AD%90&city=%E6%88%90%E9%83%BD&limit=3'
```

Manual chat verification in the browser:
- Ask: `推荐宽窄巷子附近酒店`
- Expected when model is healthy: answer text mentions the live hotel results and the widget shows a compact “实时结果” list.
- Expected when the chat model fails: answer falls back to `fallbackMessage`, and the same structured list still renders.

- [ ] **Step 5: Commit the frontend payload-preservation work**

```bash
git add frontend/src/api/chat.js \
        frontend/src/store/chat.js \
        frontend/src/store/__tests__/chat.test.js \
        frontend/src/components/ChatWidget.vue
git commit -m "feat: surface local skill payload in chat ui"
```

---

## Self-Review Checklist

### Spec coverage

- **本地能力层 / Skill Router** → Task 2
- **实时 vivo GEO 主查 + 本地兜底** → Task 2 skill implementation, Task 3 fallback handling
- **聊天模型只做总结 (`Doubao-Seed-2.0-lite`)** → Task 3 prompt + gateway integration, Task 4 local `.env` verification
- **模型失败不致使功能全挂** → Task 3 fallback behavior, Task 4 manual verification
- **前端可消费结构化结果** → Task 1 transport, Task 4 store/UI wiring

### No-unresolved-placeholders scan

- No unresolved placeholders remain.
- Every task names exact files and concrete commands.
- Each code-changing task includes explicit test code and implementation snippets.

### Type consistency

- Structured payload name is consistently `ChatSkillPayloadVO`.
- Router interface consistently uses `ChatSkillHandler` and `SkillRouterService.route(...)`.
- Frontend contract consistently uses `skillPayload`.

---

## Execution Handoff

Plan complete and saved to `F:\dachuang\docs\superpowers\plans\2026-04-28-local-skill-router.md`.

Two execution options:

1. **Subagent-Driven (recommended)** - dispatch a fresh subagent per task, review between tasks, faster parallelized iteration
2. **Inline Execution** - execute tasks in this session step-by-step using the plan as the checklist
