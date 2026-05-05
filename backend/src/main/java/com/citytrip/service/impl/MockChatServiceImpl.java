package com.citytrip.service.impl;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.vo.ChatStatusVO;
import com.citytrip.model.vo.ChatVO;
import com.citytrip.service.ChatService;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class MockChatServiceImpl implements ChatService {

    @Override
    public ChatStatusVO getStatus() {
        ChatStatusVO vo = new ChatStatusVO();
        vo.setProvider("mock");
        vo.setConfigured(true);
        vo.setRealModelAvailable(false);
        vo.setFallbackToMock(true);
        vo.setTimeoutSeconds(0);
        vo.setModel("mock");
        vo.setBaseUrl("local-mock");
        vo.setToolReady(false);
        vo.setGeoReady(false);
        vo.setEmbeddingReady(false);
        vo.setRerankReady(false);
        vo.setWarnings(List.of());
        vo.setMessage("当前聊天服务正在使用本地规则兜底响应。");
        return vo;
    }

    @Override
    public ChatVO answerQuestion(ChatReqDTO req) {
        String q = req.getQuestion() != null ? req.getQuestion() : "";
        ChatReqDTO.ChatContext ctx = req.getContext();

        ChatVO vo = new ChatVO();
        String answer = "当前处于本地规则兜底模式，我会基于成都旅游知识库为你提供基础建议。";
        List<String> tips = Arrays.asList("成都有哪些适合拍照的点位？", "武侯祠有什么历史背景？");

        if (q.contains("拍照") || q.contains("出片") || q.contains("机位")) {
            answer = "如果你想在成都拍出好看的照片，建议早上去武侯祠、下午去东郊记忆，晚上再补一组锦里的夜景。";
            tips = Arrays.asList("锦里晚上几点去比较合适？", "东郊记忆需要门票吗？");
        } else if (q.contains("雨天") || q.contains("下雨")) {
            answer = "雨天更适合安排博物馆、书店和商场类室内点位，比如成都博物馆、四川博物院或太古里周边。";
            tips = Arrays.asList("成都有哪些室内可逛点位？", "雨天路线怎么减少步行？");
        } else if (q.contains("历史") || q.contains("文化") || q.contains("武侯祠") || q.contains("杜甫")) {
            answer = "如果你更偏爱历史文化，武侯祠、杜甫草堂和成都博物馆会是很稳妥的主线组合。";
            tips = Arrays.asList("武侯祠游玩大约要多久？", "除了杜甫草堂还有哪些名人故居？");
        } else if (q.contains("春熙路") || q.contains("太古里") || q.contains("购物") || q.contains("逛街")) {
            answer = "春熙路和太古里更适合安排在下午到傍晚，兼顾购物、咖啡和夜景打卡。";
            tips = Arrays.asList("春熙路附近有什么值得吃的？", "IFS 熊猫最佳拍照点在哪？");
        } else if (q.contains("亲子") || q.contains("带孩子") || q.contains("小孩")) {
            answer = "亲子出行可优先考虑大熊猫基地、自然博物馆和海洋馆，路线不要排得太满。";
            tips = Arrays.asList("熊猫基地最好几点出发？", "自然博物馆周一开放吗？");
        } else if (q.contains("夜游") || q.contains("晚上") || q.contains("夜市")) {
            answer = "夜游可以考虑九眼桥、锦里或建设路夜市，注意返程交通与人流高峰。";
            tips = Arrays.asList("九眼桥适合几点去？", "建设路夜市有什么必吃推荐？");
        } else {
            answer = "已收到你的问题：“" + q + "”。当前我会基于本地规则和你的偏好给出基础的成都行程建议。";
        }

        if (ctx != null) {
            StringBuilder suffix = new StringBuilder();
            if (Boolean.TRUE.equals(ctx.getRainy()) && !q.contains("雨")) {
                suffix.append("（提示：你的行程设置里包含雨天条件，建议优先选择室内点位。） ");
            }
            if ("亲子".equals(ctx.getCompanionType()) && !q.contains("亲子") && !q.contains("孩子")) {
                suffix.append("（提示：亲子出行请适当控制步行强度并预留休息时间。） ");
            }
            if (ctx.getPreferences() != null && ctx.getPreferences().contains("拍照") && !q.contains("拍照")) {
                suffix.append("（提示：你当前有拍照偏好，可以优先挑选光线和建筑风格更出片的点位。）");
            }
            if (suffix.length() > 0) {
                answer += "\n\n" + suffix;
            }
        }

        vo.setAnswer(answer);
        vo.setRelatedTips(tips);
        return vo;
    }
}
