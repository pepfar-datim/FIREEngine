package org.datim.patientLevelMonitor;

import java.util.logging.Logger;

public class AgeFunction {
  
  private final static Logger log = Logger.getLogger("mainLog");
  private String ageFunction = "";  // the LHS string of the expression from OCL, such as: ageInYear(#{patient.birthdate}, now)
  private String param1 = "";
  private String param2 = "";
  boolean isValid = true;
  
  public AgeFunction(String ageFunction)  throws DataProcessingException  {
    this.ageFunction = ageFunction;     
    validateAgeFunction();
  }
  
  /**
   * 
   * @param leftHandSideAge, e.g. ageInYears(#{patient.birthDate}), ageInMonths(#{patient.birthDate})
   * @param errorSB
   * @return
   * @throws DataProcessingException
   */
  public void validateAgeFunction() throws DataProcessingException  {   
    StringBuilder errorSB = new StringBuilder();
    if (!this.ageFunction.endsWith(")") ){
      errorSB.append("The age function needs to end with ')'");
      
    }
    
    if (this.ageFunction.split("\\,").length != 2) {
      errorSB.append("The age function should contain 2 parameters separated by comma");
    }else {      
      String subString = this.ageFunction.substring( "ageInYears(".length(), this.ageFunction.length() - 1) ;
      if (this.ageFunction.startsWith("ageInMonths")) {
        subString = this.ageFunction.substring( "ageInMonths(".length(), this.ageFunction.length() - 1) ;
      }
      String [] params = subString.split("\\,");
      param1 = params[0];
      param2 = params[1];
      for (String s: params) {        
        s = s.trim();        
        if (!s.equalsIgnoreCase(Expression.NOW)) {
          if (!Expression.isValidatePathExpression(s, errorSB)) {            
            errorSB.append("The age function is invalid " + errorSB);
          }   
        }
      }                    
    }    
    if (errorSB.length() > 0) {
      this.isValid = false;
      log.warning("ageFunction validation failed for " + this.ageFunction + " error: " + errorSB);
      throw new DataProcessingException(errorSB.toString());
    }
    
  }
  
  public String getParam1() {
    return this.param1;
  }
  
  public String getParam2() {
    return this.param2;
  }
  
  public String getResourceName(String param) {
    if (param.equalsIgnoreCase(Expression.NOW)) {
      return "";
    }else {
      StringBuilder errorSB = new StringBuilder();
     // boolean isValid = Expression.validatePathExpression(param, errorSB);
     // if (isValid) {
        String s = Expression.getFullPathWithoutTags(param).split("\\.")[0];  // already validated param in proper format before adding the option to optionIDsWithValidExpression. 
        return s;
      //}
    }                
  }
  public String getPaths(String param) {
    if (param.equalsIgnoreCase(Expression.NOW)) {
      return "";
    }else {
      String pathValue = "";
      try {        
          String s = Expression.getFullPathWithoutTags(param);
          pathValue = s.substring(getResourceName(param).length() + 1, s.length()  );                
      }catch(Exception e) {
        log.warning("Exception occurred when getPathValue for param: " + param + ". " + e.getMessage());
      }
      return pathValue;
    }                
  }  
  
}
