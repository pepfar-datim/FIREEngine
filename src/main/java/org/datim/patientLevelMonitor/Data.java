package org.datim.patientLevelMonitor;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
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


import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.MeasureReport.MeasureReportGroupComponent;
import org.hl7.fhir.r4.model.MeasureReport.StratifierGroupComponent;
import org.hl7.fhir.r4.model.MeasureReport.StratifierGroupComponentComponent;
import org.hl7.fhir.r4.model.Period;


public class Data<T> {
  
  private final static Logger log = Logger.getLogger("mainLog");
  
  private List<PatientData> patientDataList = new ArrayList<PatientData>();
  public static String delimiter1 = "###";
  public static String delimiter2 = "@@@";
  private Map<String, CategoryOption> mapOptionIDtoCategoryOptionObj = new HashMap<String, CategoryOption>();
  private Map<String, String> mapHmisConceptIDToFhirConceptID = new HashMap<String, String>(); 
  private Map<String, String> mapFhirConceptIDToName = new HashMap<String, String>();
  private Map<String, String> mapHmisConceptIDToFhirConceptName = new HashMap<String, String>();
  public static final String DEFAULT_EXPRESSION_KEY = "default"; // key for default expression defined in OCL for category option
  private List<MeasureReport> measureReportList = new ArrayList<MeasureReport>();
  
  public Data() {};
  public Data(List<T> data) {
   
   if (data != null) 
     log.info("data size: " +  data.size()); 
   else 
     log.info("data is null");
   
    for (T d: data) {     
      if (d instanceof PatientData) {        
        this.patientDataList.add((PatientData)d);
      }else if (d instanceof MeasureReport) {
          this.measureReportList.add((MeasureReport)d);
      }
    }       
    if (Configuration.getInputBundleType().equalsIgnoreCase(BundleParser.BUNDLE_TYPE_MEASURE_REPORT)) {
      log.info("this.measreReportlist:" + this.measureReportList.size());
    }else {
      log.info("this.patientDatalist:" + this.patientDataList.size());
    }
       
  }
  
  

  public File transform(MetadataMappings mappings, String adxPath, String processID, Map<String, Integer> calculatedDataStoreAll) throws DataProcessingException{    
    File file = new File(adxPath + File.separator + processID + ".xml");
    mapOptionIDtoCategoryOptionObj  = mappings.getMapOptionIDtoCategoryOptionObj();
    try {
      List<DHIS2DataValue> datimData = getDatimData(calculatedDataStoreAll); // convert data from calculatedDataStore to list of DatimData      
      log.info("datimData: " + datimData.size());
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
    //log.info("optionIDsWithValidExpression:" + optionIDsWithValidExpression);
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
              //log.info("patient profile: " + profilePatient);
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
                  log.info("option:"+optionID);
                  log.info("optionIDsWithValidExpression:"+optionIDsWithValidExpression);
                  if (optionIDsWithValidExpression.contains(optionID)) {                    
                    CategoryOption co = mapOptionIDtoCategoryOptionObj.get(optionID);    
                    log.info("co:" + co.getMapIndicatorProfileToExpression().keySet());
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
                    log.info("expressionOCL :"+ expressionOCL);
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
                uniqueDataKey = getUniqueDataKey(mapMatchedCategoryIDToOptionID, uniqueDataKey);
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

  public void transformMeasureReportData(MetadataMappings mappings, Map<String, Integer> calculatedDataStoreAll) throws DataProcessingException  {
    log.info("transformMeasureReportData...");   
    Map<String, List<String>> mapDataElementIDtoCategories = mappings.getMapDataElementIDtoCategories();   
    mapHmisConceptIDToFhirConceptID = mappings.getMapHmisConceptIDToFhirConceptID() ;
    mapFhirConceptIDToName = mappings.getMapFhirConceptIDToName() ;
    mapOptionIDtoCategoryOptionObj  = mappings.getMapOptionIDtoCategoryOptionObj();    
    mapHmisConceptIDToFhirConceptName = getMapHmisConceptIDToFhirConceptName();
    
    log.info("measureReport list: " + this.measureReportList.size());
    log.info("mapHmisConceptIDToFhirConceptID:"+mapHmisConceptIDToFhirConceptID.size() + " --- " + mapHmisConceptIDToFhirConceptID);
    log.info("mapFhirConceptIDToName:" + mapFhirConceptIDToName.size() + " --- " + mapFhirConceptIDToName );
    log.info("mapHmisConceptIDToFhirConceptName:"+mapHmisConceptIDToFhirConceptName.size() + " --- " + mapHmisConceptIDToFhirConceptName);
    if (this.measureReportList.size() > 0) { 
      
      for (String dataElementID: mapDataElementIDtoCategories.keySet()) {// loop through data elements
        log.info("Data element ID: " + dataElementID);
       
        // find matched dataElement to Fhir_measure
        if (mapHmisConceptIDToFhirConceptID.containsKey(dataElementID)) {
          log.info("contain data elemnt :" + dataElementID);
          List<String> categories = mapDataElementIDtoCategories.get(dataElementID); 
          log.info("categories size: " +categories.size());
          if (categories.size() == 0) {
            log.warning("categories size is 0 for " + dataElementID + " - skip processing this data element");
            continue;
          }
          String fhirConceptName_dataElement = "";         
          if (this.mapHmisConceptIDToFhirConceptName.containsKey(dataElementID)) {
            fhirConceptName_dataElement = this.mapHmisConceptIDToFhirConceptName.get(dataElementID).trim();
            log.info("FhirConceptID_dataElement:"   + fhirConceptName_dataElement); 
            
            for (MeasureReport report : this.measureReportList ) {// loop through reports
              log.info("report: " +report.getId() + " -  " + report.getMeasure());
              if (!fhirConceptName_dataElement.equals("") && report.getId().equalsIgnoreCase("MeasureReport/" + fhirConceptName_dataElement)) {// found a match report with the data element
                log.info("process this measure report" + report.getMeasure());
                processMeasureReport(dataElementID, categories, report, calculatedDataStoreAll);
                log.info("after processMeasurereport - calculatedDataStoreAll:" + calculatedDataStoreAll);
              }              
            }            
          }          
          log.info("\n\n");                    
        }        
      }
     }    
  }
 
  
  private Map<String, String> getMapHmisConceptIDToFhirConceptName(){
    // TODO Auto-generated method stub
    for (String hmisConceptID: this.mapHmisConceptIDToFhirConceptID.keySet()) {
      String fhirConceptID = this.mapHmisConceptIDToFhirConceptID.get(hmisConceptID);
      for (String s: this.mapFhirConceptIDToName.keySet()) {
        String fhirConceptName = this.mapFhirConceptIDToName.get(fhirConceptID);
        if (fhirConceptID != null && s.trim().equalsIgnoreCase(fhirConceptID.trim())) {
          this.mapHmisConceptIDToFhirConceptName.put(hmisConceptID, fhirConceptName);
        }
      }
    }
    return this.mapHmisConceptIDToFhirConceptName;
  }
  
  private void processMeasureReport(String dataElementID, List<String>categories, MeasureReport report, Map<String, Integer> calculatedDataStoreAll) throws DataProcessingException {
    
    Period period = report.getPeriod();    
     
    if (period == null || period.getStart() == null ) {      
      String msg = "Period is null when process measure report " + report.getId() + ".  This report is not processed.";
      log.warning(msg);
      if (!Configuration.isParcialProcessingAllowed()) {
        throw new DataProcessingException (msg);
      }
      return;
    }
    // get the date from period start.  TO DO: may need to check the period is quarterly before processing?
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    String startDate = sdf.format(period.getStart());    
    String adxPeriod = DHIS2DataValue.getAdxPeriod(startDate);
    String location = report.getReporter().getReference();// Need to change when OU location is finalized
    log.info("Processing measure report :"+ report.getId() + " - " + report.getMeasure() + " period:"+ period.getStart() + " -" + period.getEnd() + " location: " + location);
    
    for (MeasureReportGroupComponent group : report.getGroup()) {      
      //group.getPopulation();
      log.info(group.getCode().getCodingFirstRep().getCode() + " - score: " + group.getMeasureScore().getValue() );
      String groupCode = group.getCode().getCodingFirstRep().getCode();
      if (groupCode != null && groupCode.equalsIgnoreCase("numerator")) {
        log.info("group.getStratifier() size:"+ group.getStratifier().size());
        for (MeasureReport.MeasureReportGroupStratifierComponent  stratefier : group.getStratifier() ) {
         
          String[] stratefierCategories = null;
          String stratefierCode = stratefier.getCodeFirstRep().getCodingFirstRep().getCode(); //AGE_GROUP:SEX
          if (stratefierCode != null) {           
            stratefierCategories = stratefierCode.split(":");
          }else {
            String msg = "stratefier code is not defined, return. No data is processed for this measure report - " + report.getId();
            log.warning(msg);
            if (!Configuration.isParcialProcessingAllowed()) {
              throw new DataProcessingException (msg);
            }
            return;
          }
          log.info("stratefierCategories size:" + stratefierCategories.length );
          if (stratefierCategories.length < categories.size()) {
            log.warning("stratifier size is less than the Hmis category size, no data is processed for " + report.getId() + ". return! ");
            return;
          }         
          if (!isCategorySizeEqual(categories, stratefierCategories)) {
            log.warning("matched stratefier size not same as HMIS categories size, no data is processed, return. " + stratefierCode + " - " + report.getId());
            return;
          }
          
          TreeMap<String, String> mapMatchedCategoryIDToOptionID = new TreeMap<String, String>();
          int index = 0;
          for (StratifierGroupComponent stratum: stratefier.getStratum()) {           
            index++;  
            log.info(index + " - stratum :" + stratum.getValue().getCodingFirstRep().getCode() + " score:"+ stratum.getMeasureScore().getValue());          
            String fhirCode =   stratum.getValue().getCodingFirstRep().getCode(); //P0Y--P1Y:F:POSITIVE:Undocumented_Test_Indication
            String [] codeParts = fhirCode.split(":");
            if (codeParts.length != stratefierCategories.length) {
              log.info("stratum code split size (" + codeParts.length + ") not same as stratefier code split size (" + stratefierCategories.length + "), skip this stratum. " + fhirCode);
              continue;
            } 
            for (int i = 0; i< stratefierCategories.length; i++) {
              String stratefierCategory = stratefierCategories[i]; //AGE_GROUP                      
              String categoryId = getHmisId(stratefierCategory);
              if (!categories.contains(categoryId)) {
                log.info("category id: " + categoryId + "( from stratefierCategory :" + stratefierCategory  + " is not found in categories, skip)");               
                continue;
              }                          
             String code = codeParts[i];
             String optionId = this.getHmisId(code);  
             if (optionId != null) {
               mapMatchedCategoryIDToOptionID.put(categoryId, optionId); 
             }else {
               log.info("optionId is null for " + code);
             }                                    
            }            
            
            //add to calculatedDataStoreAll
            if (mapMatchedCategoryIDToOptionID.keySet().size() == categories.size()) {                         
              int value = stratum.getMeasureScore().getValue().intValue();
              String uniqueDataKey = dataElementID.trim() + delimiter1 + location.trim() + delimiter1 + adxPeriod + delimiter1 + new PatientData(sdf.format(new java.util.Date())).getFundingMedhanism();          
              uniqueDataKey = getUniqueDataKey(mapMatchedCategoryIDToOptionID, uniqueDataKey);
              int valueTemp = getExistingValue(calculatedDataStoreAll, uniqueDataKey);
              value = value + valueTemp;
              log.info(index + " - value: " +value);
              calculatedDataStoreAll.put(uniqueDataKey, value);         
              log.info("added to calculated datastore all, count:" + calculatedDataStoreAll.size() + " category size: " + categories.size() + " mapMatchedCategoryIDToOptionID: "+ mapMatchedCategoryIDToOptionID);                                       
            }else {
              log.warning("Not add to calculatedDataStoreAll as mapMatchedCategoryIDToOptionID size (" + mapMatchedCategoryIDToOptionID.keySet().size() + ") not eq categories size (" + categories.size() + ")");
            }
           }                    
        }        
      }     
    }    
  }
  private String getUniqueDataKey(TreeMap<String, String> mapMatchedCategoryIDToOptionID, String uniqueDataKey){
    for (String key: mapMatchedCategoryIDToOptionID.keySet()) {            
      String optionID = mapMatchedCategoryIDToOptionID.get(key);
      String catOptionKey = key.trim()  + delimiter2 + optionID.trim();
      uniqueDataKey = uniqueDataKey + delimiter1 + catOptionKey;
    }
    return uniqueDataKey;
  }
  private int getExistingValue(Map<String, Integer> calculatedDataStoreAll, String uniqueDataKey){
    int valueTemp = 0;
    if (calculatedDataStoreAll.containsKey(uniqueDataKey)) {
      valueTemp = calculatedDataStoreAll.get(uniqueDataKey);      
    }
    return valueTemp;
  }
  
  private boolean isCategorySizeEqual(List<String> categories, String[] stratefierCategories){
    // check stratifier contains all the categories
    int matchedCategories = 0;
    for (int i = 0; i< stratefierCategories.length; i++) {
      String stratefierCategory = stratefierCategories[i]; //AGE_GROUP
      log.info("stratefierCategory :"+ stratefierCategory);             
      String categoryId = getHmisId(stratefierCategory);
      if (categoryId  == null || !categories.contains(categoryId)) {
        log.info("category id: " + categoryId + "( from stratefierCategory :" + stratefierCategory  + " is not found in categories, skip)");
        continue;
      }
      matchedCategories++;
    }
    log.info("matchedCategories:" + matchedCategories + " - categories size:" + categories.size());
    if (matchedCategories == categories.size()) {
      return true;
    }
    return false;
    
  }
  private String getHmisId(String fhirConceptName){
    for (String hmisId: this.mapHmisConceptIDToFhirConceptName.keySet()) {
      String fhirConceptTemp = this.mapHmisConceptIDToFhirConceptName.get(hmisId);     
      if (fhirConceptTemp.equalsIgnoreCase(fhirConceptName)) {        
        return hmisId;
      }      
     }    
    return null;
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
        if (i >= 4) {
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