package io.saira.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import io.saira.config.SecurityConfig;

/** Тесты SPA-контроллера. */
@WebMvcTest(SpaController.class)
@Import(SecurityConfig.class)
class SpaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET / — перенаправляет на index.html")
    void root_forwardsToIndexHtml() throws Exception {
        // when & then
        mockMvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
    }

    @Test
    @DisplayName("GET /traces — перенаправляет на index.html")
    void traces_forwardsToIndexHtml() throws Exception {
        // when & then
        mockMvc.perform(get("/traces")).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
    }

    @Test
    @DisplayName("GET /dashboard — перенаправляет на index.html")
    void dashboard_forwardsToIndexHtml() throws Exception {
        // when & then
        mockMvc.perform(get("/dashboard")).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
    }
}
