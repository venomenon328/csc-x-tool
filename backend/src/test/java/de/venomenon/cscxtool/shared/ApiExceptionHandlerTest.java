package de.venomenon.cscxtool.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.venomenon.cscxtool.data.RestoreRecoveryFailedException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class ApiExceptionHandlerTest {

    @Test
    void neverClaimsAKnownDataStateWhenTheRestoreRecoveryFailed() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/data/restore");

        ResponseEntity<ApiError> response = new ApiExceptionHandler().restoreRecoveryFailed(
                new RestoreRecoveryFailedException(new IllegalStateException("restore"), new IllegalStateException("recovery")), request
        );

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("RESTORE_RECOVERY_FAILED");
        assertThat(response.getBody().message()).contains("kann nicht best\u00e4tigt werden").doesNotContain("bleibt erhalten");
    }
}
