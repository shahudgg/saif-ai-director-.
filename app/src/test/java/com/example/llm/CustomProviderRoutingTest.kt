package com.example.llm

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomProviderRoutingTest {
    @Test fun explicitEndpointWinsOverVendorKeyPrefix() {
        val profile = ProviderProfile.detectProviderFromKey("sk-ant-example", "https://my-provider.example/v1")
        assertEquals("Custom", profile.id)
        assertEquals("https://my-provider.example/v1", profile.baseUrl)
    }

    @Test fun namedProvidersStillUseTheirNormalProfiles() {
        assertEquals("OpenAI", ProviderProfile.detectProviderFromKey("sk-example", "OpenAI").id)
        assertEquals("Anthropic", ProviderProfile.detectProviderFromKey("sk-ant-example", "Anthropic").id)
        assertEquals("OpenRouter", ProviderProfile.detectProviderFromKey("sk-or-example", "OpenRouter").id)
    }
}
