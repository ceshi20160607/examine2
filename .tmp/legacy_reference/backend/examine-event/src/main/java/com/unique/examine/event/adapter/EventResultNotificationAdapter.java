package com.unique.examine.event.adapter;

import com.unique.examine.core.api.ResultNotificationFacade;
import com.unique.examine.event.service.MessageTemplateService;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class EventResultNotificationAdapter implements ResultNotificationFacade {
    private final MessageTemplateService templates;

    public EventResultNotificationAdapter(MessageTemplateService templates) {
        this.templates = templates;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeliveryReceipt dispatch(Command command) {
        return templates.dispatch(command);
    }
}
