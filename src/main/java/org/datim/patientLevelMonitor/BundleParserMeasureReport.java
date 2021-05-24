package org.datim.patientLevelMonitor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.apache.commons.io.FilenameUtils;
import org.hl7.fhir.r4.model.MeasureReport;

import com.fasterxml.jackson.databind.JsonNode;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.IParser;

public class BundleParserMeasureReport extends BundleParser {

  private final static Logger log = Logger.getLogger("mainLog");   

 
  public Data<MeasureReport> parse(File inputFile, MetadataMappings mappings, String schematronValidatorLocation) throws DataProcessingException, DataFormatException{   
      MeasureReport report = null;
      IParser parser = null;     
      FhirContext ctxR4 = FhirContext.forR4();
      String contentType = "";
      String extension = FilenameUtils.getExtension(inputFile.getPath());
      if (extension.equals("json")) {
        contentType = "application/json+fhir";
      } else if (extension.equals("xml")) {
        contentType = "application/xml+fhir";
      } else {
        log.severe("Invalid format for measure report. Format cannot be " + extension);
        throw new DataFormatException("Invalid format for measure report. Format cannot be " + extension);
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
        MeasureReport r = parser.parseResource(MeasureReport.class, new FileReader(inputFile));           
        if(r instanceof MeasureReport) {
          report = (MeasureReport)r;         
          log.info("Read file into a measureReport");
        } else {
          log.info("File does not contain a valid measureReport. Resource class is " + r.getClass());
        }                
        log.info("Before validation, measure report id:" + report.getId() + ", measure:" + report.getMeasure() + ", period:" + report.getPeriod().getStart() + " - " + report.getPeriod().getEnd() + ", report type: " + report.getType().getDisplay());
        
        List<MeasureReport> reports = new ArrayList<MeasureReport>();
        reports.add(report);
        return new Data<MeasureReport>(reports);
       
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
  
  public Data<MeasureReport> parseContent(JsonNode jsonNode, MetadataMappings mappings, String schematronValidatorLocation, String contentType) throws DataProcessingException, DataFormatException{   
    MeasureReport report = null;
    IParser parser = null;     
    FhirContext ctxR4 = FhirContext.forR4();
    log.info("parseContent ...");
    if (contentType.equals("application/xml+fhir")) {
      parser = ctxR4.newXmlParser();
    } else if (contentType.equals("application/json+fhir")) {
      parser = ctxR4.newJsonParser();
    } else {
      log.severe("Unsupported content type " + contentType );
      throw new DataFormatException("Unsupported content type " + contentType );
    }
       
    BufferedReader reader = null;
    try {        
      MeasureReport r = parser.parseResource(MeasureReport.class, jsonNode.toString());           
      if(r instanceof MeasureReport) {
        report = (MeasureReport)r;         
        log.info("Read resource into a measureReport");
      } else {
        log.info("Resource does not contain a valid measureReport. Resource class is " + r.getClass());
      }                
      log.info("Before validation, measure report id:" + report.getId() + ", measure:" + report.getMeasure() + ", period:" + report.getPeriod().getStart() + " - " + report.getPeriod().getEnd() + ", report type: " + report.getType().getDisplay());
      
      List<MeasureReport> reports = new ArrayList<MeasureReport>();
      reports.add(report);
      return new Data<MeasureReport>(reports);
     
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

}
