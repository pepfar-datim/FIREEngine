package org.datim.patientLevelMonitor;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;

public class PatientDataQR extends PatientData implements Serializable{
  private static final long serialVersionUID = 1L;
  private final static Logger log = Logger.getLogger("mainLog");
  public final static String NOW = "NOW";
  
  private String patientID = "";
  private String indicator = "";
  private String periodPath = "";
  private String locationPath = "";
  
  private List<BundleEntryComponent> entries = new ArrayList<BundleEntryComponent>();
    
  public PatientDataQR (String patientID) {
    super(patientID);
    this.patientID =  patientID;    
  }
  
  
  public BundleEntryComponent getEntry(String resourceType){    
    for (BundleEntryComponent bc: this.entries) {         
      if (bc.getResource().getResourceType().name().equalsIgnoreCase(resourceType)){
       return bc; 
      }
    }
    return null;
  }
  
 
  public String toString(){
    StringBuilder sb = new StringBuilder("Patient ID:" + this.patientID +  " indicator: " + this.indicator + "  entry size: " + this.entries.size());
    for (BundleEntryComponent entry: this.entries) {
      sb.append("\n " + entry.getResource().getResourceType().name() +"");
    }
    sb.append(" location: " + this.getLocation(this.locationPath) + " timestamp: " + this.getTimestamp(this.periodPath));
    return  sb.toString() ;
  }
  

  
  public String getPeriod() {     
    return this.getPeriod(getPeriodPath() ) ; 
  }
  
  
  private String getPeriod(String periodPath) { 
    
    Date d = this.getTimestamp(periodPath);
    //log.info("d:"+ d);
    if (d != null) {
      LocalDate localDate = d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();       
      return localDate.toString();
    }else {
      log.info("Period is emtpy for patient:" + patientID);
      return "";
    }    
  }
  
  
  private Date getTimestamp(String periodPath) { // used for calculating period    
    // periodPath should contain at least one "/"    
    if (periodPath == null || periodPath.indexOf("/") == -1) {
      log.info("WARNING: invalid periodPath: \'"+ periodPath + "\'  does not contain a '/' in linkId: "+ " for patient: " + this.patientID );      
      return null;
    }
        
    if (super.getEntries().size() == 0) {
      log.info("No entry found for patient: " + this.patientID);
      return null;
    }
    Date data = null;
    data = BundleParserQR.getAnswerDateData(super.getEntries().get(0), periodPath);     
    return data;    
  }
   
  public String getLocation() {    
    return this.getLocation(getLocationPath());
  }
  
  private String getLocation(String locationPath) {
    String locationValue = "";    
    if (locationPath == null || locationPath.indexOf("/") == -1) {
      log.info("WARNING: invalid locationPath: \'"+ locationPath + "\'  does not contain a '/' in linkId: "+ " for patient: " + this.patientID );
      return null;
    }
    if (super.getEntries().size() == 0) {
      log.info("No entry found for patient: " + this.patientID);
      return null;
    }
    locationValue = BundleParserQR.getAnswerData(super.getEntries().get(0), locationPath);     
    return locationValue;
  }
    
  
}
