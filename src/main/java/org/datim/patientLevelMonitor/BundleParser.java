package org.datim.patientLevelMonitor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.io.FilenameUtils;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.CanonicalType;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.IParser;

public class BundleParser {

  private final static Logger log = Logger.getLogger("mainLog");
  public static final String BUNDLE_TYPE_QUESTIONNAIRE_RESPONSE = "QuestionnaireResponse";
  public static final String BUNDLE_TYPE_RESOURCE = "Resource";

  public Data parse(File inputFile, MetadataMappings mappings, String schematronValidatorLocation) throws DataProcessingException, DataFormatException{   
      Bundle bundle = null;
      IParser parser = null;     
      FhirContext ctxR4 = FhirContext.forR4();
      String contentType = "";
      String extension = FilenameUtils.getExtension(inputFile.getPath());
      if (extension.equals("json")) {
        contentType = "application/json+fhir";
      } else if (extension.equals("xml")) {
        contentType = "application/xml+fhir";
      } else {
        log.severe("Invalid format for bundle. Format cannot be " + extension);
        throw new DataFormatException("Invalid format for bundle. Format cannot be " + extension);
      }
      if (contentType.equals("application/xml+fhir")) {
        parser = ctxR4.newXmlParser();
      } else if (contentType.equals("application/json+fhir")) {
        parser = ctxR4.newJsonParser();
      } else {
        log.severe("Unsupported content type " + contentType + "for File " + inputFile.getName());
        throw new DataFormatException("Unsupported content type " + contentType + "for File " + inputFile.getName());
      }

      BufferedReader reader = null;
      try {
        log.info("inputfile: " + inputFile.getName());
        Bundle r = parser.parseResource(Bundle.class, new FileReader(inputFile));        
        if(r instanceof Bundle) {
          bundle = (Bundle)r;
          log.info("Read file into a bundle");
        } else {
          log.info("File does not contain a valid bundle. Resource class is " + r.getClass());
        }
                
        log.info("Before validation, bundle has entry size:" + bundle.getEntry().size());
        List<PatientData> patientDataList = getPatientDataEntries(bundle, mappings);
        log.info("****** The patient data list size before validation:  " + patientDataList.size());    
        patientDataList = Validator.validate(bundle, patientDataList, "bundle", schematronValidatorLocation);
        return new Data(patientDataList);
      } catch (DataFormatException dfe) {
        log.info("data format exception caught when parse bundle resource: " + dfe.getMessage());
        throw new DataProcessingException("Invalid format for bundle. " + dfe.getMessage());
      } catch (Exception e) {
        log.info("error: " + e.getMessage());
        throw new DataProcessingException("Error occurred when parsing bundle. " + e.getMessage());

      }
      finally {
        if (reader != null) {
          try {
            reader.close();
          }catch (IOException ie) {
            log.info("error: " + ie.getMessage());
            throw new DataProcessingException("Error occurred when trying to close the BufferedReader. " + ie.getMessage());
          }
        }        
      }
  }

  private List<PatientData> getPatientDataEntries(Bundle bundle, MetadataMappings mappings) throws DataProcessingException{
    
    List<PatientData> patientDataList = new ArrayList<PatientData>();
    Map<String, String> mapProfileToPeriodPath = mappings.getMapProfileToPeriodPath();
    Map<String, String> mapProfileToLocationPath = mappings.getMapProfileToLocationPath();
    
    String indicator = "";
    String periodPath = "";
    String locationPath = "";
    try {
      indicator = getIndicator(bundle);
      log.info("indicator: " + indicator);
      log.info("mapProfileToPeriodPath: " + mapProfileToPeriodPath.size() + " " + mapProfileToPeriodPath);
      log.info("mapProfileToLocationPath: " + mapProfileToLocationPath.size() + " " + mapProfileToLocationPath);
      periodPath = mapProfileToPeriodPath.get(indicator);
      locationPath = mapProfileToLocationPath.get(indicator);
      log.info("periodPath from mapProfileToPeriodPath: " + periodPath);
      List<String> patientIDs = this.getPatientIDs(bundle);
      for (String id : patientIDs) {
        PatientData p = new PatientData(id);
        p.setIndicator(indicator);
        p.setPeriodPath(periodPath);
        p.setLocationPath(locationPath);
        patientDataList.add(p);
      }

      if (patientIDs.size() == 0 ) {
        return new ArrayList<PatientData>(); 
      }
      List<String> orphanSubjectIDs = new ArrayList<String>(); // patient entry not found for the subject referenced      
      int addedCount = 0;
      for (BundleEntryComponent next : bundle.getEntry()) {
        String id = next.getResource().getIdElement().asStringValue();
        String subjectReferenced = "";
        if (next.getResource().getNamedProperty("subject") != null) {
          // get the patient id referenced from subject
          Base o = next.getResource().getNamedProperty("subject").getValues().get(0);
          if (o.getNamedProperty("reference") != null) {
            try {
              Base o1 = o.getNamedProperty("reference").getValues().get(0);
              subjectReferenced = o1.castToString(o1) + "";
            } catch (Exception e) {
              log.info("error for reference subject:" + e.getMessage());
            }
          } else {
            log.info("reference is not used for subjerct:" + id);
          }
        }

        if (patientIDs.contains(subjectReferenced) || patientIDs.contains(id)) {
          addedCount++;
          log.info("Added entry:" + addedCount + ". Id:" + id + ". subjectReferenced:  " + subjectReferenced);

          PatientData pd = null;
          if (patientIDs.contains(subjectReferenced)) {
            pd = PatientData.getPatientData(subjectReferenced, patientDataList);
          } else {
            pd = PatientData.getPatientData(id, patientDataList);
          }
          if (pd != null) {
            pd.addEntry(next);
            //log.info(" Added entry to id \"" + pd.getPatientID()  + "\". Total entry size for this patient: " + pd.getEntries().size());
          } else {
            log.info("id:" + id + "/subjectReferenced:  " + subjectReferenced + " not found in patient list. Skip this entry.");
          }
        } else {
          log.info("add entry to orphan subject list: " + next.getResource().getResourceType());
          orphanSubjectIDs.add(id);
        }
      }
      return patientDataList;
    } catch (Exception e) {
      throw new DataProcessingException(e.getMessage());
    }
  }

  private List<String> getPatientIDs(Bundle bundle){
    List<String> patientIDs = new ArrayList<String>();
    for (BundleEntryComponent next : bundle.getEntry()) {
      String id = next.getResource().getIdElement().asStringValue();
      String resourceType = next.getResource().getResourceType().name();
      if (resourceType.equalsIgnoreCase(BundleParser.BUNDLE_TYPE_QUESTIONNAIRE_RESPONSE)) {
        log.info("skip this resource. resourcType: QuestionnaireResponse. id: "  + id);
        continue;
      }
      if (next.getResource().getResourceType().name().equalsIgnoreCase("Patient")) {
        patientIDs.add(id);
      }
    }
    log.info("patient id size:" + patientIDs.size());
    return patientIDs;
  }

//future: may need to change to a list of indicators when allow multiple indicators
  protected String getIndicator(Bundle bundle) throws DataProcessingException{
      
    try {
      List<CanonicalType> profiles = bundle.getMeta().getProfile();
      log.info("profiles:" + java.util.Arrays.asList(profiles));
      String indicator = profiles.get(0).asStringValue();
      log.info("indicator: " + indicator);
      return indicator;
    } catch (IndexOutOfBoundsException ie) {
      throw new DataProcessingException("Bundle profile is not defined. Can't get indicator info.");
    } catch (Exception e) {
      throw new DataProcessingException("Exception occurred when getIndicator from the bundle meta profile " + e.getMessage());
    }
  }

}
