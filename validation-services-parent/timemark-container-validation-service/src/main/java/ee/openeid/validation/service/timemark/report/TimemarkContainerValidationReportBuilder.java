/*
 * Copyright 2019 Riigi Infosüsteemide Amet
 *
 * Licensed under the EUPL, Version 1.1 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the Licence is
 * distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and limitations under the Licence.
 */

package ee.openeid.validation.service.timemark.report;

import ee.openeid.siva.validation.document.ValidationDocument;
import ee.openeid.siva.validation.document.report.Error;
import ee.openeid.siva.validation.document.report.*;
import ee.openeid.siva.validation.document.report.builder.ReportBuilderUtils;
import ee.openeid.siva.validation.service.signature.policy.properties.ValidationPolicy;
import eu.europa.esig.dss.enumerations.SignatureQualification;
import eu.europa.esig.dss.enumerations.SubIndication;
import org.apache.commons.lang3.StringUtils;
import org.digidoc4j.*;
import org.digidoc4j.exceptions.DigiDoc4JException;
import org.digidoc4j.impl.asic.asice.AsicESignature;
import org.digidoc4j.impl.ddoc.DDocContainer;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ee.openeid.siva.validation.document.report.builder.ReportBuilderUtils.*;
import static org.digidoc4j.X509Cert.SubjectName.CN;

public abstract class TimemarkContainerValidationReportBuilder {

    protected static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(TimemarkContainerValidationReportBuilder.class);

    protected static final String FULL_SIGNATURE_SCOPE = "FullSignatureScope";
    protected static final String FULL_DOCUMENT = "Digest of the document content";
    protected static final String XADES_FORMAT_PREFIX = "XAdES_BASELINE_";
    private static final String REPORT_INDICATION_INDETERMINATE = "INDETERMINATE";
    protected static final String BDOC_SIGNATURE_FORM = "ASiC-E";
    protected static final String DDOC_SIGNATURE_FORM_PREFIX = "DIGIDOC_XML_";
    protected static final String DDOC_HASHCODE_SIGNATURE_FORM_SUFFIX = "_hashcode";
    protected static final String HASHCODE_CONTENT_TYPE = "HASHCODE";

    Container container;
    private ValidationDocument validationDocument;
    private ValidationPolicy validationPolicy;
    ValidationResult validationResult;
    private boolean isReportSignatureEnabled;
    private Map<String, ValidationResult> signatureValidationResults;

    public TimemarkContainerValidationReportBuilder(Container container, ValidationDocument validationDocument, ValidationPolicy validationPolicy, ValidationResult validationResult, boolean isReportSignatureEnabled) {
        this.container = container;
        this.validationDocument = validationDocument;
        this.validationPolicy = validationPolicy;
        this.signatureValidationResults = validateAllSignatures(container);
        removeSignatureResults(validationResult);
        this.validationResult = validationResult;
        this.isReportSignatureEnabled = isReportSignatureEnabled;
    }

    static Warning createWarning(String content) {
        Warning warning = new Warning();
        warning.setContent(emptyWhenNull(content));
        return warning;
    }

    private static Warning mapDigidoc4JWarning(DigiDoc4JException digiDoc4JException) {
        Warning warning = new Warning();
        warning.setContent(emptyWhenNull(digiDoc4JException.getMessage()));
        return warning;
    }

    private static Error mapDigidoc4JException(DigiDoc4JException digiDoc4JException) {
        Error error = new Error();
        error.setContent(emptyWhenNull(digiDoc4JException.getMessage()));
        return error;
    }

    public Reports build() {
        ValidationConclusion validationConclusion = getValidationConclusion();
        if (!(container instanceof DDocContainer)) {
            processSignatureIndications(validationConclusion, validationPolicy.getName());
        }
        SimpleReport simpleReport = new SimpleReport(validationConclusion);
        DetailedReport detailedReport = new DetailedReport(validationConclusion, null);
        DiagnosticReport diagnosticReport = new DiagnosticReport(validationConclusion, null);
        return new Reports(simpleReport, detailedReport, diagnosticReport);
    }

    private void removeSignatureResults(ValidationResult validationResult) {
        removeSignatureErrors(validationResult);
        removeSignatureWarning(validationResult);
    }

    private void removeSignatureErrors(ValidationResult validationResult) {
        validationResult.getErrors().removeIf(exception -> signatureValidationResults.values().stream()
                .anyMatch(sigResult -> sigResult.getErrors().stream()
                        .anyMatch(e -> e.getMessage().equals(exception.getMessage()))));
    }

    private void removeSignatureWarning(ValidationResult validationResult) {
        validationResult.getWarnings().removeIf(exception -> signatureValidationResults.values().stream()
                .anyMatch(sigResult -> sigResult.getWarnings().stream()
                        .anyMatch(e -> e.getMessage().equals(exception.getMessage()))));
    }

    private Map<String, ValidationResult> validateAllSignatures(Container container) {
        Map<String, ValidationResult> results = new HashMap<>();
        for (Signature signature : container.getSignatures()) {
            results.put(signature.getUniqueId(), signature.validateSignature());
        }
        return results;
    }

    private ValidationConclusion getValidationConclusion() {
        ValidationConclusion validationConclusion = new ValidationConclusion();
        validationConclusion.setPolicy(createReportPolicy(validationPolicy));
        validationConclusion.setValidationTime(getValidationTime());
        validationConclusion.setSignatureForm(getSignatureForm());
        validationConclusion.setSignaturesCount(container.getSignatures().size());
        List<SignatureValidationData> signaturesValidationResult = createSignaturesForReport(container);
        validationConclusion.setSignatures(signaturesValidationResult);
        validationConclusion.setValidationWarnings(containerValidationWarnings());
        validationConclusion.setValidatedDocument(ReportBuilderUtils.createValidatedDocument(isReportSignatureEnabled, validationDocument.getName(), validationDocument.getBytes()));
        validationConclusion.setValidSignaturesCount(
                (int) validationConclusion.getSignatures()
                        .stream()
                        .filter(vd -> StringUtils.equals(vd.getIndication(), SignatureValidationData.Indication.TOTAL_PASSED.toString()))
                        .count());
        return validationConclusion;
    }

    List<ValidationWarning> containerValidationWarnings() {
        return getExtraValidationWarnings();
    }

    private List<SignatureValidationData> createSignaturesForReport(Container container) {
        List<String> dataFilenames = container.getDataFiles().stream().map(DataFile::getName).collect(Collectors.toList());
        return container.getSignatures().stream().map(sig -> createSignatureValidationData(sig, dataFilenames)).collect(Collectors.toList());
    }

    private SignatureValidationData createSignatureValidationData(Signature signature, List<String> dataFilenames) {
        SignatureValidationData signatureValidationData = new SignatureValidationData();

        signatureValidationData.setId(signature.getId());
        signatureValidationData.setSignatureFormat(getSignatureFormat(signature.getProfile()));
        signatureValidationData.setSignatureLevel(getSignatureLevel(signature));
        signatureValidationData.setSignedBy(removeQuotes(signature.getSigningCertificate().getSubjectName(CN)));
        signatureValidationData.setSubjectDistinguishedName(parseSubjectDistinguishedName(signature.getSigningCertificate()));
        signatureValidationData.setErrors(getErrors(signature));
        signatureValidationData.setSignatureScopes(getSignatureScopes(signature, dataFilenames));
        signatureValidationData.setClaimedSigningTime(ReportBuilderUtils.getDateFormatterWithGMTZone().format(signature.getClaimedSigningTime()));
        signatureValidationData.setWarnings(getWarnings(signature));
        signatureValidationData.setInfo(getInfo(signature));
        signatureValidationData.setIndication(getIndication(signature));
        signatureValidationData.setSubIndication(getSubIndication(signature));
        signatureValidationData.setCountryCode(getCountryCode(signature));

        return signatureValidationData;
    }

    private SubjectDistinguishedName parseSubjectDistinguishedName(X509Cert signingCertificate) {
        String serialNumber = signingCertificate.getSubjectName(X509Cert.SubjectName.SERIALNUMBER);
        String commonName = signingCertificate.getSubjectName(CN);
        return SubjectDistinguishedName.builder()
                .serialNumber(serialNumber != null ? removeQuotes(serialNumber) : null)
                .commonName(commonName != null ? removeQuotes(commonName) : null)
                .build();
    }

    String removeQuotes(String subjectName) {
        return subjectName.replaceAll("^\"|\"$", "");
    }

    private String getSignatureLevel(Signature signature) {
        if (signature instanceof AsicESignature) {
            SignatureQualification signatureLevel = getDssSimpleReport((AsicESignature) signature).getSignatureQualification(signature.getUniqueId());
            return signatureLevel != null ? signatureLevel.name() : "";
        }
        return null;
    }

    private eu.europa.esig.dss.simplereport.SimpleReport getDssSimpleReport(AsicESignature bDocSignature) {
        return bDocSignature.getDssValidationReport().getReports().getSimpleReport();
    }

    private SignatureValidationData.Indication getIndication(Signature signature) {
        ValidationResult signatureValidationResult = signatureValidationResults.get(signature.getUniqueId());
        if (signatureValidationResult.isValid() && validationResult.getErrors().isEmpty()) {
            return SignatureValidationData.Indication.TOTAL_PASSED;
        } else if (signature instanceof AsicESignature
                && REPORT_INDICATION_INDETERMINATE.equals(getDssSimpleReport((AsicESignature) signature).getIndication(signature.getUniqueId()).name())
                && validationResult.getErrors().isEmpty()) {
            return SignatureValidationData.Indication.INDETERMINATE;
        } else {
            return SignatureValidationData.Indication.TOTAL_FAILED;
        }
    }

    private String getSubIndication(Signature signature) {
        if (signature instanceof AsicESignature) {
            if (getIndication(signature) == SignatureValidationData.Indication.TOTAL_PASSED) {
                return "";
            }
            SubIndication subindication = getDssSimpleReport((AsicESignature) signature).getSubIndication(signature.getUniqueId());
            return subindication != null ? subindication.name() : "";
        }
        return "";
    }

    private Info getInfo(Signature signature) {
        Info info = new Info();
        Date trustedTime = signature.getTrustedSigningTime();
        if (trustedTime != null) {
            info.setBestSignatureTime(ReportBuilderUtils.getDateFormatterWithGMTZone().format(trustedTime));
        } else {
            info.setBestSignatureTime("");
        }
        return info;
    }

    private List<Warning> getWarnings(Signature signature) {
        ValidationResult signatureValidationResult = signatureValidationResults.get(signature.getUniqueId());
        List<Warning> warnings = Stream.of(signatureValidationResult.getWarnings(), this.validationResult.getWarnings())
                .flatMap(Collection::stream)
                .distinct()
                .map(TimemarkContainerValidationReportBuilder::mapDigidoc4JWarning)
                .collect(Collectors.toList());
        warnings.addAll(getExtraWarnings(signature));
        return warnings;
    }

    private List<Error> getErrors(Signature signature) {
        ValidationResult signatureValidationResult = signatureValidationResults.get(signature.getUniqueId());
        return Stream.of(signatureValidationResult.getErrors(), this.validationResult.getErrors())
                .flatMap(Collection::stream)
                .distinct()
                .map(TimemarkContainerValidationReportBuilder::mapDigidoc4JException)
                .collect(Collectors.toList());
    }

    private String getCountryCode(Signature signature) {
        return signature.getSigningCertificate().getSubjectName(X509Cert.SubjectName.C);
    }

    abstract List<Warning> getExtraWarnings(Signature signature);

    abstract List<ValidationWarning> getExtraValidationWarnings();

    abstract List<SignatureScope> getSignatureScopes(Signature signature, List<String> dataFilenames);

    abstract String getSignatureForm();

    abstract String getSignatureFormat(SignatureProfile profile);

}
