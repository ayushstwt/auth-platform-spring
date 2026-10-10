package com.ayshriv.authplatform.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ayshriv.authplatform.config.JacksonConfig;
import com.ayshriv.authplatform.constants.ErrorMessages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ControllerExceptionHandlerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new JacksonConfig().objectMapper();

    record DummyRequest(@NotBlank(message = "must not be blank") String name) {}

    @RestController
    @Validated
    @RequestMapping("/test/errors")
    static class DummyErrorController {

        @GetMapping("/not-found")
        public void notFound() {
            throw new ResourceNotFoundException(ErrorMessages.E002, "42");
        }

        @GetMapping("/conflict")
        public void conflict() {
            throw new ResourceAlreadyExistException(ErrorMessages.E003);
        }

        @GetMapping("/unauthorized")
        public void unauthorized() {
            throw new UnauthorizedException(ErrorMessages.E004);
        }

        @GetMapping("/forbidden")
        public void forbidden() {
            throw new ForbiddenException(ErrorMessages.E006);
        }

        @PostMapping("/validation")
        public void validation(@Valid @RequestBody DummyRequest request) {
        }

        @GetMapping("/param")
        public void param(@RequestParam String requiredParam) {
        }

        @GetMapping("/type-mismatch/{id}")
        public void typeMismatch(@PathVariable Long id) {
        }

        @GetMapping("/constraint")
        public void constraint(@RequestParam @Positive(message = "E005") int count) {
        }

        @GetMapping("/unhandled-500")
        public void unhandled() {
            throw new RuntimeException("Sensitive database credentials leaked: pass123");
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DummyErrorController())
                .setControllerAdvice(new ControllerExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Should handle ResourceNotFoundException and return 404 envelope")
    void handleNotFound() throws Exception {
        mockMvc.perform(get("/test/errors/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is("error")))
                .andExpect(jsonPath("$.errorCode", is("E002")))
                .andExpect(jsonPath("$.message", is("User with identifier 42 not found")))
                .andExpect(jsonPath("$.path", is("/test/errors/not-found")));
    }

    @Test
    @DisplayName("Should handle ResourceAlreadyExistException and return 409 envelope")
    void handleConflict() throws Exception {
        mockMvc.perform(get("/test/errors/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is("error")))
                .andExpect(jsonPath("$.errorCode", is("E003")))
                .andExpect(jsonPath("$.message", is("Resource already exists")));
    }

    @Test
    @DisplayName("Should handle UnauthorizedException and return 401 envelope")
    void handleUnauthorized() throws Exception {
        mockMvc.perform(get("/test/errors/unauthorized"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is("error")))
                .andExpect(jsonPath("$.errorCode", is("E004")))
                .andExpect(jsonPath("$.message", is("Authentication required")));
    }

    @Test
    @DisplayName("Should handle ForbiddenException and return 403 envelope")
    void handleForbidden() throws Exception {
        mockMvc.perform(get("/test/errors/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is("error")))
                .andExpect(jsonPath("$.errorCode", is("E006")))
                .andExpect(jsonPath("$.message", is("Access denied")));
    }

    @Test
    @DisplayName("Should handle MethodArgumentNotValidException with per-field errors")
    void handleValidation() throws Exception {
        mockMvc.perform(post("/test/errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DummyRequest(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is("error")))
                .andExpect(jsonPath("$.errorCode", is("E005")))
                .andExpect(jsonPath("$.message", is("Validation failed")))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field", is("name")))
                .andExpect(jsonPath("$.errors[0].message", is("must not be blank")));
    }

    @Test
    @DisplayName("Should handle HttpRequestMethodNotSupportedException and return 405 envelope")
    void handleMethodNotSupported() throws Exception {
        mockMvc.perform(post("/test/errors/not-found"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status", is("error")))
                .andExpect(jsonPath("$.errorCode", is("E008")));
    }

    @Test
    @DisplayName("Should handle MissingServletRequestParameterException and return 400 envelope")
    void handleMissingParam() throws Exception {
        mockMvc.perform(get("/test/errors/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is("error")))
                .andExpect(jsonPath("$.errorCode", is("E010")));
    }

    @Test
    @DisplayName("Should handle MethodArgumentTypeMismatchException and return 400 envelope")
    void handleTypeMismatch() throws Exception {
        mockMvc.perform(get("/test/errors/type-mismatch/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is("error")))
                .andExpect(jsonPath("$.errorCode", is("E011")));
    }

    @Test
    @DisplayName("Should handle HttpMessageNotReadableException and return 400 envelope")
    void handleMalformedJson() throws Exception {
        mockMvc.perform(post("/test/errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is("error")))
                .andExpect(jsonPath("$.errorCode", is("E007")));
    }

    @Test
    @DisplayName("Should handle unexpected 500 error and sanitize internal details")
    void handleUnhandledException() throws Exception {
        mockMvc.perform(get("/test/errors/unhandled-500"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status", is("error")))
                .andExpect(jsonPath("$.errorCode", is("E500")))
                .andExpect(jsonPath("$.message", is("An unexpected internal error occurred")))
                .andExpect(jsonPath("$.message", not(org.hamcrest.Matchers.containsString("pass123"))));
    }
}
