# vivo 第一优先级能力 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 vivo 的 chat、function calling、POI 搜索、embedding + rerank 四个第一优先级能力接入为项目内稳定、可降级、可测试的能力底座，并保持路线页与社区页现有视觉不回退。

**Architecture:** 保留现有 `OpenAiGatewayClient` 作为调用门面，在其内部拆出 vivo 专用 provider client，并把 tool loop、POI 搜索和社区语义搜索分别收敛为独立 capability service。业务层只消费统一能力，不直接拼接 vivo 协议细节；社区搜索走“本地候选 -> embedding 粗排 -> rerank 精排 -> 降级回退”的固定链路。

**Tech Stack:** Spring Boot 3、MyBatis、RestTemplate、Jackson、JUnit 5、MockRestServiceServer、Vue 3、Axios、Vite

---

## 文件结构与职责锁定

### 既有文件（将修改）
- `F:\dachuang\backend\src\main\java\com\citytrip\config\LlmProperties.java`  
  扩展 `tool` scene、vivo 模型白名单校验、tool readiness 判断。
- `F:\dachuang\backend\src\main\java\com\citytrip\model\vo\ChatStatusVO.java`  
  扩展 `toolReady`、`geoReady`、`embeddingReady`、`rerankReady`、`warnings`。
- `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\OpenAiChatServiceImpl.java`  
  让真实 chat status 带出 `toolReady` 等 readiness 细节。
- `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\RoutingChatServiceImpl.java`  
  汇总配置问题、warnings 与 readiness，对 `/api/chat/messages/status` 输出稳定契约。
- `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\MockChatServiceImpl.java`  
  为 mock 场景补齐新增 readiness 字段的默认值。
- `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\OpenAiGatewayClient.java`  
  保持门面职责不变，内部切分普通请求 / vivo 请求 / tool 调用解析。
- `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\RealChatGatewayService.java`  
  接入 tool loop，把 `search_poi` / `search_community_posts` / `get_route_context` 接进聊天主链路。
- `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\RealLlmGatewayService.java`  
  路线文案、路线装饰继续走 text scene，但通过新网关获得 `requestId` 与 vivo 错误模型。
- `F:\dachuang\backend\src\main\java\com\citytrip\service\geo\impl\GeoSearchServiceImpl.java`  
  统一按 1736 请求格式查询 `keywords/city/page_num/page_size/requestId`，并去掉假 route 语义。
- `F:\dachuang\backend\src\main\java\com\citytrip\service\PoiService.java`  
  扩展 live POI 搜索服务契约。
- `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\PoiServiceImpl.java`  
  串起 vivo live 搜索、本地模糊回退和统一 `PoiSearchResultVO` 输出。
- `F:\dachuang\backend\src\main\java\com\citytrip\controller\PoiController.java`  
  新增 `/api/pois/search`。
- `F:\dachuang\backend\src\main\java\com\citytrip\service\application\community\CommunityItineraryQueryService.java`  
  在 `keyword` 非空时接入语义搜索链路。
- `F:\dachuang\frontend\src\api\chat.js`  
  消费更细的 chat status。
- `F:\dachuang\frontend\src\api\poi.js`  
  新增 POI live 搜索封装。

### 新增文件（将创建）
- `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\vivo\VivoRequestIdFactory.java`
- `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\vivo\VivoChatClient.java`
- `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\vivo\VivoEmbeddingClient.java`
- `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\vivo\VivoRerankClient.java`
- `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\vivo\VivoToolDefinition.java`
- `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\vivo\VivoToolRegistry.java`
- `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\vivo\VivoFunctionCallingService.java`
- `F:\dachuang\backend\src\main\java\com\citytrip\service\application\community\CommunitySemanticSearchService.java`
- `F:\dachuang\backend\src\main\java\com\citytrip\model\vo\PoiSearchResultVO.java`
- `F:\dachuang\backend\src\test\java\com\citytrip\config\LlmPropertiesTest.java`
- `F:\dachuang\backend\src\test\java\com\citytrip\service\impl\RoutingChatServiceImplTest.java`
- `F:\dachuang\backend\src\test\java\com\citytrip\service\impl\VivoFunctionCallingServiceTest.java`
- `F:\dachuang\backend\src\test\java\com\citytrip\service\application\community\CommunitySemanticSearchServiceTest.java`
- `F:\dachuang\backend\src\test\java\com\citytrip\controller\PoiControllerTest.java`

---

### Task 1: 扩展配置模型与 readiness 契约

**Files:**
- Create: `F:\dachuang\backend\src\test\java\com\citytrip\config\LlmPropertiesTest.java`
- Create: `F:\dachuang\backend\src\test\java\com\citytrip\service\impl\RoutingChatServiceImplTest.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\config\LlmProperties.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\model\vo\ChatStatusVO.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\OpenAiChatServiceImpl.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\RoutingChatServiceImpl.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\MockChatServiceImpl.java`
- Modify: `F:\dachuang\backend\src\main\resources\application.yml`

- [ ] **Step 1: 写失败测试，锁定 tool scene 与 vivo readiness 契约**

```java
class LlmPropertiesTest {

    @Test
    void shouldReportToolConfigIssuesWhenToolSceneMissing() {
        LlmProperties properties = new LlmProperties();
        properties.getOpenai().setApiKey("sk-test");
        properties.getOpenai().setBaseUrl("https://api-ai.vivo.com.cn/v1");
        properties.getOpenai().setModel("Doubao-Seed-2.0-mini");
        properties.getOpenai().getChat().setModel("Doubao-Seed-2.0-mini");
        properties.getOpenai().getText().setModel("Doubao-Seed-2.0-pro");

        assertThat(properties.getRealToolConfigIssues())
                .contains("OPENAI_TOOL_MODEL is empty");
    }

    @Test
    void shouldWarnWhenConfiguredModelIsOutsideVivoAllowList() {
        LlmProperties properties = new LlmProperties();
        properties.getOpenai().setApiKey("sk-test");
        properties.getOpenai().setBaseUrl("https://api-ai.vivo.com.cn/v1");
        properties.getOpenai().getChat().setModel("BlueLM-7B-Chat");

        assertThat(properties.getRealModelConfigWarnings())
                .anyMatch(item -> item.contains("not in vivo allow-list"));
    }
}
```

```java
class RoutingChatServiceImplTest {

    @Test
    void getStatusShouldExposeExtendedReadinessFields() {
        LlmProperties properties = new LlmProperties();
        properties.setProvider("real");
        properties.getOpenai().setApiKey("sk-test");
        properties.getOpenai().setBaseUrl("https://api-ai.vivo.com.cn/v1");
        properties.getOpenai().getChat().setModel("Doubao-Seed-2.0-mini");
        properties.getOpenai().getTool().setModel("Volc-DeepSeek-V3.2");

        RoutingChatServiceImpl service = new RoutingChatServiceImpl(null, new MockChatServiceImpl(), properties);
        ChatStatusVO status = service.getStatus();

        assertThat(status.isToolReady()).isTrue();
        assertThat(status.getWarnings()).isNotNull();
    }
}
```

- [ ] **Step 2: 运行测试，确认它们先红**

Run:

```bash
cd /d F:\dachuang\backend
mvn -q -Dtest=LlmPropertiesTest,RoutingChatServiceImplTest test
```

Expected:
- FAIL，提示 `getRealToolConfigIssues` 不存在或 warning 不包含 allow-list 结果。

- [ ] **Step 3: 最小实现 tool scene、allow-list 与 VO 字段**

```java
public static class OpenAiProperties {
    private SceneProperties tool = new SceneProperties();

    public SceneProperties getTool() {
        return tool;
    }

    public void setTool(SceneProperties tool) {
        this.tool = tool;
    }

    public ResolvedOpenAiOptions resolveToolOptions() {
        return resolveSceneOptions(tool);
    }
}

public List<String> getRealToolConfigIssues() {
    return getRealSceneConfigIssues(openai == null ? null : openai.resolveToolOptions(), "tool");
}

private static final Set<String> VIVO_ALLOWED_MODELS = Set.of(
        "Volc-DeepSeek-V3.2",
        "Doubao-Seed-2.0-mini",
        "Doubao-Seed-2.0-lite",
        "Doubao-Seed-2.0-pro",
        "qwen3.5-plus"
);
```

```java
@Data
public class ChatStatusVO {
    private String provider;
    private boolean configured;
    private boolean realModelAvailable;
    private boolean fallbackToMock;
    private int timeoutSeconds;
    private String model;
    private String baseUrl;
    private boolean toolReady;
    private boolean geoReady;
    private boolean embeddingReady;
    private boolean rerankReady;
    private List<String> warnings;
    private String message;
}
```

```yaml
llm:
  openai:
    tool:
      base-url: ${OPENAI_TOOL_BASE_URL:${OPENAI_BASE_URL:https://api.openai.com/v1}}
      model: ${OPENAI_TOOL_MODEL:}
      temperature: ${OPENAI_TOOL_TEMPERATURE:${OPENAI_TEMPERATURE:0.2}}
      max-output-tokens: ${OPENAI_TOOL_MAX_OUTPUT_TOKENS:512}
```

- [ ] **Step 4: 再跑测试确认转绿**

Run:

```bash
cd /d F:\dachuang\backend
mvn -q -Dtest=LlmPropertiesTest,RoutingChatServiceImplTest test
```

Expected:
- PASS，`tool` scene 和 allow-list warning 已生效。

- [ ] **Step 5: 提交这一逻辑边界**

```bash
git -C F:\dachuang add -- \
  backend/src/main/java/com/citytrip/config/LlmProperties.java \
  backend/src/main/java/com/citytrip/model/vo/ChatStatusVO.java \
  backend/src/main/java/com/citytrip/service/impl/OpenAiChatServiceImpl.java \
  backend/src/main/java/com/citytrip/service/impl/RoutingChatServiceImpl.java \
  backend/src/main/java/com/citytrip/service/impl/MockChatServiceImpl.java \
  backend/src/main/resources/application.yml \
  backend/src/test/java/com/citytrip/config/LlmPropertiesTest.java \
  backend/src/test/java/com/citytrip/service/impl/RoutingChatServiceImplTest.java
git -C F:\dachuang commit -m "feat: add vivo tool scene and readiness config"
```

---

### Task 2: 引入 vivo requestId 感知的 chat client，并接入网关门面

**Files:**
- Create: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\vivo\VivoRequestIdFactory.java`
- Create: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\vivo\VivoChatClient.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\OpenAiGatewayClient.java`
- Test: `F:\dachuang\backend\src\test\java\com\citytrip\service\impl\OpenAiGatewayClientTest.java`

- [ ] **Step 1: 写失败测试，锁定 vivo 请求必须带 requestId，且 200+error 继续失败**

```java
@Test
void requestShouldAppendRequestIdWhenCallingVivoEndpoint() throws Exception {
    AtomicReference<String> capturedQuery = new AtomicReference<>();
    try (HttpServerHandle server = HttpServerHandle.custom(exchange -> {
        capturedQuery.set(exchange.getRequestURI().getQuery());
        respondJson(exchange, """
                {"choices":[{"message":{"content":"ok","role":"assistant"}}]}
                """);
    })) {
        LlmProperties properties = new LlmProperties();
        OpenAiGatewayClient client = new OpenAiGatewayClient(properties, new ObjectMapper());
        LlmProperties.ResolvedOpenAiOptions options = new LlmProperties.ResolvedOpenAiOptions(
                server.baseUrl(), "Doubao-Seed-2.0-mini", 0.2D, 64
        );

        String answer = client.request(options, "test-key",
                List.of(new OpenAiGatewayClient.OpenAiMessage("user", "hi")));

        assertThat(answer).isEqualTo("ok");
        assertThat(capturedQuery.get()).contains("requestId=");
    }
}
```

- [ ] **Step 2: 跑测试确认先红**

Run:

```bash
cd /d F:\dachuang\backend
mvn -q -Dtest=OpenAiGatewayClientTest test
```

Expected:
- FAIL，当前实现不会自动加 `requestId`。

- [ ] **Step 3: 先写最小 vivo client 与 requestId 工厂**

```java
public final class VivoRequestIdFactory {

    public String create() {
        return UUID.randomUUID().toString();
    }
}
```

```java
public class VivoChatClient {

    public String buildEndpoint(String baseUrl, String requestId) {
        return normalize(baseUrl) + "/chat/completions?requestId=" + URLEncoder.encode(requestId, StandardCharsets.UTF_8);
    }

    public boolean isVivoBaseUrl(String baseUrl) {
        return baseUrl != null && baseUrl.contains("api-ai.vivo.com.cn");
    }
}
```

- [ ] **Step 4: 让 `OpenAiGatewayClient` 通过 vivo client 发请求**

```java
private final VivoChatClient vivoChatClient;
private final VivoRequestIdFactory vivoRequestIdFactory;

private String execute(...) {
    String requestId = vivoRequestIdFactory.create();
    String endpoint = vivoChatClient.isVivoBaseUrl(options.getBaseUrl())
            ? vivoChatClient.buildEndpoint(normalizeBaseUrl(options.getBaseUrl()), requestId)
            : normalizeBaseUrl(options.getBaseUrl()) + "/chat/completions";
    ...
}
```

同时保持：
- `200 + {"error":...}` 仍然抛 `IllegalStateException`
- 普通流式 / 非流式接口签名不变

- [ ] **Step 5: 再跑网关测试**

Run:

```bash
cd /d F:\dachuang\backend
mvn -q -Dtest=OpenAiGatewayClientTest test
```

Expected:
- PASS，vivo 请求自动带 `requestId`，原有 provider error 行为不回退。

- [ ] **Step 6: 提交 provider 网关切分**

```bash
git -C F:\dachuang add -- \
  backend/src/main/java/com/citytrip/service/impl/OpenAiGatewayClient.java \
  backend/src/main/java/com/citytrip/service/impl/vivo/VivoRequestIdFactory.java \
  backend/src/main/java/com/citytrip/service/impl/vivo/VivoChatClient.java \
  backend/src/test/java/com/citytrip/service/impl/OpenAiGatewayClientTest.java
git -C F:\dachuang commit -m "feat: add vivo request id aware chat gateway"
```

---

### Task 3: 接入 function calling 基础设施，并让聊天主链路进入 tool loop

**Files:**
- Create: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\vivo\VivoToolDefinition.java`
- Create: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\vivo\VivoToolRegistry.java`
- Create: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\vivo\VivoFunctionCallingService.java`
- Create: `F:\dachuang\backend\src\test\java\com\citytrip\service\impl\VivoFunctionCallingServiceTest.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\RealChatGatewayService.java`

- [ ] **Step 1: 写失败测试，先锁工具白名单、工具异常包装和最大轮数**

```java
class VivoFunctionCallingServiceTest {

    @Test
    void shouldRejectUnknownTool() {
        VivoFunctionCallingService service = new VivoFunctionCallingService(...);

        assertThatThrownBy(() -> service.executeToolCall("unknown_tool", "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown_tool");
    }

    @Test
    void shouldWrapToolFailureIntoStructuredResult() {
        VivoFunctionCallingService service = new VivoFunctionCallingService(...failing registry...);

        String payload = service.executeToolCall("search_poi", "{\"keyword\":\"太古里\"}");

        assertThat(payload).contains("\"status\":\"error\"");
        assertThat(payload).contains("\"tool\":\"search_poi\"");
    }
}
```

- [ ] **Step 2: 运行测试，确认红**

Run:

```bash
cd /d F:\dachuang\backend
mvn -q -Dtest=VivoFunctionCallingServiceTest test
```

Expected:
- FAIL，说明 function calling 基础设施尚不存在。

- [ ] **Step 3: 先写工具定义与注册表骨架**

```java
public record VivoToolDefinition(
        String name,
        String description,
        String parametersJsonSchema,
        Function<String, String> executor
) {}
```

```java
public class VivoToolRegistry {

    private final Map<String, VivoToolDefinition> tools = new LinkedHashMap<>();

    public Optional<VivoToolDefinition> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public List<VivoToolDefinition> list() {
        return new ArrayList<>(tools.values());
    }
}
```

- [ ] **Step 4: 实现 tool loop 服务**

```java
public class VivoFunctionCallingService {

    public String executeToolCall(String toolName, String argumentsJson) {
        VivoToolDefinition definition = registry.find(toolName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown tool: " + toolName));
        try {
            return definition.executor().apply(argumentsJson);
        } catch (Exception ex) {
            return "{\"tool\":\"" + toolName + "\",\"status\":\"error\",\"message\":\"tool temporarily unavailable\"}";
        }
    }
}
```

并固定：
- 最多 3 个工具调用
- 最多 2 轮模型往返
- 工具无结果统一返回 `NO_RESULT`

- [ ] **Step 5: 把 tool loop 接入 `RealChatGatewayService`**

```java
String answer = openAiGatewayClient.request(...);
if (functionCallingService.shouldEnterToolLoop(answer)) {
    ToolLoopResult toolLoopResult = functionCallingService.runToolLoop(req, answer, chatOptions, llmProperties.getOpenai().getApiKey());
    answer = toolLoopResult.finalAnswer();
}
```

第一批工具只注册：
- `search_poi`
- `search_community_posts`
- `get_route_context`

- [ ] **Step 6: 再跑 function calling 测试**

Run:

```bash
cd /d F:\dachuang\backend
mvn -q -Dtest=VivoFunctionCallingServiceTest test
```

Expected:
- PASS，未知工具被拒绝，工具异常被包装。

- [ ] **Step 7: 提交聊天工具链**

```bash
git -C F:\dachuang add -- \
  backend/src/main/java/com/citytrip/service/impl/RealChatGatewayService.java \
  backend/src/main/java/com/citytrip/service/impl/vivo/VivoToolDefinition.java \
  backend/src/main/java/com/citytrip/service/impl/vivo/VivoToolRegistry.java \
  backend/src/main/java/com/citytrip/service/impl/vivo/VivoFunctionCallingService.java \
  backend/src/test/java/com/citytrip/service/impl/VivoFunctionCallingServiceTest.java
git -C F:\dachuang commit -m "feat: add vivo function calling tool loop"
```

---

### Task 4: 正式化 POI 搜索接口，并收正 GEO 参数

**Files:**
- Create: `F:\dachuang\backend\src\main\java\com\citytrip\model\vo\PoiSearchResultVO.java`
- Create: `F:\dachuang\backend\src\test\java\com\citytrip\controller\PoiControllerTest.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\PoiService.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\PoiServiceImpl.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\geo\impl\GeoSearchServiceImpl.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\controller\PoiController.java`
- Modify: `F:\dachuang\frontend\src\api\poi.js`
- Test: `F:\dachuang\backend\src\test\java\com\citytrip\service\geo\impl\GeoSearchServiceImplTest.java`

- [ ] **Step 1: 先写失败测试，锁 GEO 参数和控制器契约**

```java
@Test
void searchByKeyword_shouldUseOfficialVivoQueryShape() {
    ...
    server.expect(requestTo(allOf(
            startsWith("https://api-ai.vivo.com.cn/search/geo?"),
            containsString("keywords=%E5%A4%AA%E5%8F%A4%E9%87%8C"),
            containsString("page_num=1"),
            containsString("page_size=8"),
            containsString("requestId=")
    )));
}
```

```java
@WebMvcTest(PoiController.class)
class PoiControllerTest {

    @Test
    void searchEndpointShouldReturnLivePoiShape() throws Exception {
        when(poiService.searchLive(anyString(), anyString(), anyInt())).thenReturn(List.of(
                new PoiSearchResultVO("成都远洋太古里", "锦江区中纱帽街8号", "商场",
                        new BigDecimal("30.655"), new BigDecimal("104.083"), "成都市", "510100", "vivo-geo")
        ));

        mockMvc.perform(get("/api/pois/search").param("keyword", "太古里").param("city", "成都"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("成都远洋太古里"))
                .andExpect(jsonPath("$[0].source").value("vivo-geo"));
    }
}
```

- [ ] **Step 2: 跑测试确认红**

Run:

```bash
cd /d F:\dachuang\backend
mvn -q -Dtest=GeoSearchServiceImplTest,PoiControllerTest test
```

Expected:
- FAIL，当前 query 仍带旧参数形态，且控制器没有 `/search`。

- [ ] **Step 3: 最小实现 `PoiSearchResultVO` 与 controller 入口**

```java
public interface PoiService extends IService<Poi> {
    Poi getDetailWithStatus(Long id, LocalDate tripDate);
    List<Poi> enrichOperatingStatus(List<Poi> pois, LocalDate tripDate);
    List<PoiSearchResultVO> searchLive(String keyword, String city, int limit);
}
```

```java
public class PoiSearchResultVO {
    private String name;
    private String address;
    private String category;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String cityName;
    private String adcode;
    private String source;
}
```

```java
@GetMapping("/search")
public List<PoiSearchResultVO> search(@RequestParam("keyword") String keyword,
                                      @RequestParam(value = "city", required = false) String city,
                                      @RequestParam(value = "limit", defaultValue = "8") Integer limit) {
    return poiService.searchLive(keyword, city, limit == null ? 8 : limit);
}
```

- [ ] **Step 4: 把 `GeoSearchServiceImpl` 收正为官方参数**

```java
private MultiValueMap<String, String> buildBaseQuery(String cityName, int limit) {
    MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
    if (StringUtils.hasText(cityName)) {
        query.add("city", cityName.trim());
    } else if (StringUtils.hasText(geoSearchProperties.getDefaultCityName())) {
        query.add("city", geoSearchProperties.getDefaultCityName().trim());
    }
    query.add("page_num", "1");
    query.add("page_size", String.valueOf(Math.min(limit, 15)));
    query.add("requestId", UUID.randomUUID().toString());
    return query;
}
```

关键约束：
- 仅以 `/search/geo` 作为 live endpoint
- `routePath` 继续保持空，不再假装支持导航
- 失败时回退本地模糊匹配，最终最多返回空数组

并在 `PoiServiceImpl` 中新增最小搜索实现：

```java
@Override
public List<PoiSearchResultVO> searchLive(String keyword, String city, int limit) {
    List<GeoPoiCandidate> live = geoSearchService.searchByKeyword(keyword, city, limit);
    if (!live.isEmpty()) {
        return live.stream().map(this::toPoiSearchResult).toList();
    }
    return lambdaQuery()
            .like(Poi::getName, keyword)
            .last("limit " + Math.max(1, Math.min(limit, 15)))
            .list()
            .stream()
            .map(this::toPoiSearchResult)
            .toList();
}
```

- [ ] **Step 5: 给前端新增 live 搜索 API**

```javascript
export function reqSearchPoi(keyword, city, limit = 8) {
  return request({
    url: '/api/pois/search',
    method: 'get',
    params: { keyword, city, limit }
  })
}
```

- [ ] **Step 6: 再跑 GEO + controller 测试**

Run:

```bash
cd /d F:\dachuang\backend
mvn -q -Dtest=GeoSearchServiceImplTest,PoiControllerTest test
```

Expected:
- PASS，query 形态符合 vivo 1736，`/api/pois/search` 返回稳定结构。

- [ ] **Step 7: 提交 POI live 能力**

```bash
git -C F:\dachuang add -- \
  backend/src/main/java/com/citytrip/service/PoiService.java \
  backend/src/main/java/com/citytrip/service/impl/PoiServiceImpl.java \
  backend/src/main/java/com/citytrip/service/geo/impl/GeoSearchServiceImpl.java \
  backend/src/main/java/com/citytrip/controller/PoiController.java \
  backend/src/main/java/com/citytrip/model/vo/PoiSearchResultVO.java \
  backend/src/test/java/com/citytrip/service/geo/impl/GeoSearchServiceImplTest.java \
  backend/src/test/java/com/citytrip/controller/PoiControllerTest.java \
  frontend/src/api/poi.js
git -C F:\dachuang commit -m "feat: add live poi search with vivo geo"
```

---

### Task 5: 新增 embedding / rerank provider client 与社区语义排序服务

**Files:**
- Create: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\vivo\VivoEmbeddingClient.java`
- Create: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\vivo\VivoRerankClient.java`
- Create: `F:\dachuang\backend\src\main\java\com\citytrip\service\application\community\CommunitySemanticSearchService.java`
- Create: `F:\dachuang\backend\src\test\java\com\citytrip\service\application\community\CommunitySemanticSearchServiceTest.java`

- [ ] **Step 1: 先写失败测试，锁粗排 / 精排 / 降级**

```java
class CommunitySemanticSearchServiceTest {

    @Test
    void shouldUseRerankScoreAsPrimarySignal() {
        CommunitySemanticSearchService service = new CommunitySemanticSearchService(fakeEmbedding(), fakeRerank());

        List<ScoredCommunityCandidate> ranked = service.rank(
                "适合情侣夜游散步",
                List.of(candidate("A", 0.50), candidate("B", 0.40))
        );

        assertThat(ranked.get(0).id()).isEqualTo("B");
    }

    @Test
    void shouldFallbackToEmbeddingWhenRerankFails() {
        CommunitySemanticSearchService service = new CommunitySemanticSearchService(fakeEmbedding(), failingRerank());

        List<ScoredCommunityCandidate> ranked = service.rank("夜游拍照", List.of(candidate("A", 0.91), candidate("B", 0.70)));

        assertThat(ranked.get(0).id()).isEqualTo("A");
    }
}
```

- [ ] **Step 2: 跑测试确认红**

Run:

```bash
cd /d F:\dachuang\backend
mvn -q -Dtest=CommunitySemanticSearchServiceTest test
```

Expected:
- FAIL，语义排序服务与 client 尚不存在。

- [ ] **Step 3: 写最小 provider client 骨架**

```java
public class VivoEmbeddingClient {

    public List<List<Double>> embed(String modelName, List<String> sentences) {
        // POST /embedding-model-api/predict/batch?requestId=...
        return List.of();
    }
}
```

```java
public class VivoRerankClient {

    public List<Double> rerank(String query, List<String> sentences) {
        // POST /rerank?requestId=...
        return List.of();
    }
}
```

- [ ] **Step 4: 实现 `CommunitySemanticSearchService`**

```java
public List<ScoredCommunityCandidate> rank(String query, List<CommunitySemanticCandidate> candidates) {
    List<ScoredCommunityCandidate> coarse = embedAndScore(query, candidates);
    List<ScoredCommunityCandidate> topN = coarse.stream().limit(30).toList();
    try {
        return rerankAndMerge(query, topN);
    } catch (Exception ex) {
        return topN;
    }
}

private double finalScore(double rerankScore, double cosineScoreNormalized) {
    return rerankScore * 0.75D + cosineScoreNormalized * 0.25D;
}
```

缓存规则：
- key = `sha256(text) + ":" + updatedAt`
- TTL = 24h

- [ ] **Step 5: 再跑语义服务测试**

Run:

```bash
cd /d F:\dachuang\backend
mvn -q -Dtest=CommunitySemanticSearchServiceTest test
```

Expected:
- PASS，rerank 主导排序，失败时回退 embedding。

- [ ] **Step 6: 提交语义服务底座**

```bash
git -C F:\dachuang add -- \
  backend/src/main/java/com/citytrip/service/impl/vivo/VivoEmbeddingClient.java \
  backend/src/main/java/com/citytrip/service/impl/vivo/VivoRerankClient.java \
  backend/src/main/java/com/citytrip/service/application/community/CommunitySemanticSearchService.java \
  backend/src/test/java/com/citytrip/service/application/community/CommunitySemanticSearchServiceTest.java
git -C F:\dachuang commit -m "feat: add vivo embedding and rerank semantic service"
```

---

### Task 6: 把社区列表接入 semantic pipeline，并保持 pinned / theme 逻辑不变

**Files:**
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\application\community\CommunityItineraryQueryService.java`
- Modify: `F:\dachuang\backend\src\test\java\com\citytrip\service\application\community\CommunityItineraryQueryServiceTest.java`

- [ ] **Step 1: 写失败测试，先锁接入条件**

```java
@Test
void listPublicShouldUseSemanticRankingWhenKeywordPresent() throws Exception {
    CommunitySemanticSearchService semanticSearchService = mock(CommunitySemanticSearchService.class);
    CommunityItineraryQueryService service = new CommunityItineraryQueryService(
            repository, commentMapper, likeMapper, userMapper, codec,
            new ItinerarySummaryAssembler(), cacheService, semanticSearchService
    );

    when(semanticSearchService.rank(anyString(), anyList())).thenReturn(List.of(
            new ScoredCommunityCandidate(2L, 0.98D),
            new ScoredCommunityCandidate(1L, 0.87D)
    ));

    CommunityItineraryPageVO page = service.listPublic(1, 12, "latest", "适合情侣夜游散步", null, null);

    assertThat(page.getRecords()).extracting(CommunityItineraryVO::getId).containsExactly(2L, 1L);
}
```

- [ ] **Step 2: 跑测试确认红**

Run:

```bash
cd /d F:\dachuang\backend
mvn -q -Dtest=CommunityItineraryQueryServiceTest test
```

Expected:
- FAIL，当前服务没有 semantic 排序入口。

- [ ] **Step 3: 最小接入 semantic 排序**

```java
if (StringUtils.hasText(normalizedKeyword) && communitySemanticSearchService != null) {
    feedRecords = applySemanticRanking(normalizedKeyword, feedRecords);
}
```

```java
private List<CommunityItineraryVO> applySemanticRanking(String keyword, List<CommunityItineraryVO> records) {
    List<CommunitySemanticCandidate> candidates = records.stream()
            .map(this::toSemanticCandidate)
            .toList();
    Map<Long, Double> scoreMap = communitySemanticSearchService.rank(keyword, candidates).stream()
            .collect(Collectors.toMap(ScoredCommunityCandidate::id, ScoredCommunityCandidate::score));
    return records.stream()
            .sorted(Comparator.comparing((CommunityItineraryVO item) -> scoreMap.getOrDefault(item.getId(), -1D)).reversed())
            .toList();
}
```

约束：
- `theme` 过滤先执行
- `pinnedRecords` 逻辑完全不动
- semantic 失败时回到原排序

- [ ] **Step 4: 再跑社区查询测试**

Run:

```bash
cd /d F:\dachuang\backend
mvn -q -Dtest=CommunityItineraryQueryServiceTest test
```

Expected:
- PASS，`keyword` 非空时进入 semantic pipeline，pinned 不受影响。

- [ ] **Step 5: 提交社区语义接入**

```bash
git -C F:\dachuang add -- \
  backend/src/main/java/com/citytrip/service/application/community/CommunityItineraryQueryService.java \
  backend/src/test/java/com/citytrip/service/application/community/CommunityItineraryQueryServiceTest.java
git -C F:\dachuang commit -m "feat: enable semantic ranking for community search"
```

---

### Task 7: 前端最小接线与全链路验证

**Files:**
- Modify: `F:\dachuang\frontend\src\api\chat.js`
- Modify: `F:\dachuang\frontend\src\api\poi.js`
- Verify only: `F:\dachuang\frontend\src\views\Community.vue`
- Verify only: `F:\dachuang\frontend\src\views\Result.vue`

- [ ] **Step 1: 补 chat status 兼容字段读取**

```javascript
export function reqGetChatStatus() {
  return request({
    url: '/api/chat/messages/status',
    method: 'get',
    skipErrorMessage: true
  }).then(payload => ({
    ...payload,
    toolReady: Boolean(payload?.toolReady),
    geoReady: Boolean(payload?.geoReady),
    embeddingReady: Boolean(payload?.embeddingReady),
    rerankReady: Boolean(payload?.rerankReady),
    warnings: Array.isArray(payload?.warnings) ? payload.warnings : []
  }))
}
```

- [ ] **Step 2: 补 POI 搜索 API 封装**

```javascript
export function reqSearchPoi(keyword, city, limit = 8) {
  return request({
    url: '/api/pois/search',
    method: 'get',
    params: { keyword, city, limit }
  })
}
```

- [ ] **Step 3: 跑后端核心测试集**

Run:

```bash
cd /d F:\dachuang\backend
mvn -q -Dtest=LlmPropertiesTest,RoutingChatServiceImplTest,OpenAiGatewayClientTest,VivoFunctionCallingServiceTest,GeoSearchServiceImplTest,PoiControllerTest,CommunitySemanticSearchServiceTest,CommunityItineraryQueryServiceTest test
```

Expected:
- PASS，7 组测试全部通过。

- [ ] **Step 4: 跑前端构建**

Run:

```bash
cd /d F:\dachuang\frontend
npm run build
```

Expected:
- exit code 0，现有路线页和社区页未因 API 封装调整而编译失败。

- [ ] **Step 5: 做 4 条手工回归**

Manual checks:
1. 访问 `/api/chat/messages/status`，确认有 `toolReady/geoReady/embeddingReady/rerankReady`
2. 调 `/api/pois/search?keyword=太古里&city=成都`，确认返回 `PoiSearchResultVO`
3. 通过聊天问“太古里附近晚上还能去哪”，确认 `search_poi` 触发并返回基于实时结果的回答
4. 在社区搜索输入“适合情侣夜游散步”，确认结果顺序与纯 keyword contains 有差异

- [ ] **Step 6: 提交最小前端接线与最终验证**

```bash
git -C F:\dachuang add -- \
  frontend/src/api/chat.js \
  frontend/src/api/poi.js
git -C F:\dachuang commit -m "feat: wire vivo readiness and poi search apis"
```

---

## 自检清单

- [ ] spec 里的 4 个第一优先能力在任务中均有落实
- [ ] 没有任何任务把 vivo `/search/geo` 写成导航能力
- [ ] 没有任务触及社区页面视觉重构
- [ ] 每个新增能力都有最少 1 个专门测试类
- [ ] 社区语义搜索存在明确降级路径
- [ ] 计划中的命令都使用绝对路径工作区 `F:\dachuang`

---

## 完成定义

只有当以下条件同时满足，才允许宣称这一计划执行完成：

1. 后端 7 组目标测试全部绿色
2. 前端 `npm run build` 通过
3. `/api/chat/messages/status` readiness 字段完整
4. `/api/pois/search` 可返回 live 结果或空数组回退
5. 聊天能真实触发至少一条 tool loop
6. 社区 `keyword` 搜索已进入 semantic pipeline
7. 现有路线页 / 社区页视觉没有回退
