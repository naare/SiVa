package ee.openeid.siva.webapp.request.validation.annotations;

import javax.validation.Constraint;
import javax.validation.Payload;
import javax.validation.ReportAsSingleViolation;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static ee.openeid.siva.webapp.request.validation.annotations.NotNullValidFilenamePattern.PATTERN;


@NotNull
@Pattern(regexp = PATTERN)
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy={})
@ReportAsSingleViolation
public @interface NotNullValidFilenamePattern {

    String PATTERN = "^[^*&%/\"\\\\:?]+$";

    String message() default "{validation.error.message.filename}";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

}