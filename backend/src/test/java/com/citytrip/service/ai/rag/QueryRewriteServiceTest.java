package com.citytrip.service.ai.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QueryRewriteServiceTest {

    @Test
    void rewriteShouldExpandCommonChengduPoiAliasesBeyondWanXiangCheng() {
        QueryRewriteService service = new QueryRewriteService(new PoiAliasResolver());

        List<String> rewrites = service.rewrite("自然博物馆", "成都");

        assertThat(rewrites).contains("自然博物馆", "成都自然博物馆");
        assertThat(rewrites).anyMatch(item -> item.contains("博物馆"));
    }

    @Test
    void rewriteShouldExpandShortNamesForZooAndShoppingMall() {
        QueryRewriteService service = new QueryRewriteService(new PoiAliasResolver());

        List<String> zooRewrites = service.rewrite("动物园", "成都");
        List<String> mallRewrites = service.rewrite("万象城", "成都");

        assertThat(zooRewrites).contains("成都动物园");
        assertThat(mallRewrites).contains("成都万象城");
        assertThat(mallRewrites).anyMatch(item -> item.contains("购物中心") || item.contains("商场"));
    }
}
