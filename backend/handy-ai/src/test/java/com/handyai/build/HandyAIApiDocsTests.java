package com.handyai.build;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** The generated OpenAPI description and Swagger UI. */
@SpringBootTest
@AutoConfigureMockMvc
class HandyAIApiDocsTests {

    @Autowired
    private MockMvc mvc;

    @Test
    void describesTheApiAsOpenApi3() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value(org.hamcrest.Matchers.startsWith("3.")))
                .andExpect(jsonPath("$.info.title").value("HandyAI API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/auth/login'].post").exists())
                .andExpect(jsonPath("$.paths['/api/tools/semantic-search'].get").exists())
                .andExpect(jsonPath("$.paths['/api/me/interests'].put").exists())
                .andExpect(jsonPath("$.tags[?(@.name == 'Tools')]").exists());
    }

    @Test
    void servesSwaggerUi() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
