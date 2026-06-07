package com.unique.examine.plat.manage.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;

import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.plat.manage.enums.PlatErrorCode;
import com.unique.examine.plat.manage.service.PlatformModuleInitializationWriter.InitializationCommand;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;

class MybatisPlatformModuleInitializationWriterTest {

    @Test
    void shouldMapSqlFailureToSystemInitFailure() throws SQLException {
        SqlSessionTemplate sqlSessionTemplate = mock(SqlSessionTemplate.class);
        Connection connection = mock(Connection.class);
        when(sqlSessionTemplate.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenThrow(new SQLException("boom"));

        MybatisPlatformModuleInitializationWriter writer =
                new MybatisPlatformModuleInitializationWriter(sqlSessionTemplate);

        InitializationCommand command = new InitializationCommand(1L, 2L, 3L, "admin",
                LocalDateTime.parse("2026-06-06T10:00:00"));

        assertThatThrownBy(() -> writer.initialize(command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PlatErrorCode.SYSTEM_INIT_FAILED);
    }
}
