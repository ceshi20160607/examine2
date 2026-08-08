package com.unique.examine.ai.provider;

import com.unique.examine.ai.domain.PlatformAiProvider;

/** Platform provider channel with no system/tenant secret scope. */
public interface PlatformAiProviderClient {
    AiProviderClient.Completion complete(
            PlatformAiProvider provider, AiProviderClient.Request request);
}
