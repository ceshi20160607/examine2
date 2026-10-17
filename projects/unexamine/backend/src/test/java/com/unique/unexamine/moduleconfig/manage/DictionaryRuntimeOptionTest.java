package com.unique.unexamine.moduleconfig.manage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.moduleconfig.manage.repository.DictionaryUsageRepository;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.system.base.service.SystemTenantBaseService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** 验证公共字典选项校验随发布变化生效，且不绕过字典可访问范围。 */
class DictionaryRuntimeOptionTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AuthenticatedContext context = mock(AuthenticatedContext.class);
    private final DictionaryConfigurationService service = spy(new DictionaryConfigurationService(
            null, null, null, null, mock(SystemTenantBaseService.class), null, json,
            mock(DictionaryUsageRepository.class)));

    private DictionaryModels.RuntimeDictionary options(String code, String status) throws Exception {
        return new DictionaryModels.RuntimeDictionary(1L, "stage", "阶段", false, 2L, 1, 0,
                List.of(json.readTree("{\"code\":\"" + code + "\",\"status\":\"" + status + "\"}")));
    }

    @Test void acceptsCurrentPublishedCode() throws Exception {
        doReturn(options("blue", "ACTIVE")).when(service).runtime(context, 1L, null);
        assertThatCode(() -> service.validateRuntimeOption(context, 1L, "blue")).doesNotThrowAnyException();
    }

    @Test void rejectsDisabledOrMissingCode() throws Exception {
        doReturn(options("blue", "DISABLED")).when(service).runtime(context, 1L, null);
        assertThatThrownBy(() -> service.validateRuntimeOption(context, 1L, "blue"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("已停用");
        assertThatThrownBy(() -> service.validateRuntimeOption(context, 1L, "green"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不存在");
    }

    @Test void reloadsOptionsAfterPublicationChanges() throws Exception {
        doReturn(options("blue", "ACTIVE"), options("green", "ACTIVE")).when(service).runtime(context, 1L, null);
        service.validateRuntimeOption(context, 1L, "blue");
        assertThatThrownBy(() -> service.validateRuntimeOption(context, 1L, "blue"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不存在");
    }

    @Test void unavailableDictionaryCannotUseAClientSuppliedValue() {
        doThrow(new DomainException("DICTIONARY_NOT_FOUND", "字典不存在或尚未发布", HttpStatus.NOT_FOUND))
                .when(service).runtime(context, 1L, null);
        assertThatThrownBy(() -> service.validateRuntimeOption(context, 1L, "blue"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("字典不可用");
    }

    @Test void validationRejectionsDoNotMarkPreviewTransactionForRollback() throws Exception {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        TransactionStatus transaction = mock(TransactionStatus.class);
        when(manager.getTransaction(any())).thenReturn(transaction);
        ProxyFactory factory = new ProxyFactory(service);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        DictionaryConfigurationService proxy = (DictionaryConfigurationService) factory.getProxy();
        doReturn(options("blue", "DISABLED")).when(service).runtime(context, 1L, null);
        assertThatThrownBy(() -> proxy.validateRuntimeOption(context, 1L, "blue"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> proxy.validateRuntimeSelection(context, 1L, List.of(), 2, false))
                .isInstanceOf(IllegalArgumentException.class);
        verify(manager, times(2)).commit(transaction);
        verify(manager, never()).rollback(any());
    }
}
