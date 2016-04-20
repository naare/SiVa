/**
 * DSS - Digital Signature Services
 * Copyright (C) 2015 European Commission, provided under the CEF programme
 *
 * This file is part of the "DSS - Digital Signature Services" project.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package ee.openeid.pdf.webservice.json;

import eu.europa.esig.dss.DSSDocument;
import eu.europa.esig.dss.DSSException;
import eu.europa.esig.dss.validation.CertificateVerifier;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import eu.europa.esig.dss.validation.report.Reports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.xml.soap.SOAPException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of the Interface for the Contract of the Validation Web Service.
 */
@Service
public class PDFValidationService implements ValidationService {
	private static final Logger logger = LoggerFactory.getLogger(PDFValidationService.class);

	private CertificateVerifier certificateVerifier;

    @Override
	public Map<String, String> validateDocument(PDFDocument pdfDocument) throws DSSException {

		String exceptionMessage;
		try {
			if (logger.isInfoEnabled()) {
				logger.info("WsValidateDocument: begin");
			}

			if (pdfDocument == null) {
				throw new SOAPException("No request document found");
			}

			final DSSDocument dssDocument = JSONUtils.createDssDocument(pdfDocument);
			final SignedDocumentValidator validator = SignedDocumentValidator.fromDocument(dssDocument);
			validator.setCertificateVerifier(certificateVerifier);

			final InputStream inputStream =  null;
			final Reports reports = validator.validateDocument(inputStream);

            if (logger.isInfoEnabled()) {
				logger.info(
						"Validation completed. Total signature count: {} and valid signature count: {}",
						reports.getSimpleReport().getElement("//SignaturesCount").getText(),
						reports.getSimpleReport().getElement("//ValidSignaturesCount").getText()
				);

				logger.info("WsValidateDocument: end");
			}

			Map<String, String> reportMap = new HashMap<>();
			reportMap.put("SIMPLE", reports.getSimpleReport().toString());
			reportMap.put("DETAILED", reports.getDetailedReport().toString());
			reportMap.put("DIAGNOSTICDATA", reports.getDiagnosticData().toString());

            return reportMap;
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			exceptionMessage = e.getMessage();
		}
		logger.info("WsValidateDocument: end with exception");
		throw new DSSException(exceptionMessage);
	}

	@Autowired
	public void setCertificateVerifier(CertificateVerifier certificateVerifier) {
		this.certificateVerifier = certificateVerifier;
	}


}