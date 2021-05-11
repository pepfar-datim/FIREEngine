package org.datim.patientLevelMonitor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.datim.patientLevelMonitor.MetadataMappings;
import org.datim.utils.DHIS2DataValue;

import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;


public class Data {
  
  private final static Logger log = Logger.getLogger("mainLog");
  
  private List<PatientData> patientDataList;
  public static String delimiter1 = "###";
  public static String delimiter2 = "@@@";
  private Map<String, CategoryOption> mapOptionIDtoCategoryOptionObj = new HashMap<String, CategoryOption>();
  public static final String DEFAULT_EXPRESSION_KEY = "default"; // key for default expression defined in OCL for category option
  
  public Data(List<PatientData> data) {
    this.patientDataList = data;
    if (data != null) {
      log.info("******** The patient data list size after validation:  " + this.patientDataList.size());    
    }
  }
  
  public File transform(MetadataMappings mappings, String adxPath, String processID, Map<String, Integer> calculatedDataStoreAll) throws DataProcessingException{    
    File file = new File(adxPath + File.separator + processID + ".xml");
    mapOptionIDtoCategoryOptionObj  = mappings.getMapOptionIDtoCategoryOptionObj();
    try {
      List<DHIS2DataValue> datimData = getDatimData(calculatedDataStoreAll); // convert data from calculatedDataStore to list of DatimData      
      sortDataByOU(datimData);      
      datimData = sortDataByPeriod(datimData);      
      DHIS2DataValue.writeToADX(file, datimData, mapOptionIDtoCategoryOptionObj);
    } catch (IOException e) {
      throw new DataProcessingException("Failed to generate ADX file", e);
    }
    return file;
  }

  
  /**
   * loop through data elements,patient data, categories to check for the matching options, 
   * only when matching found for all the categories, add data to calculatedDataStore     
   * @param mappings
   * @param calculatedDataStoreAll
   * @throws DataProcessingException
   */
  public void transformPatientData(MetadataMappings mappings, Map<String, Integer> calculatedDataStoreAll) throws DataProcessingException  {
   
    Map<String, List<String>> mapDataElementIDtoProfiles = mappings.getMapDataElementIDtoProfiles();
    Map<String, List<String>> mapDataElementIDtoCategories = mappings.getMapDataElementIDtoCategories();
    Map<String, List<String>> mapCategoryIDtoOptions = mappings.getMapCategoryIDtoOptions();
    mapOptionIDtoCategoryOptionObj  = mappings.getMapOptionIDtoCategoryOptionObj();
    
    List<String> ptNotAddedToDatastore = new ArrayList<String>();
        
    List<String> optionIDsWithValidExpression = mappings.getOptionsWithValidExpression();
    log.info("patientdata list: " + this.patientDataList.size());
    if (this.patientDataList.size() > 0) { 
      
      for (String dataElementID: mapDataElementIDtoProfiles.keySet()) {
        log.info("de id: " + dataElementID);
        List<String> profilesOCL = mapDataElementIDtoProfiles.get(dataElementID); 
        
        // loop through profilesOCL
        for (String profileOCL: profilesOCL) { 
          log.info("profileOCL:"+ profileOCL);
          int count = 0;
          for (PatientData pd: this.patientDataList ) {
            count++;
            
            try {
              if (pd instanceof PatientDataQR) {              
                pd = (PatientDataQR)pd;
              }
                         
              String profilePatient = pd.getIndicator().trim();     
              log.info("patient profile: " + profilePatient);
              if (!profileOCL.equalsIgnoreCase(profilePatient) ) {
                //log.info("Profile in patient data doesn't match profiles from OCL. Skip this patient data. pid:" + pd.getPatientID());
                continue;
              }
                                      
              log.info("\n******* pid: " + pd.getPatientID() + ". #" + count);             
              String pe = pd.getPeriod();         
              String location = pd.getLocation();   
              log.info("patient profile: " + pd.getIndicator() + ", pt period: " + pe + ", location:" + location );
              if (pe == null || pe.equals("") || location == null || location.equals("")) {  
                String msg = "Period and/or location is empty. Skip this patient data. id:" + pd.getPatientID() +". pe: "+ pe + " locaiton: "+ location;
                log.warning(msg);
                if (!Configuration.isParcialProcessingAllowed()) {
                  throw new DataProcessingException(msg);
                }
                continue;
              }
                                       
              String uniqueDataKey = ""; // unique key for data entry: dataElemntID###location###period###fundingMechanism###categoryID1@@@optionID1###categoryID2@@@optionID2###catID...n@@@optionID...n         
              List<String> categories = mapDataElementIDtoCategories.get(dataElementID);
              log.info("categories size: " +categories.size());
              int matchedCategories = 0; //
              TreeMap<String, String> mapMatchedCategoryIDToOptionID = new TreeMap<String, String>();
                   
              for (String catID: categories  ) {                           
                List<String> options = mapCategoryIDtoOptions.get(catID);
                log.info("category ID: "+ catID + " options: " + options.size());
                // for each option get expressions      
                for (String optionID: options) {
                 
                  if (optionIDsWithValidExpression.contains(optionID)) {                    
                    CategoryOption co = mapOptionIDtoCategoryOptionObj.get(optionID);                  
                    Map<String, String> mapProfileToExpression = co.getMapIndicatorProfileToExpression();                    
                    String defaultExpression = "";
                    String expressionOCL = "";
                   
                    for (String key: mapProfileToExpression.keySet()) {                    
                      if (key.equalsIgnoreCase(Data.DEFAULT_EXPRESSION_KEY)) {
                        defaultExpression = mapProfileToExpression.get(key);
                      }
                      if (key.equalsIgnoreCase(profileOCL)) {
                        expressionOCL = mapProfileToExpression.get(key);
                      }                  
                    }
                    if (expressionOCL.equals("")){ // no indicator specific expression found, use default expression
                      expressionOCL = defaultExpression;
                    }                                                    
                    PLMExpression plmExpr = new PLMExpression(expressionOCL);              
                    List<Expression> expressions = plmExpr.getExpressions();
                    // need to match all the expressions for the option. 
                    int matchedExpressionsInOption = 0;
                    for (Expression expr: expressions) {                       
                      boolean isValid = expr.evaluate(pd);                       
                      if (isValid) {                      
                        matchedExpressionsInOption++;                        
                      }                
                    }
                    
                    if (matchedExpressionsInOption == expressions.size()) {  
                      log.info("yes, match all the expressions for optionID: " + optionID);
                      matchedCategories++;
                      mapMatchedCategoryIDToOptionID.put(catID, optionID);
                      break;
                    }
                  }else {
                    log.info("This option: " + optionID + " is not in the optionIDsWithValidExpression.");
                  }            
                }          
              }  
              //add to calculatedDataStore
              if (matchedCategories == categories.size() && categories.size() > 0) {          
                uniqueDataKey = dataElementID.trim() + delimiter1 + location.trim() + delimiter1 + DHIS2DataValue.getAdxPeriod(pe) + delimiter1 + pd.getFundingMedhanism();   
                for (String key: mapMatchedCategoryIDToOptionID.keySet()) {            
                  String optionID = mapMatchedCategoryIDToOptionID.get(key);
                  String catOptionKey = key.trim()  + delimiter2 + optionID.trim();
                  uniqueDataKey = uniqueDataKey + delimiter1 + catOptionKey;
                }
                int value = 0;
                if (calculatedDataStoreAll.containsKey(uniqueDataKey)) {
                  value = calculatedDataStoreAll.get(uniqueDataKey);
                }
                value++;
                calculatedDataStoreAll.put(uniqueDataKey, value);         
                log.info("added to calculated datastore all, count:" + calculatedDataStoreAll.size() + ". matchedCategories: "+ matchedCategories + " category size: " + categories.size() + " mapMatchedCategoryIDToOptionID: "+ mapMatchedCategoryIDToOptionID + " uniqueDataKey:"+uniqueDataKey);
              }else {
                ptNotAddedToDatastore.add(pd.getPatientID());
                log.info("NOT add this patient "+ pd.getPatientID() + " to calculated datastore. ptNotAddedToDatastore size: "+ptNotAddedToDatastore.size());
              }
            
            }catch(Exception e) {
              String msg = "Exception when transform patient data for pid: " + pd.getPatientID() + " " + e.getMessage();
              log.warning(msg);
              if (!Configuration.isParcialProcessingAllowed()) {
                throw new DataProcessingException (msg);
              }
            }      
          
          } // end loop of patient list
        
        } // end of profilesOCL
        
        
      }  // end loop of data elements   
    }
    log.info("calculatedDataStoreAll size: "+ calculatedDataStoreAll.size()); 
    log.info("total ptNotAddedToDatastore: " + ptNotAddedToDatastore.size() + ". patientID list:" + ptNotAddedToDatastore);
  }

 
  
  protected List<DHIS2DataValue> getDatimData(Map<String, Integer>  calculatedDataStoreAll) throws DataProcessingException{
    log.info("getDatimData - datstore size:" + calculatedDataStoreAll.size());
    List<DHIS2DataValue> dataList = new ArrayList<DHIS2DataValue>();

    for (String key : calculatedDataStoreAll.keySet()) {
      
      String[] keys = key.split(delimiter1);    
      String de = keys[0];
      String location = keys[1];
      String pe = keys[2];
      String fundingMechanism = keys[3];
      List<String> catOptions = new ArrayList<String>();
      int value = calculatedDataStoreAll.get(key);
      
      int i = 0;        
      for (String s: keys) {        
        if (i >=4) {
          catOptions.add(s);
        }      
        i++;
      }
      
      try {
        DHIS2DataValue dData = new DHIS2DataValue(de, pe, location, fundingMechanism, catOptions, value);
        dataList.add(dData);
      } catch (Exception e) {
        String msg = "Failed when getDatimData: "+ e.getMessage();
        log.info(msg);
        if (!Configuration.isParcialProcessingAllowed()) {
          throw new DataProcessingException(msg);
        }
      }
    }
    
    log.info(dataList.size() + " rows put into DatimData list.");
    return dataList;
  }

  /**
   * Sorts data value list by org unit in ascending order
   * 
   * @param datimData
   */
  protected void sortDataByOU(List<DHIS2DataValue> datimData){
    Collections.sort(datimData, new Comparator<DHIS2DataValue>() {
      public int compare(DHIS2DataValue d1, DHIS2DataValue d2){
        return d1.getOrgUnit().compareTo(d2.getOrgUnit());
      }
    });
  }
  /**
   * data already sorted by OU, for data in the same OU group, sort by period
   * @param datimData
   * @throws DataProcessingException
   */
  private List <DHIS2DataValue> sortDataByPeriod(List<DHIS2DataValue> data) throws DataProcessingException{
    
    String ou;
    String ou_prev = null;
    List <DHIS2DataValue> sortedDatimData = new ArrayList<DHIS2DataValue>();   
    List <DHIS2DataValue> datimDataPerOU = new ArrayList<DHIS2DataValue>();
    
    for (DHIS2DataValue dd: data) {
      ou = dd.getOrgUnit();                
      if(!(ou.equals(ou_prev) )){       
        this.sortDataByPeriodPerOU(datimDataPerOU);
        sortedDatimData.addAll(datimDataPerOU);
        datimDataPerOU = new ArrayList<DHIS2DataValue>();
        ou_prev = ou;
      }
      datimDataPerOU.add(dd);
    }           
    sortDataByPeriodPerOU(datimDataPerOU);
    sortedDatimData.addAll(datimDataPerOU);
    
    return sortedDatimData;
  }
  
  private void sortDataByPeriodPerOU(List<DHIS2DataValue> datimData){    
    Collections.sort(datimData, new Comparator<DHIS2DataValue>() {
      public int compare(DHIS2DataValue d1, DHIS2DataValue d2){        
        return d1.getPeriod().compareTo(d2.getPeriod());
      }
    });
  }

public List<PatientData> getPatientDataList() {
	return patientDataList;
}
  
}