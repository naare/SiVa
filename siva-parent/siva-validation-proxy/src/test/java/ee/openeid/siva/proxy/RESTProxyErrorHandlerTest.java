package ee.openeid.siva.proxy;

import ee.openeid.siva.proxy.http.RESTValidationProxyException;
import ee.openeid.siva.proxy.http.RestProxyErrorHandler;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RESTProxyErrorHandlerTest {

    private RestProxyErrorHandler restProxyErrorHandler;

    @Mock
    private ClientHttpResponse httpResponse;

    @Before
    public void setUp() {
        restProxyErrorHandler = new RestProxyErrorHandler();
    }

    @Test
    public void errorHandlerDoesNotHaveErrorWhenGivenHttpResponseHasNoError() throws Exception{
        when(httpResponse.getStatusCode()).thenReturn(HttpStatus.OK);
        assertFalse(restProxyErrorHandler.hasError(httpResponse));
    }

    @Test
    public void errorHandlerHasErrorWhenGivenClientErrorCode() throws Exception {
        when(httpResponse.getStatusCode()).thenReturn(HttpStatus.BAD_REQUEST);
        assertTrue(restProxyErrorHandler.hasError(httpResponse));
    }

    @Test
    public void errorHandlerHasErrorWhenGivenServerErrorCode() throws Exception{
        when(httpResponse.getStatusCode()).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR);
        assertTrue(restProxyErrorHandler.hasError(httpResponse));
    }

    @Test
    public void errorHandlerThrowsRESTValidationProxyExceptionWithInfoFromResponseBodyWhenHandlingError() throws Exception {
        createResponseWithBody();
        try {
            restProxyErrorHandler.handleError(httpResponse);
        } catch (RESTValidationProxyException e) {
            assertEquals("some key", e.getErrorKey());
            assertEquals("some message", e.getErrorMessage());
            assertTrue(HttpStatus.BAD_REQUEST == e.getHttpStatus());
        }
    }

    private ClientHttpResponse createResponseWithBody() throws IOException {
        when(httpResponse.getStatusCode()).thenReturn(HttpStatus.BAD_REQUEST);
        when(httpResponse.getStatusText()).thenReturn("Bad Request");
        String body = "{\"key\":\"some key\",\"message\":\"some message\"}";
        when(httpResponse.getBody()).thenReturn(new ByteArrayInputStream(body.getBytes()));
        return httpResponse;
    }
}