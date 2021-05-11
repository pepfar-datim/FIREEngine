package org.datim.patientLevelMonitor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import org.datim.utils.HTTPUtil;
import org.datim.utils.HTTPUtilException;
import org.hl7.fhir.r4.model.Bundle;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import ca.uhn.fhir.context.FhirContext;

public class Validator {
	private final static Logger log = Logger.getLogger("mainLog");

	/**
	 * @throws HTTPUtilException 
	 * @throws IOException 
	 * 
	 */
	public static List<PatientData> validate(Bundle bundle, List<PatientData> patientDataList, String type, String schematronValidatorLocation) throws IOException {
		List<String> invalidIDs = new ArrayList<String>();
		// Create a FHIR context
		FhirContext ctx = FhirContext.forR4();	
		
		String serialized = ctx.newXmlParser().encodeResourceToString(bundle);
				JsonNode json = HTTPUtil.sendJson(schematronValidatorLocation + type, serialized, "POST", null);
				if(json.has("ids")) {
					ObjectMapper mapper = new ObjectMapper();
					ObjectReader reader = mapper.readerFor(new TypeReference<List<String>>() {
					});
					invalidIDs = reader.readValue(json.get("ids"));
					log.info("Number of invalid records: " + invalidIDs.size());
					log.info("Validation result: " + reader.readValue(json.get("logs")));
					return removeInvalidData(patientDataList, invalidIDs);
				}
					log.severe("An error occured when validating bundle. " + json.get("error"));
					return patientDataList;
}
		
		
	public static List<PatientData> removeInvalidData(List<PatientData> patientDataList, List<String> invalidIDs) {
		Iterator<PatientData> iterator = patientDataList.iterator(); 
		while (iterator.hasNext()) { 
			if(invalidIDs.contains(iterator.next().getPatientID())) {
				iterator.remove();
				} 
			}

		return patientDataList;
	}
}
