package org.datim.patientLevelMonitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.datim.utils.HTTPUtil;
import org.datim.utils.HTTPUtilException;
import org.datim.utils.User;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


public class TerminologyServiceOCLImpl implements TerminologyServiceInterface {

  private static Logger log = Logger.getLogger("mainLog");

  protected static final String HAS_CATEGORY = "Has Category";
  protected static final String HAS_OPTION = "Has Option";
    
  private Map<String, List<String>> mapDataElementIDtoProfiles = new HashMap<String,List<String>>();
  private Map<String, List<String>> mapDataElementIDtoCategories = new HashMap<String, List<String>>() ;
  private Map<String, List<String>> mapCategoryIDtoOptions = new HashMap<String, List<String>>() ;
  private Map<String, CategoryOption> mapOptionIDtoCategoryOptionObj = new HashMap<String, CategoryOption>() ;
  private Map<String, String> mapProfileToPeriodPath = new HashMap<String,String>() ;
  private Map<String, String> mapProfileToLocationdPath = new HashMap<String,String>() ;
  
  private String countryCode;
  private String oclVersion;
  private String oclDomain;
  private String dhisDomain;
  private User oclUser;
  private User dhisUser;


  public TerminologyServiceOCLImpl(String domain, String dhisDomain, String oclVersion, User oclUser, User dhisUser) throws HTTPUtilException, RuntimeException {
    this.oclDomain = domain;
    this.dhisDomain = dhisDomain;
    this.oclVersion = oclVersion;
    this.oclUser = oclUser;
    this.dhisUser = dhisUser;    
    
    loadOCLMappings();
  }

 

  /**
   * get DATIM mappings from OCL
   * 
   * @throws HTTPUtilException
   * @throws DataProcessingException
   */
  private void loadOCLMappings() throws HTTPUtilException, RuntimeException{
    try {
      //String uid = new DHIS2Service(this.dhisDomain, this.getDhisUser()).getUid();     
      
      String query = oclDomain;      
      log.info("Load OCL mappings:  " + query);
        
      JsonNode json = HTTPUtil.getJsonFromZip(query, oclUser);          
      JsonNode jsonC = json.get("concepts");          
      JsonNode jsonM = json.get("mappings");
      
      Set<String>validCategoryOptionIDs = new HashSet<String>();
          
      log.log(Level.INFO, "Mapping size from OCL: " + jsonM.size());
    
      for (JsonNode c : jsonC) {                
        String id = c.get("id").asText();        
        String conceptClass = c.get("concept_class").asText();
        String profile = "";
        String code = "";
        String periodPath = "";
        String locationPath =  "";        
       
        if (conceptClass.equalsIgnoreCase("category option")) {
          HashMap<String, String> mapProfileToExpressions = new HashMap<String, String>(); // contains profile as key, as well as "expression" as key for the default          
          JsonNode  extras = c.get("extras");// contains "expressions_resource" and "expressions_questionnaireResponse"
          
           for (JsonNode expr : extras.get("expressions_" + Configuration.getInputBundleType())) {             
             ObjectMapper mapper = new ObjectMapper();
             Map<String, String> exprMap = mapper.convertValue(expr, new TypeReference<Map<String, String>>(){}); // contains 'default' as the default expression, expression 'profile': as the expression for a specific indicator          
             for (String key : exprMap.keySet()) {                 
               mapProfileToExpressions.put(key, exprMap.get(key));// key can be 'profile' or 'default'         
             }    
           }
         
          log.info("mapProflieToExpression:" + mapProfileToExpressions);
          if (c.get("display_name") != null) {            
            code = c.get("display_name").asText();
            // temporary until OCL fixes this, for TX_PVLS
            if (code.equalsIgnoreCase("Undocumented Test Indication")) {
              code = "Undocumented_Test_Indication";
            }
          }
                   
          if (!mapProfileToExpressions.isEmpty()) {
            CategoryOption co = new CategoryOption(id, code, mapProfileToExpressions);
            this.mapOptionIDtoCategoryOptionObj.put(id, co);
            validCategoryOptionIDs.add(id);
          }
          
        }else if (conceptClass.equalsIgnoreCase("data element")) {         
          int count = 0;
          List<String> applicableProfiles = new ArrayList<String>();
          //allow a data element mapped to multiple profiles 
          for (JsonNode profileObj : c.get("extras").get("profiles")) {                                     
            count++;
            ObjectMapper mapper = new ObjectMapper();
            Map<String, String> profileMap = mapper.convertValue(profileObj, new TypeReference<Map<String, String>>(){}); // contains profile, period-path, location-path                  
            profile =  profileMap.get("profile");                     
            if (profileMap.get("profile").toLowerCase().contains(Configuration.getInputBundleType().toLowerCase())) {
              profile = profileMap.get("profile");
              applicableProfiles.add(profile);
              
              periodPath = profileMap.get("period-path");
              this.mapProfileToPeriodPath.put(profile, periodPath);
              
              locationPath = profileMap.get("location-path");
              this.mapProfileToLocationdPath.put(profile, locationPath);
            }                                            
          } 
          if (applicableProfiles.size() > 0) {
            this.mapDataElementIDtoProfiles.put(id, applicableProfiles);
          }
        }
      }
      log.info("mapDataElementIDtoProfiles:"+mapDataElementIDtoProfiles);     
      log.info("mapProfiletoPeriod: " + this.mapProfileToPeriodPath);
      log.info("mapProfileToLocation:"+ this.mapProfileToLocationdPath);
            
      for (JsonNode m : jsonM) {        
        String mapType = m.get("map_type").asText();   
        if (mapType.equalsIgnoreCase(HAS_CATEGORY)) {          
          String dataElementID = m.get("from_concept_code").asText() ;
          String categoryID = m.get("to_concept_code").asText();
          
          List<String> categoryList = new ArrayList<String>();
          if (this.mapDataElementIDtoCategories.containsKey(dataElementID)) {
            categoryList = this.mapDataElementIDtoCategories.get(dataElementID);
          }
          categoryList.add(categoryID);
          this.mapDataElementIDtoCategories.put(dataElementID, categoryList);
                   
        }
        
        if (mapType.equalsIgnoreCase(HAS_OPTION)) {
          String categoryID = m.get("from_concept_code").asText() ;
          String optionID = m.get("to_concept_code").asText();   
          
         // should only load those optionID that is for inputBundleType ??
          if(validCategoryOptionIDs.contains(optionID)) {
            List<String> optionList = new ArrayList<String>();
            if (this.mapCategoryIDtoOptions.containsKey(categoryID)) {
              optionList = this.mapCategoryIDtoOptions.get(categoryID);
            }
            optionList.add(optionID);
            this.mapCategoryIDtoOptions.put(categoryID, optionList);
          }                         
        }        
      }
      log.info("*** mapDataElementIDtoCategories size: : " + this.mapDataElementIDtoCategories.size() + "\n" + this.mapDataElementIDtoCategories +
           "\n*** mapCategoryIDtoOptions size :"+ this.mapCategoryIDtoOptions.size()  + "\n"+ this.mapCategoryIDtoOptions +
           "\n *** mapOptionIDtoCategoryOptionObj size :"+ this.mapOptionIDtoCategoryOptionObj.size());
    } catch (HTTPUtilException e) {
      log.log(Level.SEVERE, "Encountered HTTPUtilExceptioin when loading OCL mappings. " + e.getMessage());
      throw new HTTPUtilException(e.getMessage());
    } 
    catch (Exception e) {
      log.log(Level.SEVERE, "Error loading OCL mappings. " + e.getMessage());
      throw new RuntimeException(e.getMessage());
    }
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
  
  public Map<String, CategoryOption> getMapOptionIDtoCategoryOptionObj (){
   return this.mapOptionIDtoCategoryOptionObj;
  }
  
  public String getCountryCode(){
    return countryCode;
  }

  public void setCountryCode(String countryCode){
    this.countryCode = countryCode;
  }

  public String getOclVersion(){
    return oclVersion;
  }

  public void setOclVersion(String oclVersion){
    this.oclVersion = oclVersion;
  }

  public String getOclDomain(){
    return oclDomain;
  }

  public void setOclDomain(String oclDomain){
    this.oclDomain = oclDomain;
  }

  public String getDhisDomain(){
    return dhisDomain;
  }

  public void setDhisDomain(String dhisDomain){
    this.dhisDomain = dhisDomain;
  }

  public User getUser(){
    return oclUser;
  }

  public void setUser(User user){
    this.oclUser = user;
  }

  public User getDhisUser(){
    return dhisUser;
  }

  public void setDhisUser(User dhisUser){
    this.dhisUser = dhisUser;
  }

}