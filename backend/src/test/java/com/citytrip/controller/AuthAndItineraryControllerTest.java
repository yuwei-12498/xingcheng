package com.citytrip.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.citytrip.common.GlobalExceptionHandler;
import com.citytrip.config.AdminInterceptor;
import com.citytrip.config.AuthInterceptor;
import com.citytrip.config.AuthWebMvcConfig;
import com.citytrip.config.JsonUtf8ResponseAdvice;
import com.citytrip.config.JwtProperties;
import com.citytrip.mapper.CommunityCommentMapper;
import com.citytrip.mapper.CommunityLikeMapper;
import com.citytrip.mapper.PoiMapper;
import com.citytrip.mapper.RouteNodeFactMapper;
import com.citytrip.mapper.RoutePlanFactMapper;
import com.citytrip.mapper.SavedItineraryEditVersionMapper;
import com.citytrip.mapper.SavedItineraryMapper;
import com.citytrip.mapper.UserCustomPoiMapper;
import com.citytrip.mapper.UserBehaviorEventMapper;
import com.citytrip.mapper.UserMapper;
import com.citytrip.model.dto.CommunityCommentReqDTO;
import com.citytrip.model.dto.FavoriteReqDTO;
import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.dto.LoginReqDTO;
import com.citytrip.model.dto.PublicStatusReqDTO;
import com.citytrip.model.dto.SaveItineraryReqDTO;
import com.citytrip.model.dto.SmartFillReqDTO;
import com.citytrip.model.entity.Poi;
import com.citytrip.model.entity.User;
import com.citytrip.model.vo.AdminUserVO;
import com.citytrip.model.vo.CommunityCommentVO;
import com.citytrip.model.vo.CommunityItineraryDetailVO;
import com.citytrip.model.vo.CommunityItineraryPageVO;
import com.citytrip.model.vo.CommunityItineraryVO;
import com.citytrip.model.vo.ItineraryNodeVO;
import com.citytrip.model.vo.RoutePathPointVO;
import com.citytrip.model.vo.SegmentRouteGuideVO;
import com.citytrip.model.vo.SegmentRouteStepVO;
import com.citytrip.model.vo.ItinerarySummaryVO;
import com.citytrip.model.vo.ItineraryVO;
import com.citytrip.model.vo.SmartFillVO;
import com.citytrip.model.vo.UserSessionVO;
import com.citytrip.service.AdminService;
import com.citytrip.service.ItineraryService;
import com.citytrip.service.UserService;
import com.citytrip.service.application.itinerary.SmartFillUseCase;
import com.citytrip.service.guard.AiRequestGuard;
import com.citytrip.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, ItineraryController.class, CommunityController.class, ProfileController.class, AdminController.class})
@Import({
        AuthWebMvcConfig.class,
        AuthInterceptor.class,
        AdminInterceptor.class,
        JsonUtf8ResponseAdvice.class,
        GlobalExceptionHandler.class,
        AuthAndItineraryControllerTest.TestConfig.class
})
class AuthAndItineraryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private ItineraryService itineraryService;

    @MockBean
    private SmartFillUseCase smartFillUseCase;

    @MockBean
    private AdminService adminService;

    @MockBean
    private AiRequestGuard aiRequestGuard;

    @MockBean
    private PoiMapper poiMapper;

    @MockBean
    private SavedItineraryMapper savedItineraryMapper;

    @MockBean
    private SavedItineraryEditVersionMapper savedItineraryEditVersionMapper;

    @MockBean
    private UserCustomPoiMapper userCustomPoiMapper;

    @MockBean
    private CommunityCommentMapper communityCommentMapper;

    @MockBean
    private CommunityLikeMapper communityLikeMapper;

    @MockBean
    private UserMapper userMapper;

    @MockBean
    private RoutePlanFactMapper routePlanFactMapper;

    @MockBean
    private RouteNodeFactMapper routeNodeFactMapper;

    @MockBean
    private UserBehaviorEventMapper userBehaviorEventMapper;

    private String bearerToken;

    @BeforeEach
    void setUp() {
        bearerToken = "Bearer " + jwtUtil.generateToken(101L, 1);
        lenient().when(userMapper.selectById(101L)).thenReturn(buildUser(101L, "tester", "Test Nickname", 1, 1));
        lenient().when(aiRequestGuard.call(anyString(), anyString(), any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(2)).get());
    }

    @Test
    void loginReturnsSessionPayload() throws Exception {
        LoginReqDTO req = new LoginReqDTO();
        req.setUsername("tester");
        req.setPassword("password123");

        UserSessionVO sessionVO = buildSession(101L, "tester", "Test Nickname", 1, "token-123");
        when(userService.login(any(LoginReqDTO.class))).thenReturn(sessionVO);

        mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("application/json")))
                .andExpect(header().string("Content-Type", containsString("charset=UTF-8")))
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.username").value("tester"))
                .andExpect(jsonPath("$.role").value(1))
                .andExpect(jsonPath("$.token").value("token-123"));
    }

    @Test
    void sendRegisterCodeAcceptsEmailAndDelegatesToUserService() throws Exception {
        mockMvc.perform(post("/api/users/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"USER@example.COM"}
                                """))
                .andExpect(status().isNoContent());

        verify(userService).sendRegisterCode("user@example.com");
    }

    @Test
    void sendPasswordResetCodeAcceptsEmailAndDelegatesToUserService() throws Exception {
        mockMvc.perform(post("/api/auth/password/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"USER@example.COM"}
                                """))
                .andExpect(status().isNoContent());

        verify(userService).sendPasswordResetCode("user@example.com");
    }

    @Test
    void resetPasswordDelegatesToUserService() throws Exception {
        mockMvc.perform(post("/api/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"USER@example.COM","emailCode":"123456","password":"newpass123"}
                                """))
                .andExpect(status().isNoContent());

        verify(userService).resetPassword(any());
    }

    @Test
    void meUsesJwtUserIdFromRequestAttribute() throws Exception {
        UserSessionVO sessionVO = buildSession(101L, "tester", "Test Nickname", 1, "token-123");
        when(userService.getSessionUser(101L)).thenReturn(sessionVO);

        mockMvc.perform(get("/api/users/me").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.username").value("tester"))
                .andExpect(jsonPath("$.role").value(1));
    }

    @Test
    void createItineraryUsesJwtUserId() throws Exception {
        GenerateReqDTO req = new GenerateReqDTO();
        req.setTripDate("2026-04-03");
        req.setStartTime("09:00");
        req.setEndTime("18:00");
        req.setThemes(List.of("culture", "history"));

        ItineraryVO itineraryVO = buildItinerary(501L, 180, "Rule-based recommendation");
        when(itineraryService.generateUserItinerary(eq(101L), any(GenerateReqDTO.class))).thenReturn(itineraryVO);

        mockMvc.perform(post("/api/itineraries")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(501))
                .andExpect(jsonPath("$.totalDuration").value(180));

        verify(itineraryService).generateUserItinerary(eq(101L), any(GenerateReqDTO.class));
    }

    @Test
    void generateItineraryEndpointUsesJwtUserId() throws Exception {
        GenerateReqDTO req = new GenerateReqDTO();
        req.setCityName("成都");
        req.setTripDays(2.0D);
        req.setTripDate("2026-05-01");
        req.setTotalBudget(1800D);
        req.setBudgetLevel("medium");
        req.setStartTime("09:30");
        req.setEndTime("21:30");
        req.setThemes(List.of("人文", "美食"));

        ItineraryVO itineraryVO = buildItinerary(601L, 420, "AI generated route");
        when(itineraryService.generateUserItinerary(eq(101L), any(GenerateReqDTO.class))).thenReturn(itineraryVO);

        mockMvc.perform(post("/api/itineraries/generate")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(601))
                .andExpect(jsonPath("$.totalDuration").value(420));

        verify(itineraryService).generateUserItinerary(eq(101L), any(GenerateReqDTO.class));
    }

    @Test
    void generateItineraryEndpointSerializesSegmentRouteGuide() throws Exception {
        GenerateReqDTO req = new GenerateReqDTO();
        req.setCityName("Chengdu");
        req.setTripDate("2026-05-01");
        req.setStartTime("09:30");
        req.setEndTime("21:30");

        ItineraryVO itineraryVO = buildItinerary(602L, 420, "AI generated route");
        ItineraryNodeVO node = new ItineraryNodeVO();
        node.setStepOrder(1);
        node.setPoiName("Museum");
        node.setSegmentRouteGuide(buildSegmentRouteGuide("walk 5 min"));
        itineraryVO.setNodes(List.of(node));

        when(itineraryService.generateUserItinerary(eq(101L), any(GenerateReqDTO.class))).thenReturn(itineraryVO);

        mockMvc.perform(post("/api/itineraries/generate")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes[0].segmentRouteGuide.summary").value("walk 5 min"))
                .andExpect(jsonPath("$.nodes[0].segmentRouteGuide.transportMode").value("walk"))
                .andExpect(jsonPath("$.nodes[0].segmentRouteGuide.steps[0].instruction").value("Walk to the next stop"))
                .andExpect(jsonPath("$.nodes[0].segmentRouteGuide.pathPoints[0].latitude").value(30.6573))
                .andExpect(jsonPath("$.nodes[0].segmentRouteGuide.pathPoints[0].longitude").value(104.0817));
    }

    @Test
    void smartFillEndpointReturnsStructuredPayload() throws Exception {
        SmartFillVO vo = new SmartFillVO();
        vo.setThemes(List.of("购物"));
        vo.setMustVisitPoiNames(List.of("IFS国际金融中心"));
        vo.setSummary(List.of("必去：IFS国际金融中心"));

        when(smartFillUseCase.parse(any(SmartFillReqDTO.class))).thenReturn(vo);

        mockMvc.perform(post("/api/itineraries/smart-fill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"我想去IFS金融中心"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustVisitPoiNames[0]").value("IFS国际金融中心"))
                .andExpect(jsonPath("$.summary[0]").value("必去：IFS国际金融中心"));

        verify(smartFillUseCase).parse(any(SmartFillReqDTO.class));
    }

    @Test
    void smartFillRejectsBlankTextBeforeCallingUseCase() throws Exception {
        mockMvc.perform(post("/api/itineraries/smart-fill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("smart-fill text must not be blank"));
    }

    @Test
    void favoriteItineraryUsesJwtUserId() throws Exception {
        FavoriteReqDTO req = new FavoriteReqDTO();
        req.setSelectedOptionKey("balanced");
        req.setTitle("My Chengdu Route");

        ItineraryVO itineraryVO = buildItinerary(501L, 240, "Already favorited");
        itineraryVO.setFavorited(true);
        when(itineraryService.favoriteItinerary(eq(101L), eq(501L), any(FavoriteReqDTO.class))).thenReturn(itineraryVO);

        mockMvc.perform(put("/api/itineraries/501/favorite")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(501))
                .andExpect(jsonPath("$.favorited").value(true));

        verify(itineraryService).favoriteItinerary(eq(101L), eq(501L), any(FavoriteReqDTO.class));
    }

    @Test
    void listItinerariesUsesJwtUserIdForHistoryQuery() throws Exception {
        ItinerarySummaryVO summary = new ItinerarySummaryVO();
        summary.setId(501L);
        summary.setTitle("History Route");
        when(itineraryService.listItineraries(101L, true, 5)).thenReturn(List.of(summary));

        mockMvc.perform(get("/api/itineraries")
                        .header("Authorization", bearerToken)
                        .param("favorite", "true")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(501))
                .andExpect(jsonPath("$[0].title").value("History Route"));

        ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(itineraryService).listItineraries(eq(101L), eq(true), limitCaptor.capture());
        assertThat(limitCaptor.getValue()).isEqualTo(5);
    }

    @Test
    void profileGeneratedItinerariesUseJwtUserId() throws Exception {
        ItinerarySummaryVO summary = new ItinerarySummaryVO();
        summary.setId(601L);
        summary.setTitle("My Generated Route");
        summary.setCityName("成都");

        when(itineraryService.listProfileItineraries(101L, "generated", 20))
                .thenReturn(List.of(summary));

        mockMvc.perform(get("/api/user/itineraries")
                        .header("Authorization", bearerToken)
                        .param("type", "generated")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(601))
                .andExpect(jsonPath("$[0].title").value("My Generated Route"))
                .andExpect(jsonPath("$[0].cityName").value("成都"));

        verify(itineraryService).listProfileItineraries(101L, "generated", 20);
    }

    @Test
    void profileSavedItinerariesUseJwtUserId() throws Exception {
        ItinerarySummaryVO summary = new ItinerarySummaryVO();
        summary.setId(701L);
        summary.setTitle("My Saved Route");

        when(itineraryService.listProfileItineraries(101L, "saved", null))
                .thenReturn(List.of(summary));

        mockMvc.perform(get("/api/user/itineraries")
                        .header("Authorization", bearerToken)
                        .param("type", "saved"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(701))
                .andExpect(jsonPath("$[0].title").value("My Saved Route"));

        verify(itineraryService).listProfileItineraries(101L, "saved", null);
    }

    @Test
    void communityPageEndpointReturnsPublicCards() throws Exception {
        CommunityItineraryVO summary = new CommunityItineraryVO();
        summary.setId(801L);
        summary.setTitle("成都人文慢游");
        summary.setCityName("成都");
        summary.setCoverImageUrl("https://images.example.com/chengdu.jpg");
        summary.setAuthorLabel("城市体验官");
        summary.setLikeCount(88L);
        summary.setCommentCount(12L);

        CommunityItineraryPageVO page = new CommunityItineraryPageVO();
        page.setPage(1);
        page.setSize(12);
        page.setTotal(1L);
        page.setRecords(List.of(summary));

        when(itineraryService.listCommunityItineraries(1, 12, "latest", null, null, null)).thenReturn(page);

        mockMvc.perform(get("/api/community/itineraries")
                        .param("page", "1")
                        .param("size", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records[0].id").value(801))
                .andExpect(jsonPath("$.records[0].title").value("成都人文慢游"))
                .andExpect(jsonPath("$.records[0].coverImageUrl").value("https://images.example.com/chengdu.jpg"))
                .andExpect(jsonPath("$.records[0].authorLabel").value("城市体验官"))
                .andExpect(jsonPath("$.records[0].likeCount").value(88));
    }

    @Test
    void communityDetailEndpointReturnsPublicRouteDetailFromNewPath() throws Exception {
        CommunityItineraryDetailVO detail = new CommunityItineraryDetailVO();
        detail.setId(801L);
        detail.setTitle("成都人文慢游");
        detail.setCityName("成都");
        detail.setAuthorLabel("城市体验官");
        detail.setRouteSummary("杜甫草堂 -> 宽窄巷子");

        when(itineraryService.getCommunityItinerary(801L, null)).thenReturn(detail);

        mockMvc.perform(get("/api/community/itineraries/801"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(801))
                .andExpect(jsonPath("$.title").value("成都人文慢游"))
                .andExpect(jsonPath("$.routeSummary").value("杜甫草堂 -> 宽窄巷子"));
    }

    @Test
    void communityEndpointReturnsPublicItineraries() throws Exception {
        CommunityItineraryVO summary = new CommunityItineraryVO();
        summary.setId(801L);
        summary.setTitle("One-day citywalk");

        CommunityItineraryPageVO page = new CommunityItineraryPageVO();
        page.setPage(1);
        page.setSize(12);
        page.setTotal(1L);
        page.setRecords(List.of(summary));

        when(itineraryService.listCommunityItineraries(1, 12, "latest", null, null, null)).thenReturn(page);

        mockMvc.perform(get("/api/itineraries/community")
                        .param("page", "1")
                        .param("size", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records[0].id").value(801))
                .andExpect(jsonPath("$.records[0].title").value("One-day citywalk"));
    }

    @Test
    void communityDetailEndpointReturnsRouteDetail() throws Exception {
        CommunityItineraryDetailVO detail = new CommunityItineraryDetailVO();
        detail.setId(801L);
        detail.setTitle("Chengdu citywalk");
        detail.setAuthorLabel("Test Author");

        when(itineraryService.getCommunityItinerary(801L, null)).thenReturn(detail);

        mockMvc.perform(get("/api/itineraries/community/801"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(801))
                .andExpect(jsonPath("$.title").value("Chengdu citywalk"));
    }

    @Test
    void saveCommunityItineraryUsesJwtUserId() throws Exception {
        SaveItineraryReqDTO req = new SaveItineraryReqDTO();
        req.setSourceItineraryId(801L);
        req.setSelectedOptionKey("balanced");
        req.setTitle("收藏到我的行程");

        ItineraryVO itineraryVO = buildItinerary(901L, 360, "Saved from community");
        itineraryVO.setFavorited(true);

        when(itineraryService.saveCommunityItinerary(eq(101L), any(SaveItineraryReqDTO.class))).thenReturn(itineraryVO);

        mockMvc.perform(post("/api/itineraries/save")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(901))
                .andExpect(jsonPath("$.favorited").value(true));

        verify(itineraryService).saveCommunityItinerary(eq(101L), any(SaveItineraryReqDTO.class));
    }

    @Test
    void addCommunityCommentUsesJwtUserId() throws Exception {
        CommunityCommentReqDTO req = new CommunityCommentReqDTO();
        req.setContent("Great route for a first-time visitor.");

        CommunityCommentVO comment = new CommunityCommentVO();
        comment.setId(901L);
        comment.setItineraryId(801L);
        comment.setContent("Great route for a first-time visitor.");
        comment.setMine(true);

        when(itineraryService.addCommunityComment(eq(101L), eq(801L), any(CommunityCommentReqDTO.class))).thenReturn(comment);

        mockMvc.perform(post("/api/itineraries/community/801/comments")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(901))
                .andExpect(jsonPath("$.mine").value(true));

        verify(itineraryService).addCommunityComment(eq(101L), eq(801L), any(CommunityCommentReqDTO.class));
    }

    @Test
    void likeCommunityItineraryUsesJwtUserId() throws Exception {
        CommunityItineraryDetailVO detail = new CommunityItineraryDetailVO();
        detail.setId(801L);
        detail.setLikeCount(9L);
        detail.setLiked(true);

        when(itineraryService.likeCommunityItinerary(101L, 801L)).thenReturn(detail);

        mockMvc.perform(post("/api/itineraries/community/801/like")
                        .header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(801))
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(9));

        verify(itineraryService).likeCommunityItinerary(101L, 801L);
    }

    @Test
    void unlikeCommunityItineraryUsesJwtUserId() throws Exception {
        CommunityItineraryDetailVO detail = new CommunityItineraryDetailVO();
        detail.setId(801L);
        detail.setLikeCount(8L);
        detail.setLiked(false);

        when(itineraryService.unlikeCommunityItinerary(101L, 801L)).thenReturn(detail);

        mockMvc.perform(delete("/api/itineraries/community/801/like")
                        .header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(801))
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.likeCount").value(8));

        verify(itineraryService).unlikeCommunityItinerary(101L, 801L);
    }

    @Test
    void updatePublicStatusUsesJwtUserId() throws Exception {
        PublicStatusReqDTO req = new PublicStatusReqDTO();
        req.setIsPublic(true);

        ItineraryVO itineraryVO = buildItinerary(501L, 240, "Public itinerary");
        itineraryVO.setIsPublic(true);
        when(itineraryService.updatePublicStatus(eq(101L), eq(501L), any(PublicStatusReqDTO.class))).thenReturn(itineraryVO);

        mockMvc.perform(patch("/api/itineraries/501/public")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(501))
                .andExpect(jsonPath("$.isPublic").value(true));

        verify(itineraryService).updatePublicStatus(eq(101L), eq(501L), any(PublicStatusReqDTO.class));
    }

    @Test
    void adminUsersEndpointUsesJwtRoleForAuthorization() throws Exception {
        AdminUserVO adminUser = new AdminUserVO();
        adminUser.setId(101L);
        adminUser.setUsername("admin");
        adminUser.setNickname("admin");
        adminUser.setRole(1);
        adminUser.setStatus(1);

        Page<AdminUserVO> page = new Page<>(1, 10);
        page.setRecords(List.of(adminUser));
        page.setTotal(1);

        when(adminService.getUserPage(1, 10, null)).thenReturn(page);

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", bearerToken)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records[0].id").value(101))
                .andExpect(jsonPath("$.records[0].role").value(1))
                .andExpect(jsonPath("$.total").value(1));

        verify(adminService).getUserPage(1, 10, null);
    }

    @Test
    void adminCanCreatePoi() throws Exception {
        Poi poi = new Poi();
        poi.setName("City Museum");
        poi.setCategory("museum");
        poi.setStayDuration(90);
        poi.setPriorityScore(new BigDecimal("4.5"));

        Poi created = new Poi();
        created.setId(700L);
        created.setName("City Museum");
        created.setCategory("museum");
        created.setStayDuration(90);
        created.setPriorityScore(new BigDecimal("4.5"));

        when(adminService.createPoi(any(Poi.class))).thenReturn(created);

        mockMvc.perform(post("/api/admin/pois")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(poi)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(700))
                .andExpect(jsonPath("$.name").value("City Museum"));

        verify(adminService).createPoi(any(Poi.class));
    }

    @Test
    void adminCanFreezePoi() throws Exception {
        mockMvc.perform(patch("/api/admin/pois/700/status")
                        .header("Authorization", bearerToken)
                        .param("temporarilyClosed", "1")
                        .param("statusNote", "maintenance"))
                .andExpect(status().isOk());

        verify(adminService).updatePoiTemporaryStatus(700L, 1, "maintenance");
    }

    @Test
    void adminCanDeletePoi() throws Exception {
        mockMvc.perform(delete("/api/admin/pois/700")
                        .header("Authorization", bearerToken))
                .andExpect(status().isOk());

        verify(adminService).deletePoi(700L);
    }

    @Test
    void adminEndpointUsesCurrentDatabaseRoleInsteadOfJwtRole() throws Exception {
        when(userMapper.selectById(101L)).thenReturn(buildUser(101L, "tester", "Test Nickname", 0, 1));

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", bearerToken)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isForbidden());
    }

    @Test
    void frozenUserCannotAccessProtectedEndpointEvenWithValidToken() throws Exception {
        when(userMapper.selectById(101L)).thenReturn(buildUser(101L, "tester", "Test Nickname", 1, 0));

        mockMvc.perform(get("/api/users/me").header("Authorization", bearerToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").isString());
    }

    private UserSessionVO buildSession(Long id, String username, String nickname, Integer role, String token) {
        UserSessionVO vo = new UserSessionVO();
        vo.setId(id);
        vo.setUsername(username);
        vo.setNickname(nickname);
        vo.setRole(role);
        vo.setToken(token);
        return vo;
    }

    private ItineraryVO buildItinerary(Long id, Integer totalDuration, String reason) {
        ItineraryVO vo = new ItineraryVO();
        vo.setId(id);
        vo.setTotalDuration(totalDuration);
        vo.setTotalCost(new BigDecimal("99.00"));
        vo.setRecommendReason(reason);
        return vo;
    }

    private SegmentRouteGuideVO buildSegmentRouteGuide(String summary) {
        SegmentRouteStepVO step = new SegmentRouteStepVO();
        step.setStepOrder(1);
        step.setType("walk");
        step.setInstruction("Walk to the next stop");
        step.setDistanceMeters(320);
        step.setDurationMinutes(5);
        step.setPathPoints(List.of(buildRoutePoint("30.6573", "104.0817"), buildRoutePoint("30.6581", "104.0792")));

        SegmentRouteGuideVO guide = new SegmentRouteGuideVO();
        guide.setSummary(summary);
        guide.setTransportMode("walk");
        guide.setDurationMinutes(5);
        guide.setDistanceKm(new BigDecimal("0.3"));
        guide.setDetailAvailable(true);
        guide.setSteps(List.of(step));
        guide.setPathPoints(List.of(buildRoutePoint("30.6573", "104.0817"), buildRoutePoint("30.6581", "104.0792")));
        guide.setSource("provider");
        return guide;
    }

    private RoutePathPointVO buildRoutePoint(String latitude, String longitude) {
        RoutePathPointVO point = new RoutePathPointVO();
        point.setLatitude(new BigDecimal(latitude));
        point.setLongitude(new BigDecimal(longitude));
        return point;
    }

    private User buildUser(Long id, String username, String nickname, Integer role, Integer status) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setNickname(nickname);
        user.setRole(role);
        user.setStatus(status);
        return user;
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        JwtProperties jwtProperties() {
            JwtProperties properties = new JwtProperties();
            properties.setSecret("test-jwt-secret-test-jwt-secret-123456");
            properties.setExpirationHours(24);
            return properties;
        }

        @Bean
        JwtUtil jwtUtil(JwtProperties jwtProperties) {
            return new JwtUtil(jwtProperties);
        }
    }
}
