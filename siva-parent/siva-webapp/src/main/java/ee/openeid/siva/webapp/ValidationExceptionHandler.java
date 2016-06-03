package ee.openeid.siva.webapp;

import ee.openeid.siva.validation.exception.MalformedDocumentException;
import ee.openeid.siva.validation.exception.ValidationServiceException;
import ee.openeid.siva.webapp.response.erroneus.RequestValidationError;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ValidationExceptionHandler {

    @Autowired
    private MessageSource messageSource;

    @ExceptionHandler(MalformedDocumentException.class)
    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    public RequestValidationError handleMalformedDocumentException(MalformedDocumentException e) {
        RequestValidationError requestValidationError = new RequestValidationError();
        requestValidationError.addFieldError("document", getMessage("validation.error.message.document.malformed"));
        return requestValidationError;
    }

    @ExceptionHandler(ValidationServiceException.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleValidationServiceException(ValidationServiceException e) {
        return getMessage("validation.service.error.message");
    }

    private String getMessage(String key) {
        return messageSource.getMessage(key, null, null);
    }

}