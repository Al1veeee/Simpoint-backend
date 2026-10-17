package ru.simpoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.simpoint.service.PasswordService;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.jwt.secret=integration-test-secret-integration-test-secret",
        "app.bootstrap.admin-password=" + SalesFlowIntegrationTest.ADMIN_PASSWORD
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class SalesFlowIntegrationTest {

    static final String ADMIN_PASSWORD = "admin-test-pass";
    private static final String SELLER_PASSWORD = "seller-test-pass";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordService passwordService;

    @BeforeEach
    void setSellerPassword() {
        passwordService.change("seller", SELLER_PASSWORD);
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mvc.perform(get("/api/v1/tariffs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publishedDemoPasswordsDoNotWork() throws Exception {
        for (String[] credentials : new String[][]{{"admin", "admin123"}, {"seller", "seller123"}}) {
            mvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"%s","password":"%s"}
                                    """.formatted(credentials[0], credentials[1])))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void fullSalesFlow() throws Exception {
        String token = login("seller", SELLER_PASSWORD);
        String adminToken = login("admin", ADMIN_PASSWORD);

        MvcResult batch = mvc.perform(post("/api/v1/sim-cards/batch")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operator":"MEGAFON","iccids":["8970199900000000001","8970199900000000002"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("IN_STOCK"))
                .andReturn();
        long simId = json(batch).get(0).get("id").asLong();

        mvc.perform(post("/api/v1/sim-cards/batch")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operator":"MEGAFON","iccids":["8970199900000000001"]}
                                """))
                .andExpect(status().isConflict());

        MvcResult tariffResult = mvc.perform(post("/api/v1/tariffs")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operator":"MEGAFON","name":"Тест 1000","monthlyPrice":650,
                                 "minutes":1000,"unlimitedInternet":true}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long tariffId = json(tariffResult).get("id").asLong();

        mvc.perform(post("/api/v1/tariffs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operator":"YOTA","name":"Нельзя продавцу","monthlyPrice":100}
                                """))
                .andExpect(status().isForbidden());

        MvcResult saleResult = mvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentMethod":"CARD","items":[
                                  {"serviceType":"SIM_SALE","operator":"MEGAFON","simCardId":%d,"tariffId":%d},
                                  {"serviceType":"IMEI_REPLACEMENT","operator":"MEGAFON","price":1500,"description":"IMEI"},
                                  {"serviceType":"PHONE_REPAIR","price":2000,"description":"Замена стекла"}
                                ]}
                                """.formatted(simId, tariffId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.employee").value("seller"))
                .andExpect(jsonPath("$.total").value(4150.0))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andReturn();
        long saleId = json(saleResult).get("id").asLong();

        mvc.perform(get("/api/v1/sim-cards/{id}", simId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SOLD"));

        mvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentMethod":"CASH","items":[
                                  {"serviceType":"IMEI_REPLACEMENT","operator":"MTS","price":1500}
                                ]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("IMEI")));

        String today = LocalDate.now(ZoneId.of("Europe/Moscow")).toString();
        mvc.perform(get("/api/v1/reports/sales")
                        .param("from", today)
                        .param("to", today)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salesCount").value(1))
                .andExpect(jsonPath("$.totalRevenue").value(4150.0))
                .andExpect(jsonPath("$.byOperator[0].operator").value("MEGAFON"))
                .andExpect(jsonPath("$.byOperator[0].revenue").value(2150.0));

        mvc.perform(post("/api/v1/sales/{id}/cancel", saleId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Клиент передумал"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mvc.perform(get("/api/v1/sim-cards/{id}", simId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.status").value("IN_STOCK"));

        mvc.perform(get("/api/v1/reports/sales")
                        .param("from", today)
                        .param("to", today)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.salesCount").value(0));
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        return json(result).get("token").asText();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
