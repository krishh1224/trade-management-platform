package com.team5.strategies.web;

import static com.team5.strategies.StrategyFixtures.OWNER;
import static com.team5.strategies.StrategyFixtures.maCrossover;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team5.auth.AuthWebMvcConfig;
import com.team5.auth.SecurityConfig;
import com.team5.strategies.domain.StrategyRow;
import com.team5.strategies.domain.StrategyStatus;
import com.team5.strategies.dto.StrategyChartResponse;
import com.team5.strategies.dto.StrategyResponse;
import com.team5.strategies.domain.StrategyType;
import java.util.List;
import com.team5.strategies.exception.StrategyNotFoundException;
import com.team5.strategies.service.StrategyChartService;
import com.team5.strategies.service.StrategyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StrategyController.class)
@Import({SecurityConfig.class, AuthWebMvcConfig.class})
class StrategyControllerTest {

    private static final String BASE = "/api/v1/accounts/" + OWNER + "/strategies";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StrategyService strategyService;

    @MockBean
    private StrategyChartService chartService;

    @Test
    void create_returns201WithTheStrategy() throws Exception {
        when(strategyService.create(eq(OWNER), eq(OWNER), any())).thenReturn(StrategyResponse.from(maCrossover(9L)));

        mockMvc.perform(post(BASE).with(customer()).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"INFY trend","symbol":"INFY.NS","type":"MA_CROSSOVER","shortWindow":2,"longWindow":4,
                 "quantity":10,"maxSpend":100000,"maxPosition":50}"""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.strategyId").value(9))
            .andExpect(jsonPath("$.status").value("ENABLED"))
            .andExpect(jsonPath("$.remainingSpend").value(100000.0));
    }

    @Test
    void create_withoutLimits_is422() throws Exception {
        mockMvc.perform(post(BASE).with(customer()).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"no limits","symbol":"INFY.NS","type":"MA_CROSSOVER","shortWindow":2,"longWindow":4,
                 "quantity":10}"""))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("VAL-422"));
        verifyNoInteractions(strategyService);
    }

    @Test
    void disable_returnsTheStrategyNowDisabled() throws Exception {
        StrategyRow disabled = maCrossover(9L);
        disabled.setStatus(StrategyStatus.DISABLED);
        disabled.setStatusReason("Disabled by the customer");
        when(strategyService.disable(OWNER, OWNER, 9L)).thenReturn(StrategyResponse.from(disabled));

        mockMvc.perform(post(BASE + "/9/disable").with(customer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DISABLED"));
        verify(strategyService).disable(OWNER, OWNER, 9L);
    }

    @Test
    void unknownStrategy_is404() throws Exception {
        when(strategyService.get(OWNER, OWNER, 9L)).thenThrow(new StrategyNotFoundException());

        mockMvc.perform(get(BASE + "/9").with(customer()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("STR-404"));
    }

    @Test
    void delete_is204() throws Exception {
        mockMvc.perform(delete(BASE + "/9").with(customer()))
            .andExpect(status().isNoContent());
        verify(strategyService).delete(OWNER, OWNER, 9L);
    }

    @Test
    void chart_returnsPointsAndMarkers() throws Exception {
        when(chartService.chart(OWNER, OWNER, 9L)).thenReturn(
            new StrategyChartResponse(9L, "INFY.NS", StrategyType.MA_CROSSOVER, 5, List.of(), List.of()));

        mockMvc.perform(get(BASE + "/9/chart").with(customer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requiredPrices").value(5))
            .andExpect(jsonPath("$.points").isArray());
    }

    @Test
    void noToken_is401() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        verifyNoInteractions(strategyService);
    }

    private static JwtRequestPostProcessor customer() {
        return jwt().jwt(token -> token.claim("accountId", OWNER).claim("iss", "auth-service"));
    }
}
