package ru.yandex.practicum.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TokenEndpointTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void clientCredentialsIssuesAPaymentToken() throws Exception {
        mvc.perform(post("/oauth2/token")
                        .with(httpBasic("storefront", "storefront-secret"))
                        .param("grant_type", "client_credentials")
                        .param("scope", "payment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("Bearer"));
    }

    @Test
    void wrongSecretIsRejected() throws Exception {
        mvc.perform(post("/oauth2/token")
                        .with(httpBasic("storefront", "wrong"))
                        .param("grant_type", "client_credentials")
                        .param("scope", "payment"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicKeysAreAvailableWithoutAClient() throws Exception {
        mvc.perform(get("/.well-known/oauth-authorization-server"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value("http://localhost:9000"));

        mvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys").isArray());
    }

    @Test
    void anythingBesidesTheProtocolEndpointsIsClosed() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isForbidden());
    }
}
