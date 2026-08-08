package com.unique.examine.event.api;

import com.unique.examine.core.api.ResultNotificationFacade;
import com.unique.examine.event.adapter.EventResultNotificationAdapter;
import com.unique.examine.event.config.EventJdbcConfiguration;
import com.unique.examine.event.service.MessageTemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.assertj.core.api.Assertions.assertThat;

class MessageTemplateApiContractTest {
    @Test
    void adminApiAndRequiresNewNotificationPortRemainOwnedByEvent() throws Exception {
        assertThat(MessageTemplateController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/api/v1/systems/{systemId}/admin/event/message-templates");
        assertThat(MessageTemplateController.class.getMethod("list", long.class, Object.class,
                jakarta.servlet.http.HttpServletRequest.class).getAnnotation(GetMapping.class)).isNotNull();
        assertThat(MessageTemplateController.class.getMethod("update", long.class, String.class,
                MessageTemplateService.UpdateCommand.class, Object.class,
                jakarta.servlet.http.HttpServletRequest.class).getAnnotation(PutMapping.class)).isNotNull();
        assertThat(MessageTemplateController.class.getMethod("publish", long.class, String.class,
                MessageTemplateController.PublishRequest.class, Object.class,
                jakarta.servlet.http.HttpServletRequest.class).getAnnotation(PostMapping.class).value())
                .containsExactly("/{templateCode}:publish");

        var dispatch = EventResultNotificationAdapter.class.getMethod(
                "dispatch", ResultNotificationFacade.Command.class);
        assertThat(dispatch.getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        var bean = EventJdbcConfiguration.class.getDeclaredMethod(
                "resultNotificationFacade", MessageTemplateService.class);
        assertThat(bean.getReturnType()).isEqualTo(ResultNotificationFacade.class);
        assertThat(bean.getAnnotation(Bean.class)).isNotNull();
    }
}
