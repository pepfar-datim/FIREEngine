package org.datim.patientLevelMonitor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.io.FilenameUtils;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleEntryRequestComponent;
import org.hl7.fhir.r4.model.CanonicalType;

import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseItemComponent;
import org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent ;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.IParser;

public class BundleParserQR extends BundleParser {

  private final static Logger log = Logger.getLogger("mainLog");    

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
        patientDataList = Validator.validate(bundle, patientDataList, "questionnaireResponse", schematronValidatorLocation);
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
    
    log.info("mapProfileToPeriodPath:" + mapProfileToPeriodPath.size() + " " + mapProfileToPeriodPath + "\n" 
    + " mapProfileToLocationPath:" + mapProfileToLocationPath.size() + " " + mapProfileToLocationPath + "\n" 
    + " config bundle type: " + Configuration.getInputBundleType());
    
    String indicator = "";
    String periodPath = "";
    String locationPath = "";
    try {
      indicator = getIndicator(bundle);          
      periodPath = mapProfileToPeriodPath.get(indicator);
      locationPath = mapProfileToLocationPath.get(indicator);      
      log.info("indicator: " + indicator + ". period path: " + periodPath + ". location path: " + locationPath);         
      log.info("bundle entry size (number of patients): " + bundle.getEntry().size());
     
      // add QuestionnaireResponseItemComponent to patient object
      for (BundleEntryComponent next : bundle.getEntry()) {
        // each entry is for one patient
        String id = next.getResource().getIdElement().asStringValue();        
        String resourceType = next.getResource().getResourceType().name();        
        if (!resourceType.equalsIgnoreCase("QuestionnaireResponse")) {
          log.info("resourcetype is NOT QR, skip. id: " + id +  " - " + resourceType);
          continue;
        }               
        String patientID = getPatientID(next);
        if (!patientID.equalsIgnoreCase("")) {          
          PatientData p = new PatientDataQR(patientID);
          p.setIndicator(indicator);
          p.setPeriodPath(periodPath);
          p.setLocationPath(locationPath);
          p.addEntry(next);
         
          patientDataList.add(p);
        }        
      }      
      return patientDataList;
    } catch (Exception e) {
      throw new DataProcessingException(e.getMessage());
    }
  }

private String getPatientID(BundleEntryComponent bundleEntryComponent){
  return getAnswerData(bundleEntryComponent, "/Patient/id");   
}

protected static String getAnswerData(BundleEntryComponent bundleEntryComponent, String linkId){
  String data = "";
  QuestionnaireResponse qr = (QuestionnaireResponse)bundleEntryComponent.getResource();        
  List<QuestionnaireResponseItemComponent> qrItemComponents = qr.getItem();
  
  for (QuestionnaireResponseItemComponent qrItemComponent: qrItemComponents) {    
      List<QuestionnaireResponseItemComponent> itemComponents = qrItemComponent.getItem();
      //log.info("QR level 2 items: " + itemComponents.size());      
      for (QuestionnaireResponseItemComponent item: itemComponents) {                    
        if (item.getLinkId().equalsIgnoreCase(linkId)) {
          data = getItemValue(item);                
          return data;         
      }                       
    }       
  }
  return data;  
}

protected static Date getAnswerDateData(BundleEntryComponent bundleEntryComponent, String linkId){
  Date data = null;
  QuestionnaireResponse qr = (QuestionnaireResponse)bundleEntryComponent.getResource();        
  List<QuestionnaireResponseItemComponent> qrItemComponents = qr.getItem();
  
  for (QuestionnaireResponseItemComponent qrItemComponent: qrItemComponents) {    
      List<QuestionnaireResponseItemComponent> itemComponents = qrItemComponent.getItem();          
      for (QuestionnaireResponseItemComponent item: itemComponents) {                    
        if (item.getLinkId().equalsIgnoreCase(linkId)) {
          data = getItemDateValue(item);                
          return data;         
      }                       
    }       
  }
  return data;  
 }

 

  private static String getItemValue(QuestionnaireResponseItemComponent item){
    List<QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent> anwserComponents = item.getAnswer();
    //log.info("answerComponents size :" + anwserComponents.size() + anwserComponents.get(0).getValue() + "  ------   has code:" + anwserComponents.get(0).hashCode() + " has value:"+ anwserComponents.get(0).hasValue() + "  hasValueString type :" +anwserComponents.get(0).hasValueStringType() + " has value coding :" + anwserComponents.get(0).hasValueCoding() );
    String value = "";
    if (anwserComponents.get(0).hasValueStringType()) {
      value = anwserComponents.get(0).getValueStringType().getValueAsString();
    }else if (anwserComponents.get(0).hasValueDateType()) {
      value = anwserComponents.get(0).getValueDateType().getValueAsString();
    }else if (anwserComponents.get(0).hasValueDateTimeType()) {
      value = anwserComponents.get(0).getValueDateTimeType().getValueAsString();
    }else if (anwserComponents.get(0).hasValueDecimalType()) {
        value = anwserComponents.get(0).getValueDecimalType().getValueAsString();
    }else if (anwserComponents.get(0).hasValueIntegerType()) {
      value = anwserComponents.get(0).getValueIntegerType().getValueAsString();
    }
    else if (anwserComponents.get(0).hasValueCoding()) {      
      value = anwserComponents.get(0).getValueCoding().getCode();       
    }
    return value;
  }

  protected static Date getItemDateValue(QuestionnaireResponseItemComponent item){
    List<QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent> anwserComponents = item.getAnswer();
    //log.info("answerComponents size :" + anwserComponents.size() + anwserComponents.get(0).getValue() + "  ------   has code:" + anwserComponents.get(0).hashCode() + " has value:"+ anwserComponents.get(0).hasValue() + "  hasValueString type :" +anwserComponents.get(0).hasValueStringType() + " has value coding :" + anwserComponents.get(0).hasValueCoding() );
    Date value = null;
    if (anwserComponents.get(0).hasValueDateType()) {
      value = anwserComponents.get(0).getValueDateType().getValue();
    }else if (anwserComponents.get(0).hasValueDateTimeType()) {
      value = anwserComponents.get(0).getValueDateTimeType().getValue();
    }
    return value;
  }

}
