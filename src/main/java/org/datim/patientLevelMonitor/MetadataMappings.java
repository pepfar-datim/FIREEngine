package org.datim.patientLevelMonitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.datim.utils.HTTPUtilException;


public class MetadataMappings {

  private Map<String, List<String>> mapDataElementIDtoProfiles;
  private Map<String, List<String>> mapDataElementIDtoCategories;
  private Map<String, List<String>> mapCategoryIDtoOptions;
  private Map<String, CategoryOption> mapOptionIDtoCategoryOptionObj;
  private List<String> optionsWithValidExpression;
  private Map<String, String> mapProfileToPeriodPath;
  private Map<String, String> mapProfileToLocationdPath;
  private Map<String, String> mapHmisConceptIDToFhirConceptID;
  private Map<String, String> mapFhirConceptIDToName;
  
  
  private final static Logger log = Logger.getLogger("mainLog");
  
  private Map<String, String> mapMohFactilityIdToOUId;

  public MetadataMappings() {}

  public void loadIndicatorMappings(TerminologyServiceInterface ts) throws HTTPUtilException{    
    
    this.mapDataElementIDtoProfiles = ts.getMapDataElementIDtoProfiles();
    this.mapDataElementIDtoCategories = ts.getMapDataElementIDtoCategories();
    this.mapCategoryIDtoOptions = ts.getMapCategoryIDtoOptions();
    this.mapOptionIDtoCategoryOptionObj = ts.getMapOptionIDtoCategoryOptionObj(); 
    this.mapProfileToLocationdPath = ts.getMapProfileToLocationPath();
    this.mapProfileToPeriodPath = ts.getMapProfileToPeriodPath();
    this.mapFhirConceptIDToName = ts.getMapFhirConceptIDToName();
    this.mapHmisConceptIDToFhirConceptID = ts.getMapHmisConceptIDToFhirConceptID();
  }

  /**
   * @return the mapMohFactilityIdToOUId
   */
  public Map<String, String> getMapMohFactilityIdToOUId() {
    return mapMohFactilityIdToOUId;
  }

  public Map<String, List<String>> getMapDataElementIDtoProfiles(){
    return this.mapDataElementIDtoProfiles;
  }
  public Map<String, String> getMapProfileToPeriodPath(){
    return this.mapProfileToPeriodPath;
  }
  public Map<String, String> getMapProfileToLocationPath(){
    return this.mapProfileToLocationdPath;
  }
  public Map<String, List<String>> getMapDataElementIDtoCategories(){
    return this.mapDataElementIDtoCategories;
  }
  public Map<String, List<String>> getMapCategoryIDtoOptions(){
    return this.mapCategoryIDtoOptions;
  }
  public Map<String, CategoryOption> getMapOptionIDtoCategoryOptionObj(){
    return this.mapOptionIDtoCategoryOptionObj;
  }
  public Map<String, String> getMapFhirConceptIDToName(){
    return this.mapFhirConceptIDToName;
  }
  public Map<String, String> getMapHmisConceptIDToFhirConceptID(){
    return this.mapHmisConceptIDToFhirConceptID;
  }
    
  public List<String> getOptionsWithValidExpression(){
    return this.optionsWithValidExpression;
  }
  
  public void validateOptionsWithValidExpression() throws DataProcessingException{
    List<String> optionIDsWithValidExpression = new ArrayList<String>();
    StringBuffer errorSB = new StringBuffer();
    log.info("validateOptionsWithValidExpresison starts...");
    log.info("this.mapOptionIDtoCategoryOptionObj.keySet():"+this.mapOptionIDtoCategoryOptionObj.keySet());
    for (String optionID: this.mapOptionIDtoCategoryOptionObj.keySet()) {
      CategoryOption co = mapOptionIDtoCategoryOptionObj.get(optionID);
      // check every expression for co. If invalid expression found, not include this option, as we don't know which expression to use otherwise. Can't just use the default expression which may not be the expression intended and cause data error if used. 
      Map<String, String> mapProfilesToExpression = co.getMapIndicatorProfileToExpression();
      boolean foundInvalid = false;
      String expr = "";
      for (String key: mapProfilesToExpression.keySet()) {       
        expr = mapProfilesToExpression.get(key);           
        PLMExpression plmExpr = new PLMExpression(expr);
        plmExpr.validate();  
        log.info("optionID: "+optionID + ". key:" + key + ".  expr:" + expr + ". isValid:" + plmExpr.isValid());                
        if (expr.equals("") && !plmExpr.isValid()) {
          foundInvalid = true;   
          break;
        }
      }
           
      if (!foundInvalid) {
        optionIDsWithValidExpression.add(optionID);
      }else {
        String msg = "WARNING: check expression in OCL. invalid expression found in OCL.  option ID: " + optionID + ". Exclude this category option in data transformation. expr: " + expr; 
        errorSB.append(msg) ;       
      }
    }
    if (errorSB.length() > 0) {
      log.warning( errorSB.toString());
      throw new DataProcessingException(errorSB.toString());
    }
    log.info("optionIDsWithValidExpression: " + optionIDsWithValidExpression.size()  + " " + optionIDsWithValidExpression);
    this.optionsWithValidExpression = optionIDsWithValidExpression;
    
  }
  
}