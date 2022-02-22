package org.datim.patientLevelMonitor;


import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.logging.Logger;
import org.datim.patientLevelMonitor.DataProcessingException;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;


public class Expression {
  
  enum Operator {    
    LESS_OR_EQUAL("<="),
    LESS_THAN("<"),
    GREATER_OR_EQUAL (">="), 
    GREATER_THAN(">"),  
    EQUAL("="),
    IN ("in");
    
    private String operator;    
    Operator(String operator) {
      this.operator = operator;     
    }
  };
  
  private final static Logger log = Logger.getLogger("mainLog");
  private String expr = "";
  private String leftHandSide, operator, rightHandSide;
  public final static String NOW = "now";
  private boolean isValid = true;
  
  public Expression(String expr) throws DataProcessingException {
    this.expr = expr;
    validateExpression();
  }
  
  public void validateExpression() throws DataProcessingException {
       
    boolean containValidOP = false;
    StringBuilder errorSB = new StringBuilder();
    
    for (int i = 0; i < Operator.values().length; i++) {        
      // check operator
      if (this.expr.indexOf(Operator.values()[i].operator) > -1) {       
        // check LHS and RHS exist
        if (this.expr.split(Operator.values()[i].operator).length != 2) {
          errorSB.append("Invalid expression. The expression should have two parts, LHS and RHS separated by the operator. expr: " + this.expr +". ");         
          break;
        }
        
        operator = Operator.values()[i].operator;
        leftHandSide = this.expr.split(Operator.values()[i].operator)[0].trim() ;
        rightHandSide = this.expr.split(Operator.values()[i].operator)[1].trim();
          
        // for case 1 = 0, 1 = 1
        if ( operator.equals(Operator.EQUAL.operator)) { 
          if (leftHandSide.equals("1") && (rightHandSide.equals("0") ) || rightHandSide.equals("1")) {  
            this.isValid = true;
            return;
          }           
        }
        
        // LHS                   
        if (this.isAgeFunction()) {                 
          AgeFunction af = new AgeFunction(this.leftHandSide);
          this.isValid = af.isValid; 
        }   
        else {
          this.isValid = isValidatePathExpression(leftHandSide, errorSB);
        }              
        
        // RHS - check value format. For  <, >, <=, >= operators should be a float value
       if (operator.equals(Operator.GREATER_OR_EQUAL.operator)  
           || operator.equals(Operator.GREATER_THAN.operator)
           || operator.equals(Operator.LESS_OR_EQUAL.operator)
           ||operator.equals(Operator.LESS_THAN.operator)) {
         try {
           Float.parseFloat(this.rightHandSide);
         }catch(NumberFormatException nfe) {          
           errorSB.append("\tInvliad expression. RHS should be a number for operator: \"" + operator + "\" in expr: \"" + this.expr +"\".");
         }         
       }
        
       // EQ operator value should not contain [] or {}
       if (operator.equals(Operator.EQUAL.operator)) {         
         if (this.rightHandSide.startsWith("[") || this.rightHandSide.startsWith("{") 
             || this.rightHandSide.endsWith("]") || this.rightHandSide.endsWith("}") ) {
           this.isValid = false;
           errorSB.append("\tInvliad expression. RHS should be a string or numeric value for the equal operator. Expr: \"" + this.expr +"\".");
         }           
       }        
       // IN operator 
       if (operator.equals(Operator.IN.operator)) {          
         if (!this.rightHandSide.startsWith("[")  || !this.rightHandSide.endsWith("]") ) {         
           errorSB.append("\tRHS should start with '[' and end with ']' for the IN operator in expr: \"" + this.expr +"\".");
         } else if (this.rightHandSide.substring(1, this.rightHandSide.length()-1).trim().equals("")) {                      
           errorSB.append("\tInvalid expression. RHS is empty for the IN operator. " + this.expr);
         }                
       }       
      //log.info("LHS:" + leftHandSide + " RHS:" + rightHandSide);                    
      containValidOP = true;        
      break;
      }
    }    
    if (!containValidOP) {      
      errorSB.append("Expression doesn't contain valid operator. expr:  " + this.expr + ". ");      
    }    
    if (errorSB.length()> 0) {      
      log.warning("Expression is not valid. error:" + errorSB);
      this.isValid = false;
      throw new DataProcessingException("Expression is invalid: " + errorSB +". Expr: " + this.expr );
    }        
  }  
  
  public boolean isValidExpression() {
    return this.isValid;
  }
  
  private boolean isAgeFunction() {
    return leftHandSide.startsWith("ageInYears(") || leftHandSide.startsWith("agetInMonths(");
  }
  
  
  /**
   * 
   * @param fullPath: in the resource.path format, e.g. #{Patient.birthDate}
   * @return
   */
  public static boolean isValidatePathExpression(String fullPath, StringBuilder errorSB) {
    boolean errorFound = false;       
    if (!fullPath.startsWith("#{") || !fullPath.endsWith("}") ) {
      errorSB.append("Path expression needs to start with \"#{\" and end with \"}\".  Expr:" + fullPath +". ");
      errorFound = true;
    }else if (Configuration.getInputBundleType().equalsIgnoreCase(BundleParserQR.BUNDLE_TYPE_QUESTIONNAIRE_RESPONSE) && fullPath.indexOf("/") == -1) {
      errorSB.append("\nPath expression need to contain at least one \"/\".  Expr:" + fullPath +". ");
      errorFound = true;
    } 
    else if (Configuration.getInputBundleType().equalsIgnoreCase(BundleParserQR.BUNDLE_TYPE_RESOURCE) && fullPath.indexOf(".") == -1) {
      errorSB.append("\nPath expression need to contain at least one \".\".  Expr:" + fullPath +". " + (Configuration.getInputBundleType().equalsIgnoreCase(BundleParserQR.BUNDLE_TYPE_QUESTIONNAIRE_RESPONSE) )+ " ___ " + Configuration.getInputBundleType() + " index of: " + fullPath.indexOf("/"));
      errorFound = true;
    }        
    return !errorFound;
  }
  
  
  
  /**
   * removed #{ } from fullPath
   * @param fullPath: in format of resource.path, e.g: #{patient.gender}
   * @return Patient.gender
   */
  public static String getFullPathWithoutTags(String fullPath) {
    fullPath = fullPath.trim();
    return fullPath.substring(2, fullPath.length() -1).trim();
  }
  
  private boolean isContant() {
    return leftHandSide.equals("1")  || leftHandSide.equals("0");
  }
  
  public String getResourceName()  {
    if (this.isContant()) {
      return "";
    }
    if (this.isAgeFunction()) {
      //need to implement. may want to create a separate AgeFunction class containing: param1, param2 
    }
    if (this.isValid) {           
      String s = Expression.getFullPathWithoutTags(this.leftHandSide).split("\\.")[0];      
      return s;      
    }else {
      return "";
    }
  }
    
  public String getPathValue()  {     
    if (this.isContant()) {
      return "";
    }
    String pathValue = "";
    try {        
        String s = Expression.getFullPathWithoutTags(this.leftHandSide);       
        return s.substring(this.getResourceName().length() + 1, s.length()  );              
    }catch(Exception e) {
      log.warning("Exception occurred when getPathValue: " + e.getMessage());
    }
     return pathValue;
  }
  
  /**
   * check if the patient data from the path matches the RHS value in expression
   * @param pd
   * @return
   */
  public boolean evaluate(PatientData pd) {
     String value = "";
     boolean isValid = false;    
     if (this.isAgeFunction() ) {       
       int age = evaluateExpressionAgeFunction(pd);
       //log.info("age:"+ age);
       if (age >= 0) {
         value = age + "";           
       }else {         
         value = "";                
       }
       isValid = evaluateValue(value, new StringBuilder());
     } else {
       //log.info("this.isContant():" + this.isContant());
       if (this.isContant()) {
         value = this.leftHandSide;
         if (value.equalsIgnoreCase("0")) { // remove this if statement once OCL mapping changed from 0=0 to 1=0 
           return false;
         }
       }else {         
         value = this.getPatientDataValue(pd);          
       }      
       isValid = evaluateValue(value, new StringBuilder());         
     }    
    return isValid;
  }
  
  
  private int evaluateExpressionAgeFunction (PatientData pd) {
    
    int age = -1;
    try {
      AgeFunction af = new AgeFunction(this.leftHandSide);
      
      String param1 = af.getParam1();
      String param2 = af.getParam2();
      String value1 = "";
      String value2 = "";
      LocalDate localDate1;
      LocalDate localDate2;
       
      // get value for param1
      if (param1.equalsIgnoreCase(Expression.NOW) ) {
        localDate1 = LocalDate.now();
        value1 = localDate1.toString();
      }else {        
        if (Configuration.getInputBundleType().equalsIgnoreCase(BundleParserQR.BUNDLE_TYPE_QUESTIONNAIRE_RESPONSE)) {          
          value1 = this.getPatientDataValueQR(pd, getFullPathWithoutTags(param1));          
        }else {
          String resourceNameParam1 = af.getResourceName(param1);
          String pathsParam1 = af.getPaths(param1);
          value1 = this.getPatientDataValue(pd, resourceNameParam1, pathsParam1);
        }       
      }
      
      // get value for param2
      if (param2.trim().equalsIgnoreCase(Expression.NOW) ) {   
        localDate2 = LocalDate.now();
        value2 = localDate2.toString();
      }else {
        if (Configuration.getInputBundleType().equalsIgnoreCase(BundleParserQR.BUNDLE_TYPE_QUESTIONNAIRE_RESPONSE)) {         
          value2 = this.getPatientDataValueQR(pd, getFullPathWithoutTags(param2));          
        }else {
          String resourceNameParam2 = af.getResourceName(param2);
          String pathsParam2 = af.getPaths(param2);       
          value2 = getPatientDataValue(pd, resourceNameParam2, pathsParam2);
        }       
      }
     
      if (value1 == null || value1.equalsIgnoreCase("null") || value1.equals("")|| value2.equalsIgnoreCase("null") || value2 == null || value2.equalsIgnoreCase("")) {        
        log.info("value1 or value 2 is null");
        return age;
      }
      
      //value should be in ISO_LOCAL_DATE  yyyy-MM-DD format               
      localDate1 = LocalDate.parse(value1.trim().substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE);
      localDate2 = LocalDate.parse(value2.trim().substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE);      
      
      age = getAgeInYears(localDate1, localDate2);       
    }
     catch(DateTimeParseException dpe) {
       log.warning("errro when parse date. The date should be in yyyy-MM-DD format."+ dpe.getMessage());         
    }catch(DataProcessingException dpe) {
      log.warning("Error when create ageFunction object: " + dpe.getMessage() + " LHS: " + this.leftHandSide);
    }    
    return age;
  }
  
  private Period getBetweenPeriod(LocalDate fromDate, LocalDate toDate) throws DataProcessingException {
    if (fromDate == null || toDate == null) {
      throw new DataProcessingException("Error in getBetweenPeriod - fromDate and toDate are required.");
    }        
    Period diff = Period.between(fromDate, toDate);
    return diff;
  }
  
  public int getAgeInYears(LocalDate fromDate, LocalDate toDate) throws DataProcessingException {         
    return  this.getBetweenPeriod(fromDate, toDate).getYears() ;
  }
  
  public int getAgeInMonths(LocalDate fromDate, LocalDate toDate) throws DataProcessingException {
    return  this.getBetweenPeriod(fromDate, toDate).getMonths();
  }
  
  private String getPatientDataValueQR(PatientData pd, String linkId) {
    
   if (linkId.equalsIgnoreCase("") ) {
     return "";
   }   
   String patientDataValue = "";
   patientDataValue = BundleParserQR.getAnswerData(pd.getEntries().get(0), linkId);
   return patientDataValue;
  }
  
  
  private String getPatientDataValue(PatientData pd, String resourceName, String paths) {       
    if (resourceName.equalsIgnoreCase("") || paths.equalsIgnoreCase("") ) {
      return "";
    }
    BundleEntryComponent bec = pd.getEntry(resourceName);    
    if (bec == null)
    {
      log.warning("Warning: patient data does not contain resource: " + resourceName + ". Patient id: " + pd.getPatientID());
      return null;
    }    
    // get value based on resourceName and pathToValue from bec    
    Base o = bec.getResource();
    String patientDataValue = "";
   
    for (String s : paths.split("\\.")) {                                   
      o = o.getNamedProperty(s).getValues().get(0);                              
    }        
    if (!o.isEmpty()) {
      patientDataValue =  o.castToString(o) + "";  
    }
    return patientDataValue;
  }
  
  /**
   * for the non-ageFunction case
   * @param pd
   * @return
   */
  private String getPatientDataValue(PatientData pd) {
    
    String patientDataValue = "";    
    if (Configuration.getInputBundleType().equalsIgnoreCase(BundleParserQR.BUNDLE_TYPE_QUESTIONNAIRE_RESPONSE)) {      
      patientDataValue  = this.getPatientDataValueQR(pd, getFullPathWithoutTags(this.leftHandSide));
    }else {
      patientDataValue = this.getPatientDataValue(pd, this.getResourceName(), this.getPathValue());
    }
    //log.info("patientDataValue: "+ patientDataValue);
    return patientDataValue;   
  }
  
  public boolean evaluateValue(String value, StringBuilder errorSB ) {    
    value = value.trim();
    for (int i = 0; i < Operator.values().length; i++) {                  
      
      if (this.expr.indexOf(Operator.values()[i].operator) > -1) {       
    
        if (operator.equals(Operator.GREATER_OR_EQUAL.operator)  
            || operator.equals(Operator.GREATER_THAN.operator)
            || operator.equals(Operator.LESS_OR_EQUAL.operator)
            ||operator.equals(Operator.LESS_THAN.operator)) {
          try {
            
            float valueFl = Float.parseFloat(value);
            float rightHandSideFl = Float.parseFloat(rightHandSide);
            
            if (operator.equals(Operator.GREATER_OR_EQUAL.operator ) && valueFl >= rightHandSideFl) {
              return true;
            }
            
            if (operator.equals(Operator.GREATER_THAN.operator ) && valueFl > rightHandSideFl) {
              return true;
            }
            if (operator.equals(Operator.LESS_OR_EQUAL.operator ) && valueFl <= rightHandSideFl) {
              return true;
            }
            if (operator.equals(Operator.LESS_THAN.operator ) && valueFl < rightHandSideFl) {
              return true;
            }            
          }catch(NumberFormatException nfe) {
            errorSB.append("\tInvalid value. The value should be a number for operator: \"" + operator + "\". Value: \"" + value +"\".");
          }         
        }else if (operator.equalsIgnoreCase(Operator.EQUAL.operator)) {  
          //System.out.println("operator is equal. this.rightHandSide: " + this.rightHandSide + " this left:" + this.leftHandSide + " this.rightHandSide.equalsIgnoreCase(\"NULL\")" + this.rightHandSide.equalsIgnoreCase("NULL"));
          if (this.rightHandSide.equalsIgnoreCase("NULL") && (value.equals("") || this.leftHandSide.equals("") || this.leftHandSide.equalsIgnoreCase("NULL"))) {
            return true;
          }
          else if (value.equalsIgnoreCase(rightHandSide)  || ("\"" + value + "\"").equalsIgnoreCase(rightHandSide) || ("\'" + value + "\'").equalsIgnoreCase(rightHandSide)) {
            return true;
          }
        }else if (operator.equalsIgnoreCase(Operator.IN.operator)) {
          String rightHandSideSubString = this.rightHandSide.substring(1, this.rightHandSide.length()-1);
          
          String [] valuesRHS = rightHandSideSubString.split("\\,");
          String valueQuoted = "\"" + value +"\"";     
          String valueSingleQuoted = "\'" + value +"\'"; 
          for (String s: valuesRHS) {
            
            if (s.trim().equalsIgnoreCase(value) || s.trim().equalsIgnoreCase(valueQuoted) ||  s.trim().equalsIgnoreCase(valueSingleQuoted)) {
              return true;
            }
          }          
        }                
        break;
      }
    }
    
  return false;
  }

  
}
  
