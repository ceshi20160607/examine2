package com.unique.examine.collab.recordcomment;

import com.unique.examine.collab.recordcomment.ai.AiRecordCommentReadAdapter;
import com.unique.examine.collab.recordteam.RecordTeamRepository;
import com.unique.examine.core.ai.AiRecordCommentReadFacade;
import com.unique.examine.core.api.ResultNotificationFacade;
import com.unique.examine.core.api.RuntimeActiveMemberFacade;
import com.unique.examine.core.runtime.RuntimeRecordAccessFacade;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
public class RecordCommentConfiguration {
    @Bean
    @ConditionalOnMissingBean(RecordCommentRepository.class)
    public RecordCommentRepository recordCommentRepository(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager
    ) {
        return new JdbcRecordCommentRepository(jdbcTemplate, transactionManager);
    }

    @Bean
    @ConditionalOnMissingBean(RecordCommentService.class)
    public RecordCommentService recordCommentService(
            RecordCommentRepository repository,
            RuntimeRecordAccessFacade recordAccess,
            RecordTeamRepository recordTeams,
            RuntimeActiveMemberFacade activeMembers,
            ResultNotificationFacade notifications
    ) {
        return new RecordCommentService(repository, recordAccess, recordTeams, activeMembers, notifications);
    }

    @Bean
    @ConditionalOnMissingBean(AiRecordCommentReadFacade.class)
    public AiRecordCommentReadFacade aiRecordCommentReadFacade(
            RecordCommentService comments
    ) {
        return new AiRecordCommentReadAdapter(comments);
    }
}
