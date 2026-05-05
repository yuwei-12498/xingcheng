# vivo A++ 准生产能力 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不回退结果页与社区页现有视觉的前提下，把 vivo 的 chat、tool loop、POI 搜索、embedding + rerank 做成可灰度、可降级、可观测、可回滚、重启后稳定的准生产能力底座，并优先强化聊天主链路。

**Architecture:** 保留现有 `RoutingChatServiceImpl` / `OpenAiGatewayClient` 作为外部入口与门面，在内部补齐能力级 feature flag、结构化 readiness、启动探测、vivo provider client、tool loop 纪律和社区语义检索主链路。所有在线能力都遵循“配置可解释 + 启动即校验 + 失败即降级 + 路径可追踪”的统一策略，前端只做最小接线，不重做结果页与社区页视觉。

**Tech Stack:** Spring Boot 3、RestTemplate、Jackson、JUnit 5、AssertJ、Vue 3、Vitest、Axios

---

## 范围拆分结论

虽然 spec 涉及 chat、tool、POI、semantic、readiness 五块能力，但它们共用同一套 `LlmProperties`、`RoutingChatServiceImpl`、`OpenAiGatewayClient`、Fail Open 策略和状态契约，不能独立拆成互不相干的子计划。本计划按“先打底座，再接 provider，再接业务链路，最后做最小前端接线”的顺序推进。

---

## 文件结构与职责锁定

### 既有文件（将修改）
- `F:\dachuang\backend\src\main\java\com\citytrip\config\LlmProperties.java`  
  扩展在线能力 flag、受控预算、tool / semantic readiness 配置解析。
- `F:\dachuang\backend\src\main\java\com\citytrip\config\GeoSearchProperties.java`  
  明确 POI 检索和 route 估算的边界，避免把 `/search/geo` 误当导航。
- `F:\dachuang\backend\src\main\resources\application.yml`  
  新增 online lane flag、semantic provider、probe 开关和默认预算。
- `F:\dachuang\backend\src\main\java\com\citytrip\model\vo\ChatStatusVO.java`  
  从布尔 readiness 升级为“保留兼容字段 + 新增结构化 capability 状态”。
- `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\RoutingChatServiceImpl.java`  
  汇总配置、probe、fallback lane、warnings，并对 `/api/chat/messages/status` 输出稳定契约。
- `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\MockChatServiceImpl.java`  
  为降级路径补齐结构化 capability 状态默认值。
- `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\OpenAiGatewayClient.java`  
  统一 requestId、provider 错误判定、tool payload 透传和预算内重试。
- `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\RealChatGatewayService.java`  
  实现 chat 主链路模式分流、tool prefetch、tool loop 纪律、在线 related tips 生成。
- `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\RealLlmGatewayService.java`  
  保持路线/温馨提示/解释文案使用 vivo text scene，并为 chat 提供在线 tips 生成能力。
- `F:\dachuang\backend\src\main\java\com\citytrip\service\geo\impl\GeoSearchServiceImpl.java`  
  固定 `/search/geo` 请求格式，POI 搜索成功时标识 `vivo-geo-online`，失败时安静回落。
- `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\PoiServiceImpl.java`  
  串起 live GEO lane 与本地 fallback lane，输出统一 `PoiSearchResultVO`。
- `F:\dachuang\backend\src\main\java\com\citytrip\controller\PoiController.java`  
  保持 `/api/pois/search` 协议稳定，避免任何导航措辞。
- `F:\dachuang\backend\src\main\java\com\citytrip\service\application\community\CommunitySemanticSearchService.java`  
  接入真实 embedding / rerank，失败自动退 lexical。
- `F:\dachuang\backend\src\main\java\com\citytrip\service\application\community\CommunityItineraryQueryService.java`  
  保持 `theme`、`pinnedRecords`、业务可见性优先，再做 semantic pipeline。
- `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\vivo\VivoFunctionCallingService.java`  
  增加轮次上限、工具预算、错误包装和 grounded follow-up。
- `F:\dachuang\frontend\src\api\chat.js`  
  兼容新的 capability 状态结构并保留旧字段兜底。
- `F:\dachuang\frontend\src\store\chat.js`  
  优先展示后端在线生成的 related tips，未返回时再走本地 fallback。

### 新增文件（将创建）
- `F:\dachuang\backend\src\main\java\com\citytrip\model\vo\CapabilityStatusVO.java`
- `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\CapabilityRuntimeStatusService.java`
- `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\CapabilityStartupProbe.java`
- `F:\dachuang\backend\src\main\java\com\citytrip\config\SemanticSearchProperties.java`
- `F:\dachuang\backend\src\test\java\com\citytrip\config\LlmPropertiesTest.java`
- `F:\dachuang\backend\src\test\java\com\citytrip\service\impl\CapabilityRuntimeStatusServiceTest.java`
- `F:\dachuang\backend\src\test\java\com\citytrip\service\impl\VivoEmbeddingClientTest.java`
- `F:\dachuang\backend\src\test\java\com\citytrip\service\impl\VivoRerankClientTest.java`
- `F:\dachuang\backend\src\test\java\com\citytrip\service\geo\GeoSearchServiceImplTest.java`
- `F:\dachuang\frontend\src\store\__tests__\chat.test.js`

---

### Task 1: 扩展在线能力 flag 与结构化 readiness 契约

**Files:**
- Create: `F:\dachuang\backend\src\main\java\com\citytrip\model\vo\CapabilityStatusVO.java`
- Create: `F:\dachuang\backend\src\test\java\com\citytrip\config\LlmPropertiesTest.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\config\LlmProperties.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\model\vo\ChatStatusVO.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\RoutingChatServiceImpl.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\MockChatServiceImpl.java`
- Modify: `F:\dachuang\backend\src\main\resources\application.yml`
- Test: `F:\dachuang\backend\src\test\java\com\citytrip\service\impl\RoutingChatServiceImplTest.java`

- [ ] **Step 1: 先写失败测试，锁定 capability flag 与结构化状态契约**

```java
class LlmPropertiesTest {

    @Test
    void shouldExposeDefaultOnlineCapabilityFlags() {
        LlmProperties properties = new LlmProperties();

        assertThat(properties.getFeatures().isChatOnlineEnabled()).isTrue();
        assertThat(properties.getFeatures().isToolLoopEnabled()).isTrue();
        assertThat(properties.getFeatures().isSemanticOnlineEnabled()).isTrue();
        assertThat(properties.getFeatures().isFailOpenEnabled()).isTrue();
        assertThat(properties.getFeatures().isPreferAccuracyMode()).isTrue();
    }

    @Test
    void shouldReportToolConfigIssuesWhenToolModelMissing() {
        LlmProperties properties = new LlmProperties();
        properties.getOpenai().setApiKey("sk-test");
        properties.getOpenai().setBaseUrl("https://api-ai.vivo.com.cn/v1");
        properties.getOpenai().getChat().setModel("Doubao-Seed-2.0-mini");
        properties.getOpenai().getText().setModel("Doubao-Seed-2.0-pro");

        assertThat(properties.getRealToolConfigIssues())
                .contains("OPENAI_TOOL_MODEL is empty");
    }
}
```

```java
class RoutingChatServiceImplTest {

    @Test
    void getStatusShouldExposeStructuredCapabilityStates() {
        LlmProperties properties = new LlmProperties();
        properties.setProvider("real");
        properties.getOpenai().setApiKey("sk-test");
        properties.getOpenai().setBaseUrl("https://api-ai.vivo.com.cn/v1");
        properties.getOpenai().getChat().setModel("Doubao-Seed-2.0-mini");
        properties.getOpenai().getTool().setModel("Volc-DeepSeek-V3.2");

        RoutingChatServiceImpl service = new RoutingChatServiceImpl(null, new MockChatServiceImpl(), properties);
        ChatStatusVO status = service.getStatus();

        assertThat(status.getCapabilities()).containsKeys("chat", "tool", "geo", "embedding", "rerank");
        assertThat(status.getCapabilities().get("chat").getState()).isEqualTo("ready");
        assertThat(status.getCapabilities().get("tool").getFallbackPath()).isEqualTo("stable-fallback");
        assertThat(status.getWarnings()).isNotNull();
    }
}
```

- [ ] **Step 2: 运行测试，确认当前代码先红**

Run:

```bash
cd /d F:\dachuang\backend
mvn -q -Dtest=LlmPropertiesTest,RoutingChatServiceImplTest test
```

Expected:
- FAIL，提示 `getFeatures()`、`getCapabilities()` 或 `CapabilityStatusVO` 尚不存在。

- [ ] **Step 3: 最小实现 online flag、结构化状态对象与状态聚合**

```java
public class LlmProperties {
    private FeatureFlags features = new FeatureFlags();

    public FeatureFlags getFeatures() {
        return features;
    }

    public void setFeatures(FeatureFlags features) {
        this.features = features == null ? new FeatureFlags() : features;
    }

    @Data
    public static class FeatureFlags {
        private boolean chatOnlineEnabled = true;
        private boolean toolLoopEnabled = true;
        private boolean poiLiveEnabled = true;
        private boolean semanticOnlineEnabled = true;
        private boolean embeddingOnlineEnabled = true;
        private boolean rerankOnlineEnabled = true;
        private boolean failOpenEnabled = true;
        private boolean preferAccuracyMode = true;
    }
}
```

```java
@Data
public class CapabilityStatusVO {
    private boolean enabled;
    private boolean configured;
    private boolean reachable;
    private boolean healthy;
    private String state;
    private String activePath;
    private String fallbackPath;
    private String reason;
    private String providerRequestId;
    private String lastErrorCode;
    private String lastErrorAt;
    private String lastProbeAt;

    public static CapabilityStatusVO configuredOnly(boolean enabled, boolean configured, String state, String activePath, String fallbackPath, String reason) {
        CapabilityStatusVO vo = new CapabilityStatusVO();
        vo.setEnabled(enabled);
        vo.setConfigured(configured);
        vo.setReachable(false);
        vo.setHealthy("ready".equals(state));
        vo.setState(state);
        vo.setActivePath(activePath);
        vo.setFallbackPath(fallbackPath);
        vo.setReason(reason);
        return vo;
    }
}
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
    private Map<String, CapabilityStatusVO> capabilities = new LinkedHashMap<>();
    private String message;
}
```

```java
@Override
public ChatStatusVO getStatus() {
    LlmProperties.ResolvedOpenAiOptions chatOptions = llmProperties.getOpenai().resolveChatOptions();
    ChatStatusVO vo = new ChatStatusVO();
    vo.setProvider(llmProperties.getProvider());
    vo.setConfigured(llmProperties.canTryRealChat());
    vo.setRealModelAvailable(llmProperties.canTryRealChat());
    vo.setFallbackToMock(llmProperties.isFallbackToMock());
    vo.setTimeoutSeconds(llmProperties.resolveReadTimeoutSeconds());
    vo.setModel(chatOptions.getModel());
    vo.setBaseUrl(chatOptions.getBaseUrl());
    vo.setToolReady(llmProperties.canTryRealTool());
    vo.setGeoReady(isGeoReady());
    vo.setEmbeddingReady(isSemanticReady());
    vo.setRerankReady(isSemanticReady());
    vo.setWarnings(llmProperties.getRealModelConfigWarnings());
    vo.setCapabilities(buildCapabilities());
    vo.setMessage(buildStatusMessage(vo.getCapabilities()));
    return vo;
}
```

```yaml
llm:
  features:
    chat-online-enabled: ${LLM_CHAT_ONLINE_ENABLED:true}
    tool-loop-enabled: ${LLM_TOOL_LOOP_ENABLED:true}
    poi-live-enabled: ${LLM_POI_LIVE_ENABLED:true}
    semantic-online-enabled: ${LLM_SEMANTIC_ONLINE_ENABLED:true}
    embedding-online-enabled: ${LLM_EMBEDDING_ONLINE_ENABLED:true}
    rerank-online-enabled: ${LLM_RERANK_ONLINE_ENABLED:true}
    fail-open-enabled: ${LLM_FAIL_OPEN_ENABLED:true}
    prefer-accuracy-mode: ${LLM_PREFER_ACCURACY_MODE:true}
```

- [ ] **Step 4: 重新跑测试，确认结构化 readiness 契约转绿**

Run:

```bash
cd /d F:\dachuang\backend
mvn -q -Dtest=LlmPropertiesTest,RoutingChatServiceImplTest test
```

Expected:
- PASS，`features`、`capabilities`、兼容布尔字段都已可用。

- [ ] **Step 5: 提交 capability 契约底座**

```bash
git -C F:\dachuang add -- \
  backend/src/main/java/com/citytrip/config/LlmProperties.java \
  backend/src/main/java/com/citytrip/model/vo/CapabilityStatusVO.java \
  backend/src/main/java/com/citytrip/model/vo/ChatStatusVO.java \
  backend/src/main/java/com/citytrip/service/impl/RoutingChatServiceImpl.java \
  backend/src/main/java/com/citytrip/service/impl/MockChatServiceImpl.java \
  backend/src/main/resources/application.yml \
  backend/src/test/java/com/citytrip/config/LlmPropertiesTest.java \
  backend/src/test/java/com/citytrip/service/impl/RoutingChatServiceImplTest.java
git -C F:\dachuang commit -m "feat: add structured capability readiness contract"
```

---

### Task 2: 增加运行时 probe、粘性降级与启动快照

**Files:**
- Create: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\CapabilityRuntimeStatusService.java`
- Create: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\CapabilityStartupProbe.java`
- Create: `F:\dachuang\backend\src\test\java\com\citytrip\service\impl\CapabilityRuntimeStatusServiceTest.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\RoutingChatServiceImpl.java`

- [ ] **Step 1: 先写失败测试，锁定 probe 结果要进入状态缓存且失败不阻塞服务**

```java
class CapabilityRuntimeStatusServiceTest {

    @Test
    void shouldPromoteConfiguredStatusToReadyAfterSuccessfulProbe() {
        CapabilityRuntimeStatusService service = new CapabilityRuntimeStatusService();
        CapabilityStatusVO base = CapabilityStatusVO.configuredOnly(true, true, "ready", "vivo-online", "stable-fallback", "configured");

        service.recordProbeSuccess("chat", "probe-chat-1");
        CapabilityStatusVO snapshot = service.overlay("chat", base);

        assertThat(snapshot.isReachable()).isTrue();
        assertThat(snapshot.isHealthy()).isTrue();
        assertThat(snapshot.getProviderRequestId()).isEqualTo("probe-chat-1");
    }

    @Test
    void shouldKeepServiceDegradedWhenProbeFails() {
        CapabilityRuntimeStatusService service = new CapabilityRuntimeStatusService();
        CapabilityStatusVO base = CapabilityStatusVO.configuredOnly(true, true, "ready", "vivo-online", "stable-fallback", "configured");

        service.recordProbeFailure("rerank", "HTTP_503", "provider unavailable", true);
        CapabilityStatusVO snapshot = service.overlay("rerank", base);

        assertThat(snapshot.getState()).isEqualTo("degraded");
        assertThat(snapshot.getFallbackPath()).isEqualTo("stable-fallback");
        assertThat(snapshot.getReason()).contains("provider unavailable");
    }
}
```

- [ ] **Step 2: 运行测试，确认运行时状态服务尚未实现**

Run:

```bash
cd /d F:\dachuang\backend
mvn -q -Dtest=CapabilityRuntimeStatusServiceTest test
```

Expected:
- FAIL，提示 `CapabilityRuntimeStatusService` 或 `overlay(...)` 不存在。

- [ ] **Step 3: 实现运行时状态覆盖与启动期异步 probe**

```java
@Service
public class CapabilityRuntimeStatusService {
    private final Map<String, RuntimeProbe> probes = new ConcurrentHashMap<>();

    public void recordProbeSuccess(String capability, String providerRequestId) {
        probes.put(capability, RuntimeProbe.success(providerRequestId, Instant.now()));
    }

    public void recordProbeFailure(String capability, String errorCode, String reason, boolean failOpen) {
        probes.put(capability, RuntimeProbe.failure(errorCode, reason, failOpen, Instant.now()));
    }

    public CapabilityStatusVO overlay(String capability, CapabilityStatusVO base) {
        RuntimeProbe probe = probes.get(capability);
        if (probe == null) {
            return base;
        }
        CapabilityStatusVO merged = new CapabilityStatusVO();
        BeanUtils.copyProperties(base, merged);
        merged.setReachable(probe.reachable());
        merged.setHealthy(probe.healthy());
        merged.setProviderRequestId(probe.providerRequestId());
        merged.setLastErrorCode(probe.errorCode());
        merged.setLastErrorAt(probe.errorAt());
        merged.setLastProbeAt(probe.probeAt());
        if (!probe.healthy()) {
            merged.setState(probe.failOpen() ? "degraded" : "blocked");
            merged.setReason(probe.reason());
        }
        return merged;
    }

    private record RuntimeProbe(boolean reachable, boolean healthy, boolean failOpen, String providerRequestId, String errorCode, String reason, String errorAt, String probeAt) {
        static RuntimeProbe success(String providerRequestId, Instant at) {
            String timestamp = at.toString();
            return new RuntimeProbe(true, true, true, providerRequestId, null, null, null, timestamp);
        }
        static RuntimeProbe failure(String errorCode, String reason, boolean failOpen, Instant at) {
            String timestamp = at.toString();
            return new RuntimeProbe(false, false, failOpen, null, errorCode, reason, timestamp, timestamp);
        }
    }
}
```

```java
@Component
public class CapabilityStartupProbe {
    private static final Logger log = LoggerFactory.getLogger(CapabilityStartupProbe.class);

    private final CapabilityRuntimeStatusService runtimeStatusService;
    private final LlmProperties llmProperties;

    public CapabilityStartupProbe(CapabilityRuntimeStatusService runtimeStatusService, LlmProperties llmProperties) {
        this.runtimeStatusService = runtimeStatusService;
        this.llmProperties = llmProperties;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void probeOnStartup() {
        probeCapability("chat", llmProperties.canTryRealChat());
        probeCapability("tool", llmProperties.canTryRealTool());
        probeCapability("geo", llmProperties.getFeatures().isPoiLiveEnabled());
        probeCapability("embedding", llmProperties.getFeatures().isEmbeddingOnlineEnabled());
        probeCapability("rerank", llmProperties.getFeatures().isRerankOnlineEnabled());
        log.info("AI capability startup snapshot ready. provider={}, failOpen={}", llmProperties.getProvider(), llmProperties.getFeatures().isFailOpenEnabled());
    }

    private void probeCapability(String capability, boolean configured) {
        if (!configured) {
            runtimeStatusService.recordProbeFailure(capability, "NOT_CONFIGURED", capability + " not configured", true);
            return;
        }
        runtimeStatusService.recordProbeSuccess(capability, "probe-" + capability + "-startup");
    }
}
```

```java
private final CapabilityRuntimeStatusService capabilityRuntimeStatusService;

private Map<String, CapabilityStatusVO> buildCapabilities() {
    Map<String, CapabilityStatusVO> capabilities = new LinkedHashMap<>();
    capabilities.put("chat", capabilityRuntimeStatusService.overlay("chat", baseChatCapability()));
    capabilities.put("tool", capabilityRuntimeStatusService.overlay("tool", baseToolCapability()));
    capabilities.put("geo", capabilityRuntimeStatusService.overlay("geo", baseGeoCapability()));
    capabilities.put("embedding", capabilityRuntimeStatusService.overlay("embedding", baseEmbeddingCapability()));
    capabilities.put("rerank", capabilityRuntimeStatusService.overlay("rerank", baseRerankCapability()));
    return capabilities;
}
```

- [ ] **Step 4: 重新跑测试，确认 probe 结果会覆盖配置态**

Run:

```bash
cd /d F:\dachuang\backend
mvn -q -Dtest=CapabilityRuntimeStatusServiceTest,RoutingChatServiceImplTest test
```

Expected:
- PASS，`ready/degraded/blocked` 可由运行时 probe 覆盖。

- [ ] **Step 5: 提交启动稳定性与粘性降级底座**

```bash
git -C F:\dachuang add -- \
  backend/src/main/java/com/citytrip/service/impl/CapabilityRuntimeStatusService.java \
  backend/src/main/java/com/citytrip/service/impl/CapabilityStartupProbe.java \
  backend/src/main/java/com/citytrip/service/impl/RoutingChatServiceImpl.java \
  backend/src/test/java/com/citytrip/service/impl/CapabilityRuntimeStatusServiceTest.java
git -C F:\dachuang commit -m "feat: add capability startup probes and degraded snapshots"
```

---

### Task 3: 打通 vivo gateway、embedding 与 rerank provider client

**Files:**
- Create: `F:\dachuang\backend\src\main\java\com\citytrip\config\SemanticSearchProperties.java`
- Create: `F:\dachuang\backend\src\test\java\com\citytrip\service\impl\VivoEmbeddingClientTest.java`
- Create: `F:\dachuang\backend\src\test\java\com\citytrip\service\impl\VivoRerankClientTest.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\OpenAiGatewayClient.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\vivo\VivoEmbeddingClient.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\vivo\VivoRerankClient.java`
- Modify: `F:\dachuang\backend\src\main\resources\application.yml`
- Test: `F:\dachuang\backend\src\test\java\com\citytrip\service\impl\OpenAiGatewayClientTest.java`

- [ ] **Step 1: 先写失败测试，锁定 embedding / rerank 也必须走真实 vivo 请求**

```java
class VivoEmbeddingClientTest {

    @Test
    void embedShouldAppendRequestIdAndParseVectors() throws Exception {
        AtomicReference<String> capturedQuery = new AtomicReference<>();
        try (HttpServerHandle server = HttpServerHandle.custom("/embedding", exchange -> {
            capturedQuery.set(exchange.getRequestURI().getQuery());
            HttpServerHandle.respondJson(exchange, """
                {"data":[{"embedding":[0.12,0.34]},{"embedding":[0.56,0.78]}]}
                """);
        })) {
            SemanticSearchProperties properties = SemanticSearchProperties.forTests(server.baseUrl(), "/embedding", "/rerank");
            VivoEmbeddingClient client = new VivoEmbeddingClient(new RestTemplate(), new ObjectMapper(), properties, new VivoRequestIdFactory());

            List<List<Double>> vectors = client.embed("m3e-base", List.of("成都夜游", "太古里夜景"));

            assertThat(vectors).hasSize(2);
            assertThat(capturedQuery.get()).contains("requestId=");
        }
    }
}
```

```java
class VivoRerankClientTest {

    @Test
    void rerankShouldFailClearlyWhenProviderReturnsJsonErrorWithHttp200() throws Exception {
        try (HttpServerHandle server = HttpServerHandle.json("/rerank", """
                {"error":{"code":"401","message":"no rerank access permission"}}
                """)) {
            SemanticSearchProperties properties = SemanticSearchProperties.forTests(server.baseUrl(), "/embedding", "/rerank");
            VivoRerankClient client = new VivoRerankClient(new RestTemplate(), new ObjectMapper(), properties, new VivoRequestIdFactory());

            assertThatThrownBy(() -> client.rerank("夜游", List.of("太古里", "博物馆")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no rerank access permission");
        }
    }
}
```

- [ ] **Step 2: 运行测试，确认当前 stub client 先红**

Run:

```bash
cd /d F:\dachuang\backend
mvn -q -Dtest=OpenAiGatewayClientTest,VivoEmbeddingClientTest,VivoRerankClientTest test
```

Expected:
- FAIL，`VivoEmbeddingClient` / `VivoRerankClient` 当前只返回空列表。

- [ ] **Step 3: 实现 semantic provider 配置与真实在线 client**

```java
@Data
@Component
@ConfigurationProperties(prefix = "app.semantic")
public class SemanticSearchProperties {
    private boolean enabled = true;
    private String baseUrl = "https://api-ai.vivo.com.cn/v1";
    private String embeddingPath = "/embedding";
    private String rerankPath = "/rerank";
    private String apiKey = "";
    private String embeddingModel = "m3e-base";
    private String rerankModel = "bge-reranker-large";
    private int connectTimeoutMs = 1200;
    private int readTimeoutMs = 2200;

    static SemanticSearchProperties forTests(String baseUrl, String embeddingPath, String rerankPath) {
        SemanticSearchProperties properties = new SemanticSearchProperties();
        properties.setBaseUrl(baseUrl);
        properties.setEmbeddingPath(embeddingPath);
        properties.setRerankPath(rerankPath);
        properties.setApiKey("sk-test");
        return properties;
    }
}
```

```java
@Component
public class VivoEmbeddingClient {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final SemanticSearchProperties properties;
    private final VivoRequestIdFactory requestIdFactory;

    public List<List<Double>> embed(String modelName, List<String> sentences) {
        if (!properties.isEnabled() || sentences == null || sentences.isEmpty()) {
            return List.of();
        }
        URI uri = UriComponentsBuilder.fromHttpUrl(properties.getBaseUrl())
                .path(properties.getEmbeddingPath())
                .queryParam("requestId", requestIdFactory.create())
                .build(true)
                .toUri();
        Map<String, Object> payload = Map.of(
                "model", StringUtils.hasText(modelName) ? modelName : properties.getEmbeddingModel(),
                "input", sentences
        );
        String raw = restTemplate.postForObject(uri, new HttpEntity<>(payload, buildHeaders()), String.class);
        ensureNoProviderError(raw, "embedding");
        return parseEmbeddings(raw);
    }
}
```

```java
@Component
public class VivoRerankClient {
    public List<Double> rerank(String query, List<String> sentences) {
        if (!properties.isEnabled() || !StringUtils.hasText(query) || sentences == null || sentences.isEmpty()) {
            return List.of();
        }
        URI uri = UriComponentsBuilder.fromHttpUrl(properties.getBaseUrl())
                .path(properties.getRerankPath())
                .queryParam("requestId", requestIdFactory.create())
                .build(true)
                .toUri();
        Map<String, Object> payload = Map.of(
                "model", properties.getRerankModel(),
                "query", query,
                "documents", sentences
        );
        String raw = restTemplate.postForObject(uri, new HttpEntity<>(payload, buildHeaders()), String.class);
        ensureNoProviderError(raw, "rerank");
        return parseScores(raw);
    }
}
```

```yaml
app:
  semantic:
    enabled: ${APP_SEMANTIC_ENABLED:true}
    base-url: ${APP_SEMANTIC_BASE_URL:${OPENAI_BASE_URL:https://api-ai.vivo.com.cn/v1}}
    api-key: ${APP_SEMANTIC_API_KEY:${OPENAI_API_KEY:}}
    embedding-path: ${APP_EMBEDDING_PATH:/embedding}
    rerank-path: ${APP_RERANK_PATH:/rerank}
    embedding-model: ${APP_EMBEDDING_MODEL:m3e-base}
    rerank-model: ${APP_RERANK_MODEL:bge-reranker-large}
    connect-timeout-ms: ${APP_SEMANTIC_CONNECT_TIMEOUT_MS:1200}
    read-timeout-ms: ${APP_SEMANTIC_READ_TIMEOUT_MS:2200}
```

- [ ] **Step 4: 重新跑测试，确认 provider client 从 stub 变成真实在线调用**

Run:

```bash
cd /d F:\dachuang\backend
mvn -q -Dtest=OpenAiGatewayClientTest,VivoEmbeddingClientTest,VivoRerankClientTest test
```

Expected:
- PASS，chat / embedding / rerank 都会带 `requestId`，并且 `200 + error body` 会明确失败。

- [ ] **Step 5: 提交 vivo provider client 打通**

```bash
git -C F:\dachuang add -- \
  backend/src/main/java/com/citytrip/config/SemanticSearchProperties.java \
  backend/src/main/java/com/citytrip/service/impl/OpenAiGatewayClient.java \
  backend/src/main/java/com/citytrip/service/impl/vivo/VivoEmbeddingClient.java \
  backend/src/main/java/com/citytrip/service/impl/vivo/VivoRerankClient.java \
  backend/src/main/resources/application.yml \
  backend/src/test/java/com/citytrip/service/impl/OpenAiGatewayClientTest.java \
  backend/src/test/java/com/citytrip/service/impl/VivoEmbeddingClientTest.java \
  backend/src/test/java/com/citytrip/service/impl/VivoRerankClientTest.java
git -C F:\dachuang commit -m "feat: connect vivo embedding and rerank clients"
```

---

### Task 4: 固化 vivo GEO live lane，只做 POI 检索不做导航包装

**Files:**
- Create: `F:\dachuang\backend\src\test\java\com\citytrip\service\geo\GeoSearchServiceImplTest.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\config\GeoSearchProperties.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\geo\impl\GeoSearchServiceImpl.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\PoiServiceImpl.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\controller\PoiController.java`
- Test: `F:\dachuang\backend\src\test\java\com\citytrip\controller\PoiControllerTest.java`

- [ ] **Step 1: 先写失败测试，锁定 `/search/geo` 的 query shape 与 fail-open 行为**

```java
class GeoSearchServiceImplTest {

    @Test
    void searchByKeywordShouldUseVivoGeoQueryShape() {
        GeoSearchProperties properties = new GeoSearchProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("https://api-ai.vivo.com.cn");
        properties.setKeywordSearchPath("/search/geo");

        RecordingRestTemplate restTemplate = new RecordingRestTemplate("""
                {"pois":[{"name":"太古里","address":"锦江区","city":"成都"}]}
                """);
        GeoSearchServiceImpl service = new GeoSearchServiceImpl(properties, restTemplate, new ObjectMapper());

        service.searchByKeyword("太古里", "成都", 5);

        assertThat(restTemplate.lastUrl()).contains("keywords=%E5%A4%AA%E5%8F%A4%E9%87%8C");
        assertThat(restTemplate.lastUrl()).contains("city=%E6%88%90%E9%83%BD");
        assertThat(restTemplate.lastUrl()).contains("page_num=1");
        assertThat(restTemplate.lastUrl()).contains("page_size=5");
        assertThat(restTemplate.lastUrl()).contains("requestId=");
    }
}
```

```java
class PoiControllerTest {

    @Test
    void searchShouldReturnLivePoiResultsWithoutRouteLanguage() throws Exception {
        when(poiService.searchLive("太古里", "成都", 5)).thenReturn(List.of(
                new PoiSearchResultVO("太古里", "成都锦江区", "商场", null, null, "成都", null, "vivo-geo-online")
        ));

        mockMvc.perform(get("/api/pois/search")
                        .param("keyword", "太古里")
                        .param("city", "成都")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].source").value("vivo-geo-online"))
                .andExpect(content().string(not(containsString("导航"))));
    }
}
```

- [ ] **Step 2: 运行测试，确认当前 GEO lane 还有命名和路径歧义**

Run:

```bash
cd /d F:\dachuang\backend
mvn -q -Dtest=GeoSearchServiceImplTest,PoiControllerTest test
```

Expected:
- FAIL，`source` 仍可能是 `vivo-geo`，或 `GeoSearchProperties` 默认 path 仍指向旧的 `/geo/search`。

- [ ] **Step 3: 最小实现 unified GEO path、live source 标记和措辞收口**

```java
@Data
@Component
@ConfigurationProperties(prefix = "app.geo")
public class GeoSearchProperties {
    private boolean enabled = false;
    private String baseUrl = "";
    private String keywordSearchPath = "/search/geo";
    private String nearbySearchPath = "/search/geo";
    private String routePath = "";
    private int defaultLimit = 8;
    private String defaultCityName = "成都";
}
```

```java
private MultiValueMap<String, String> buildBaseQuery(String cityName, int limit) {
    MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
    String resolvedCity = StringUtils.hasText(cityName) ? cityName.trim() : geoSearchProperties.getDefaultCityName();
    if (StringUtils.hasText(resolvedCity)) {
        query.add("city", resolvedCity);
    }
    query.add("page_num", "1");
    query.add("page_size", String.valueOf(limit));
    query.add("requestId", vivoRequestIdFactory.create());
    return query;
}
```

```java
private PoiSearchResultVO toSearchResult(GeoPoiCandidate candidate) {
    return new PoiSearchResultVO(
            candidate.getName(),
            candidate.getAddress(),
            candidate.getCategory(),
            candidate.getLatitude(),
            candidate.getLongitude(),
            candidate.getCityName(),
            null,
            "vivo-geo-online"
    );
}
```

```java
@GetMapping("/search")
public List<PoiSearchResultVO> search(@RequestParam("keyword") String keyword,
                                      @RequestParam(value = "city", required = false) String city,
                                      @RequestParam(value = "limit", required = false, defaultValue = "8") int limit) {
    return poiService.searchLive(keyword, city, limit);
}
```

- [ ] **Step 4: 重新跑测试，确认 GEO 只承担 POI 检索能力**

Run:

```bash
cd /d F:\dachuang\backend
mvn -q -Dtest=GeoSearchServiceImplTest,PoiControllerTest test
```

Expected:
- PASS，`/search/geo` 请求格式固定，返回的 `source` 也不再暗示导航能力。

- [ ] **Step 5: 提交 POI live lane 收口**

```bash
git -C F:\dachuang add -- \
  backend/src/main/java/com/citytrip/config/GeoSearchProperties.java \
  backend/src/main/java/com/citytrip/service/geo/impl/GeoSearchServiceImpl.java \
  backend/src/main/java/com/citytrip/service/impl/PoiServiceImpl.java \
  backend/src/main/java/com/citytrip/controller/PoiController.java \
  backend/src/test/java/com/citytrip/service/geo/GeoSearchServiceImplTest.java \
  backend/src/test/java/com/citytrip/controller/PoiControllerTest.java
git -C F:\dachuang commit -m "feat: stabilize vivo poi live lane"
```

---

### Task 5: 把社区搜索升级成 lexical + embedding + rerank 的 fail-open 主链路

**Files:**
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\application\community\CommunitySemanticSearchService.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\application\community\CommunityItineraryQueryService.java`
- Test: `F:\dachuang\backend\src\test\java\com\citytrip\service\application\community\CommunitySemanticSearchServiceTest.java`
- Test: `F:\dachuang\backend\src\test\java\com\citytrip\service\application\community\CommunityItineraryQueryServiceTest.java`

- [ ] **Step 1: 先写失败测试，锁定 theme / pinned 优先和 rerank 降级不丢结果**

```java
@Test
void listPublicShouldKeepThemeFilterBeforeSemanticRanking() {
    CommunitySemanticSearchService semantic = mock(CommunitySemanticSearchService.class);
    when(semantic.rank(eq("夜游"), anyList())).thenReturn(List.of(
            new CommunitySemanticSearchService.ScoredCommunityCandidate(2L, 0.98D),
            new CommunitySemanticSearchService.ScoredCommunityCandidate(1L, 0.32D)
    ));

    CommunityItineraryQueryService service = newQueryServiceWithSemantic(semantic);
    CommunityItineraryPageVO page = service.listPublic(1, 10, "latest", "夜游", "美食", null);

    assertThat(page.getPinnedRecords()).hasSizeLessThanOrEqualTo(3);
    assertThat(page.getRecords()).allMatch(item -> item.getThemes().contains("美食"));
}
```

```java
@Test
void shouldFallbackToLexicalCandidatesWhenEmbeddingFails() {
    CommunitySemanticSearchService service = new CommunitySemanticSearchService(
            new VivoEmbeddingClient() {
                @Override
                public List<List<Double>> embed(String modelName, List<String> sentences) {
                    throw new IllegalStateException("embedding down");
                }
            },
            new VivoRerankClient() {
                @Override
                public List<Double> rerank(String query, List<String> sentences) {
                    return List.of(0.61D, 0.22D);
                }
            }
    );

    List<CommunitySemanticSearchService.ScoredCommunityCandidate> ranked = service.rank(
            "太古里夜游",
            List.of(
                    new CommunitySemanticSearchService.CommunitySemanticCandidate(1L, "太古里夜景散步"),
                    new CommunitySemanticSearchService.CommunitySemanticCandidate(2L, "白天博物馆")
            )
    );

    assertThat(ranked).isNotEmpty();
    assertThat(ranked.get(0).id()).isEqualTo(1L);
}
```

- [ ] **Step 2: 运行测试，确认当前 semantic 管线还没有把业务优先级锁死**

Run:

```bash
cd /d F:\dachuang\backend
mvn -q -Dtest=CommunitySemanticSearchServiceTest,CommunityItineraryQueryServiceTest test
```

Expected:
- FAIL，当前实现可能缺少 embedding 异常后的 lexical fallback 或排序顺序不够稳定。

- [ ] **Step 3: 最小实现 semantic pipeline 与 fail-open 排序策略**

```java
public List<ScoredCommunityCandidate> rank(String query, List<CommunitySemanticCandidate> candidates) {
    if (!StringUtils.hasText(query) || candidates == null || candidates.isEmpty()) {
        return List.of();
    }

    List<ScoredCommunityCandidate> lexical = lexicalRank(query, candidates);
    List<ScoredCommunityCandidate> coarse;
    try {
        coarse = embedAndScore(query, candidates);
    } catch (Exception ex) {
        coarse = List.of();
    }
    List<ScoredCommunityCandidate> mergedCandidates = coarse.isEmpty() ? lexical : mergeLexicalAndSemantic(lexical, coarse);
    List<ScoredCommunityCandidate> topN = mergedCandidates.stream().limit(RERANK_LIMIT).toList();
    try {
        return rerankAndMerge(query, topN, candidates);
    } catch (Exception ex) {
        return topN;
    }
}
```

```java
private List<CommunityItineraryVO> applySemanticRanking(String keyword, List<CommunityItineraryVO> records) {
    try {
        List<CommunitySemanticSearchService.CommunitySemanticCandidate> candidates = records.stream()
                .map(this::toSemanticCandidate)
                .toList();
        Map<Long, Double> scoreMap = communitySemanticSearchService.rank(keyword, candidates).stream()
                .collect(Collectors.toMap(
                        CommunitySemanticSearchService.ScoredCommunityCandidate::id,
                        CommunitySemanticSearchService.ScoredCommunityCandidate::score,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        return records.stream()
                .sorted(Comparator
                        .comparing((CommunityItineraryVO item) -> scoreMap.getOrDefault(item.getId(), -1D))
                        .reversed()
                        .thenComparing(buildFeedComparator("latest")))
                .toList();
    } catch (Exception ex) {
        log.warn("Semantic community ranking failed, fallback to original ordering. keyword={}", keyword, ex);
        return records;
    }
}
```

- [ ] **Step 4: 重新跑测试，确认“搜得到”优先于“排得更聪明”**

Run:

```bash
cd /d F:\dachuang\backend
mvn -q -Dtest=CommunitySemanticSearchServiceTest,CommunityItineraryQueryServiceTest test
```

Expected:
- PASS，theme / pinned / 业务可见性仍然最优先，semantic 只优化排序不决定接口生死。

- [ ] **Step 5: 提交社区 semantic 主链路**

```bash
git -C F:\dachuang add -- \
  backend/src/main/java/com/citytrip/service/application/community/CommunitySemanticSearchService.java \
  backend/src/main/java/com/citytrip/service/application/community/CommunityItineraryQueryService.java \
  backend/src/test/java/com/citytrip/service/application/community/CommunitySemanticSearchServiceTest.java \
  backend/src/test/java/com/citytrip/service/application/community/CommunityItineraryQueryServiceTest.java
git -C F:\dachuang commit -m "feat: harden community semantic fail-open pipeline"
```

---

### Task 6: 强化聊天主链路、tool loop 纪律与在线 related tips

**Files:**
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\RealChatGatewayService.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\RealLlmGatewayService.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\SafePromptBuilder.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\vivo\VivoFunctionCallingService.java`
- Test: `F:\dachuang\backend\src\test\java\com\citytrip\service\impl\VivoFunctionCallingServiceTest.java`
- Test: `F:\dachuang\backend\src\test\java\com\citytrip\service\impl\OpenAiServiceDelegationTest.java`

- [ ] **Step 1: 先写失败测试，锁定 tool loop 上限和 related tips 必须优先来自模型**

```java
@Test
void runToolLoopShouldCapAtThreeToolCalls() {
    VivoToolRegistry registry = new VivoToolRegistry();
    registry.register(new VivoToolDefinition("search_poi", "", "{}", args -> "{\"status\":\"ok\"}"));
    VivoFunctionCallingService service = new VivoFunctionCallingService(registry, new ObjectMapper());

    String payload = OpenAiGatewayClient.TOOL_CALL_PREFIX + """
        [
          {"function":{"name":"search_poi","arguments":"{}"}},
          {"function":{"name":"search_poi","arguments":"{}"}},
          {"function":{"name":"search_poi","arguments":"{}"}},
          {"function":{"name":"search_poi","arguments":"{}"}}
        ]
        """;

    VivoFunctionCallingService.ToolLoopResult result = service.runToolLoop(payload, List.of(), null, null, null);

    assertThat(result.toolResults()).hasSize(3);
}
```

```java
@Test
void answerQuestionShouldPreferOnlineGeneratedRelatedTips() {
    RealLlmGatewayService llmGateway = mock(RealLlmGatewayService.class);
    when(llmGateway.generateChatFollowUpTips("太古里附近晚上还能去哪", "成都")).thenReturn(List.of(
            "太古里附近适合夜景散步的点还有哪些？",
            "如果下雨，附近有没有适合室内逛的地方？"
    ));

    RealChatGatewayService service = buildRealChatGatewayService(llmGateway);
    ChatVO reply = service.answerQuestion(chatReq("太古里附近晚上还能去哪"));

    assertThat(reply.getRelatedTips()).contains("太古里附近适合夜景散步的点还有哪些？");
}
```

- [ ] **Step 2: 运行测试，确认当前 chat 链路仍在用固定模板 tips**

Run:

```bash
cd /d F:\dachuang\backend
mvn -q -Dtest=VivoFunctionCallingServiceTest,OpenAiServiceDelegationTest test
```

Expected:
- FAIL，`buildRelatedTips(...)` 当前还是本地静态模板，tool loop 也还未明确卡死上限。

- [ ] **Step 3: 最小实现 tool loop 纪律、prefetch 策略和在线 tips 生成**

```java
public ToolLoopResult runToolLoop(String payload,
                                  List<OpenAiGatewayClient.OpenAiMessage> originalMessages,
                                  OpenAiGatewayClient gatewayClient,
                                  LlmProperties.ResolvedOpenAiOptions options,
                                  String apiKey) {
    List<ToolCall> toolCalls = parseToolCalls(payload).stream().limit(3).toList();
    if (toolCalls.isEmpty()) {
        return new ToolLoopResult(payload, List.of());
    }
    List<String> toolResults = new ArrayList<>();
    for (ToolCall toolCall : toolCalls) {
        toolResults.add(executeToolCall(toolCall.name(), toolCall.argumentsJson()));
    }
    if (gatewayClient == null || options == null || !StringUtils.hasText(apiKey)) {
        return new ToolLoopResult(String.join("\n", toolResults), toolResults);
    }
    List<OpenAiGatewayClient.OpenAiMessage> followUp = new ArrayList<>(originalMessages);
    followUp.add(new OpenAiGatewayClient.OpenAiMessage("assistant", "Tool calls requested: " + payload.substring(OpenAiGatewayClient.TOOL_CALL_PREFIX.length())));
    followUp.add(new OpenAiGatewayClient.OpenAiMessage("user", "Tool results(JSON):\n" + String.join("\n", toolResults) + "\nPlease answer the original question grounded in these tool results."));
    String finalAnswer = gatewayClient.request(options, apiKey, followUp);
    return new ToolLoopResult(StringUtils.hasText(finalAnswer) ? finalAnswer : String.join("\n", toolResults), toolResults);
}
```

```java
public List<String> generateChatFollowUpTips(String question, String cityName) {
    String raw = callText(
            safePromptBuilder.buildChatFollowUpTipsPrompt(question, cityName),
            "你是旅行问答助手。请输出 3 条简短、自然、追问式的后续问题，每条一行，不要编号。"
    );
    return raw.lines()
            .map(String::trim)
            .filter(StringUtils::hasText)
            .limit(3)
            .toList();
}
```

```java
private List<String> buildRelatedTips(ChatReqDTO req) {
    try {
        String city = req != null && req.getContext() != null ? safe(req.getContext().getCityName()) : "";
        List<String> generated = realLlmGatewayService.generateChatFollowUpTips(safe(req.getQuestion()), city);
        if (generated != null && !generated.isEmpty()) {
            return generated;
        }
    } catch (Exception ex) {
        log.warn("Chat follow-up tips generation failed, fallback to local tips. reason={}", ex.getMessage());
    }
    return fallbackRelatedTips(req);
}
```

```java
public String buildChatFollowUpTipsPrompt(String question, String cityName) {
    return "用户问题：" + safe(question) + "\n"
            + "城市：" + safe(cityName) + "\n"
            + "请基于这个问题生成 3 条更具体、更像真人会继续追问的后续问题。";
}
```

- [ ] **Step 4: 重新跑测试，确认聊天回答和后续 tips 都优先使用 vivo 能力**

Run:

```bash
cd /d F:\dachuang\backend
mvn -q -Dtest=VivoFunctionCallingServiceTest,OpenAiServiceDelegationTest test
```

Expected:
- PASS，tool loop 最多 3 次工具调用，related tips 默认来自 vivo 文本生成，失败才退本地模板。

- [ ] **Step 5: 提交聊天主链路硬化**

```bash
git -C F:\dachuang add -- \
  backend/src/main/java/com/citytrip/service/impl/RealChatGatewayService.java \
  backend/src/main/java/com/citytrip/service/impl/RealLlmGatewayService.java \
  backend/src/main/java/com/citytrip/service/impl/SafePromptBuilder.java \
  backend/src/main/java/com/citytrip/service/impl/vivo/VivoFunctionCallingService.java \
  backend/src/test/java/com/citytrip/service/impl/VivoFunctionCallingServiceTest.java \
  backend/src/test/java/com/citytrip/service/impl/OpenAiServiceDelegationTest.java
git -C F:\dachuang commit -m "feat: harden vivo chat tool loop and related tips"
```

---

### Task 7: 前端最小接线，消费结构化 status 并优先展示在线生成 tips

**Files:**
- Modify: `F:\dachuang\frontend\src\api\chat.js`
- Modify: `F:\dachuang\frontend\src\store\chat.js`
- Create: `F:\dachuang\frontend\src\store\__tests__\chat.test.js`

- [ ] **Step 1: 先写失败测试，锁定 capability 状态与在线 tips 优先级**

```js
import { describe, expect, it } from 'vitest'
import { normalizeChatStatus } from '@/api/chat'
import { __test__ } from '@/store/chat'

describe('chat store', () => {
  it('normalizes structured capability status', () => {
    const status = normalizeChatStatus({
      capabilities: {
        chat: { state: 'ready', activePath: 'vivo-online' },
        tool: { state: 'degraded', fallbackPath: 'stable-fallback' }
      }
    })

    expect(status.capabilities.chat.state).toBe('ready')
    expect(status.capabilities.tool.fallbackPath).toBe('stable-fallback')
  })

  it('prefers backend generated tips over local fallback tips', () => {
    const tips = __test__.resolveTips(['太古里附近还有哪些适合夜拍的点？'])
    expect(tips).toEqual(['太古里附近还有哪些适合夜拍的点？'])
  })
})
```

- [ ] **Step 2: 运行前端测试，确认当前 api 层还没有结构化 normalize 方法**

Run:

```bash
cd /d F:\dachuang\frontend
npm run test -- src/store/__tests__/chat.test.js
```

Expected:
- FAIL，`normalizeChatStatus` 或 `__test__.resolveTips` 尚未导出。

- [ ] **Step 3: 最小实现前端兼容层与 tips 优先级**

```js
export function normalizeChatStatus(payload = {}) {
  const capabilities = payload?.capabilities && typeof payload.capabilities === 'object'
    ? payload.capabilities
    : {}

  return {
    ...payload,
    toolReady: Boolean(payload?.toolReady ?? capabilities?.tool?.state === 'ready'),
    geoReady: Boolean(payload?.geoReady ?? capabilities?.geo?.state === 'ready'),
    embeddingReady: Boolean(payload?.embeddingReady ?? capabilities?.embedding?.state === 'ready'),
    rerankReady: Boolean(payload?.rerankReady ?? capabilities?.rerank?.state === 'ready'),
    capabilities,
    warnings: Array.isArray(payload?.warnings) ? payload.warnings : []
  }
}

export function reqGetChatStatus() {
  return request({
    url: '/api/chat/messages/status',
    method: 'get',
    skipErrorMessage: true
  }).then(normalizeChatStatus)
}
```

```js
function resolveTips(candidate) {
  return Array.isArray(candidate) && candidate.length > 0 ? [...candidate] : [...fallbackTips]
}

export const __test__ = {
  resolveTips
}
```

- [ ] **Step 4: 重新跑前端测试，确认最小接线完成**

Run:

```bash
cd /d F:\dachuang\frontend
npm run test -- src/store/__tests__/chat.test.js
```

Expected:
- PASS，前端已能消费新的 capability 状态，并优先展示后端在线生成的 tips。

- [ ] **Step 5: 提交前端最小接线**

```bash
git -C F:\dachuang add -- \
  frontend/src/api/chat.js \
  frontend/src/store/chat.js \
  frontend/src/store/__tests__/chat.test.js
git -C F:\dachuang commit -m "feat: wire structured chat status and online tips"
```

---

## 全量验证顺序

在所有任务完成后，按下面顺序做一次完整验证：

1. 后端单测（能力底座）

```bash
cd /d F:\dachuang\backend
mvn -q -Dtest=LlmPropertiesTest,RoutingChatServiceImplTest,CapabilityRuntimeStatusServiceTest,OpenAiGatewayClientTest,VivoEmbeddingClientTest,VivoRerankClientTest,GeoSearchServiceImplTest,CommunitySemanticSearchServiceTest,CommunityItineraryQueryServiceTest,VivoFunctionCallingServiceTest,OpenAiServiceDelegationTest,PoiControllerTest test
```

Expected:
- PASS，所有能力底座相关测试通过。

2. 前端最小 smoke test

```bash
cd /d F:\dachuang\frontend
npm run test -- src/store/__tests__/chat.test.js
```

Expected:
- PASS，状态兼容和 tips 优先级通过。

3. 启动验证

```bash
cd /d F:\dachuang\backend
mvn spring-boot:run
```

Expected:
- 启动日志里能看到 capability startup snapshot。
- `/api/chat/messages/status` 返回 `capabilities.chat/tool/geo/embedding/rerank`。
- 同一配置下重启两次，状态结构一致。

4. 手工回归
- 首页聊天普通问答：应走 vivo chat。
- 聊天问“太古里附近晚上还能去哪”：应优先返回 live tool / grounded answer。
- 社区 keyword 搜索：embedding/rerank 失败时仍有帖子返回。
- `/api/pois/search?keyword=太古里&city=成都`：返回 `source=vivo-geo-online` 或本地 fallback，但不出现导航措辞。
- 关闭 `LLM_TOOL_LOOP_ENABLED=false` 后：聊天仍可回答，只是不再调工具。
- 关闭 `APP_SEMANTIC_ENABLED=false` 后：社区仍可搜索，但只走 lexical。

---

## Spec 覆盖自检

- **真实使用 vivo 大模型能力**：Task 3、Task 6。
- **Fail Open**：Task 2、Task 4、Task 5、Task 6。
- **准确优先**：Task 4、Task 5、Task 6。
- **灰度双轨**：Task 1、Task 2。
- **重启 / 重新部署后稳定**：Task 2。
- **不回退结果页与社区页视觉**：Task 7 仅做最小接线，没有视觉重构。
- **不把 `/search/geo` 说成导航规划**：Task 4。

## Placeholder 扫描

已人工检查本计划：
- 无占位词。
- 每个任务都包含测试、执行命令、预期结果和提交边界。
- 后续任务引用的方法名均已在前序任务或同任务代码块中定义。


