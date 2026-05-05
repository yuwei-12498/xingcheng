package com.citytrip.service.application.community;

import com.citytrip.assembler.ItinerarySummaryAssembler;
import com.citytrip.mapper.CommunityCommentMapper;
import com.citytrip.mapper.CommunityLikeMapper;
import com.citytrip.mapper.UserMapper;
import com.citytrip.service.impl.CommunityItineraryCacheService;
import com.citytrip.service.persistence.itinerary.SavedItineraryCodec;
import com.citytrip.service.persistence.itinerary.SavedItineraryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

class CommunityItineraryQueryServiceBeanCreationTest {

    @Test
    void springShouldCreateCommunityItineraryQueryServiceFromInjectedDependencies() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(SavedItineraryRepository.class, () -> mock(SavedItineraryRepository.class));
            context.registerBean(CommunityCommentMapper.class, () -> mock(CommunityCommentMapper.class));
            context.registerBean(CommunityLikeMapper.class, () -> mock(CommunityLikeMapper.class));
            context.registerBean(UserMapper.class, () -> mock(UserMapper.class));
            context.registerBean(SavedItineraryCodec.class, () -> mock(SavedItineraryCodec.class));
            context.registerBean(ItinerarySummaryAssembler.class, ItinerarySummaryAssembler::new);
            context.registerBean(CommunityItineraryCacheService.class,
                    () -> new CommunityItineraryCacheService(false, null, null, new ObjectMapper()));
            context.registerBean(CommunitySemanticSearchService.class, () -> mock(CommunitySemanticSearchService.class));
            context.registerBean(CommunityItineraryQueryService.class);

            assertThatCode(context::refresh)
                    .doesNotThrowAnyException();

            assertThat(context.getBean(CommunityItineraryQueryService.class)).isNotNull();
        }
    }
}
