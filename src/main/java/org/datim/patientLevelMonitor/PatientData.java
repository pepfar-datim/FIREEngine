package org.datim.patientLevelMonitor;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;

public class PatientData implements Serializable{
  private static final long serialVersionUID = 1L;
  private final static Logger log = Logger.getLogger("mainLog");
  public final static String NOW = "NOW";
  
  private String patientID = "";
  private String indicator = "";
  private String periodPath = "";
  private String locationPath = "";
  
  private List<BundleEntryComponent> entries = new ArrayList<BundleEntryComponent>();
    
  public PatientData (String patientID) {
    super();
    this.patientID =  patientID;    
  }
  
  public List<BundleEntryComponent> getEntries() {
    return this.entries;
  }
  
  public void addEntry(BundleEntryComponent entry) {
    this.entries.add(entry);
  }
  
  public void setIndicator(String indicator) {
    this.indicator = indicator;
  }
  
  public String getIndicator() {
    return this.indicator;
  }
  
  public void setPeriodPath(String pePath) {
    this.periodPath = pePath;
  }
  public String getPeriodPath() {
    return this.periodPath;
  }
  public void setLocationPath(String locationPath) {
    this.locationPath = locationPath;
  }
  public String getLocationPath() {
    return this.locationPath;
  }

  public BundleEntryComponent getEntry(String resourceType){    
    for (BundleEntryComponent bc: this.entries) {         
      if (bc.getResource().getResourceType().name().equalsIgnoreCase(resourceType)){
       return bc; 
      }
    }
    return null;
  }
  
  public String getPatientID () {
    return this.patientID;
  }
 
 public static PatientData getPatientData(String patientid, List<PatientData> patientDataList) {   
   for (PatientData pd: patientDataList ) {
     if (patientid.equals(pd.getPatientID())) {       
       return pd;
     }
   }
   log.info("pid:" + patientid + " not found when getPatientData.");
   return null;
 }
 
  public String toString(){
    StringBuilder sb = new StringBuilder("Patient ID:" + this.patientID +  " indicator: " + this.indicator + "  entry size: " + this.entries.size());
    for (BundleEntryComponent entry: this.entries) {
      sb.append("\n " + entry.getResource().getResourceType().name() +"");
    }
    sb.append(" location: " + this.getLocation(this.locationPath) + ". Timestamp for period: " + this.getTimestamp(this.periodPath));
    return  sb.toString() ;
  }
  
  
  
  private String getPeriod(String periodPath) {     
    Date d = this.getTimestamp(periodPath);
    if (d != null) {
      LocalDate localDate = d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();       
      return localDate.toString();
    }else {
      log.info("Period is emtpy for patient:" + this.patientID);
      return "";
    }    
  }
  
  
  public String getPeriod() {    
    return this.getPeriod(this.periodPath) ; 
  }
  
  private Date getTimestamp(String periodPath) { // used for calculating period    
    // periodPath should contain at least one period "."
    if (periodPath == null || periodPath.indexOf(".") == -1) {
      log.info("WARNING: invalid periodPath: \'"+ periodPath + "\'  does not contain a period between source and path: "+ " for patient: " + this.patientID );
      return null;
    }
    String[] pePathArr = periodPath.split("\\.", 2);
    
    String peResourceType = pePathArr[0];
    String pePath = pePathArr[1];
        
    for (BundleEntryComponent entry: this.entries) {                
      if ( entry.getResource().getResourceType().name().equalsIgnoreCase(peResourceType)) {              
        // get value based on resourceName and pathToValue from bec    
        Base o = entry.getResource();         
        try {
          for (String s : pePath.split("\\.")) {              
            o = o.getNamedProperty(s).getValues().get(0);              
          }
        }catch(Exception e) {
          log.info("WARNNG: EXCEPTION caught when get period for patient:" + this.patientID + ". " + e.getMessage() + ".  likely due to the path in patient data does not match the one in OCL. OCL resource: " + peResourceType +  " period path:  "  + pePath);
          return null;
        }
        
        if (!o.isEmpty()) {          
          try{
            DateTimeType effectiveDateTime = o.castToDateTime(o);
            Date date = effectiveDateTime.getValue();            
            return date;
          }catch(FHIRException fhire) {
            log.info("WARNING: failed to cast to date time:" +  o + " " + fhire.getMessage());
            return null;
          }          
        }else {
          log.info("path not found when get value for period. patient: " + this.patientID );
          return null;
        }                      
      }      
    }        
    return null;    
  }
   
  
  public String getLocation() {    
    return this.getLocation(this.locationPath);
  }
  
  private String getLocation(String locationPath) {
    String locationValue = "";
  
    if (locationPath == null || locationPath.indexOf(".") == -1) {
      log.info("WARNING: invalid locationPath: \'"+ locationPath + "\'  does not contain a period between source and path: "+ " for patient: " + this.patientID );
      return null;
    }
    String[] locPathArr = locationPath.split("\\.", 2);    
    String locResourceType = locPathArr[0];
    String locPath = locPathArr[1];
    
    
    for (BundleEntryComponent entry: this.entries) {               
      if ( entry.getResource().getResourceType().name().equalsIgnoreCase(locResourceType)) {                       
        // get value based on resourceName and pathToValue from bec    
        Base o = entry.getResource();          
        try {
          for (String s : locPath.split("\\.")) {              
            o = o.getNamedProperty(s).getValues().get(0);              
          }
        }catch(Exception e) {
          log.warning("EXCEPTION caught when get location:" + e.getMessage() + ".  likely due to the path in patient data does not match the one in OCL. OCL resource: " + locResourceType +  " location path:  "  + locPath);
          return "";
        }           
        if (!o.isEmpty()) {
          locationValue =  o.castToString(o) + ""; 
          
        }else {
          log.info("path not found when get value for location. patient: " + this.patientID );
          return null;
        }
      }
    }
    return locationValue;
  }
    
  
  // NOT implemented
  public String getFundingMedhanism() {
    return "HllvX50cXC0";
  }
  
  
}
