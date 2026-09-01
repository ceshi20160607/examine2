package com.unique.unexamine.foundation.manage.control;

import com.unique.unexamine.foundation.base.entity.CoreEventOutbox;

public interface FoundationCommandExtension {
    void afterCommit(CoreEventOutbox event);
}
