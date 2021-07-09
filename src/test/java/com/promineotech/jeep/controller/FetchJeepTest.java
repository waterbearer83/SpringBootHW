package com.promineotech.jeep.controller;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.doThrow;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import com.promineotech.jeep.Constants;
import com.promineotech.jeep.controller.support.FetchJeepTestSupport;
import com.promineotech.jeep.entity.Jeep;
import com.promineotech.jeep.entity.JeepModel;
import com.promineotech.jeep.service.JeepSalesService;

class FetchJeepTest {

  @Nested
  @SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
  @ActiveProfiles("test")
  @Sql(
      scripts = {"classpath:flyway/migrations/V1.0__Jeep_Schema.sql",
      "classpath:flyway/migrations/V1.1__Jeep_Data.sql"}, 
      config = @SqlConfig(encoding = "utf-8"))
  class TestsThatDoNotPolluteTheApplicationContext extends FetchJeepTestSupport { 
    
    @Test
  void testThatJeepsAreReturnedWhenAValidModelAndTrimAreSupplied() {
    JeepModel model = JeepModel.WRANGLER;
    String trim = "Sport";
    String uri = String.format("%s?model=%s&trim=%s", getBaseUriForJeeps(), model, trim);
    
    ResponseEntity<List<Jeep>> response = getRestTemplate().exchange(uri, HttpMethod.GET, null, 
        new ParameterizedTypeReference<>() {});
    
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    
    List<Jeep> actual = response.getBody();
    List<Jeep> expected = buildExpected();
  
    assertThat(actual).isEqualTo(expected);
  }
  
  @Test
  void testThatErrorMessageIsReturnedWhenAUnknownTrimIsSupplied() {
    JeepModel model = JeepModel.WRANGLER;
    String trim = "Unknown value";
    String uri = String.format("%s?model=%s&trim=%s", getBaseUriForJeeps(), model, trim);
    
    ResponseEntity<Map<String, Object>> response = getRestTemplate().exchange(uri, HttpMethod.GET, null, 
        new ParameterizedTypeReference<>() {});
    
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    Map<String, Object> error = response.getBody();
   assertErrorMessageValid(error, HttpStatus.NOT_FOUND);
  }
  
  @ParameterizedTest
  @MethodSource("com.promineotech.jeep.controller.FetchJeepTest#parametersForInvalidInput")
  void testThatErrorMessageIsReturnedWhenAInvalidTrimIsSupplied(String model, String trim, String reason) {
    String uri = String.format("%s?model=%s&trim=%s", getBaseUriForJeeps(), model, trim);
    
    ResponseEntity<Map<String, Object>> response = getRestTemplate().exchange(uri, HttpMethod.GET, null, 
        new ParameterizedTypeReference<>() {});
    
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    Map<String, Object> error = response.getBody();
   assertErrorMessageValid(error, HttpStatus.BAD_REQUEST);
  }
  }
  
  static Stream<Arguments> parametersForInvalidInput() {
    return Stream.of(
        arguments("WRANGLER", "@&#*^#%", "Trim contains non-alpha-numeric characters"),
        arguments("WRANGLER", "C".repeat(Constants.TRIM_MAX_LENGTH + 1), "Trim length too long"),
        arguments("INVALID", "Sport", "Model is not enum value"));
  }
  
  @Nested
  @SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
  @ActiveProfiles("test")
  @Sql(
      scripts = {"classpath:flyway/migrations/V1.0__Jeep_Schema.sql",
      "classpath:flyway/migrations/V1.1__Jeep_Data.sql"}, 
      config = @SqlConfig(encoding = "utf-8"))
  class TestsThatPolluteTheApplicationContext extends FetchJeepTestSupport {
    
    @MockBean
    private JeepSalesService jeepSalesService;
    
    @Test
    void testThatAnUnplannedErrorResultsInA500Status() {
      JeepModel model = JeepModel.WRANGLER;
      String trim = "Invalid";
      String uri = String.format("%s?model=%s&trim=%s", getBaseUriForJeeps(), model, trim);
      
      doThrow(new RuntimeException("Ouch!")).when(jeepSalesService).fetchJeeps(model,trim);
      
      ResponseEntity<Map<String, Object>> response = getRestTemplate().exchange(uri, HttpMethod.GET, null, 
          new ParameterizedTypeReference<>() {});
      
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
      Map<String, Object> error = response.getBody();
     assertErrorMessageValid(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }
}
