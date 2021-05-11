package org.datim.patientLevelMonitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;


public class PLMExpression {
  private final static Logger log = Logger.getLogger("mainLog");
  private String plmExpr = "";  
  private static final String BINARY_OPERATOR = "AND";
  private boolean isValid = false;
  
  public PLMExpression(String plmExpr) {
    this.plmExpr = plmExpr;          
  }
  
  public void validate() throws DataProcessingException {
    List<String> pieces = Arrays.asList(this.plmExpr.split(PLMExpression.BINARY_OPERATOR)); 
    boolean invalidFound = false;
    for (Object o : pieces) {            
      Expression expr = new Expression(o.toString());  
      expr.validateExpression();
      if (!expr.isValidExpression()) {        
        invalidFound = true;
      }
    } 
    isValid = !invalidFound;
  }
  
  public List<Expression> getExpressions() throws DataProcessingException {
    List<String> pieces = Arrays.asList(this.plmExpr.split(PLMExpression.BINARY_OPERATOR));
    List<Expression> validExpressions = new ArrayList<Expression>();
    
    for (Object o : pieces) {            
      Expression expr = new Expression(o.toString()); 
      
      if (expr.isValidExpression()) {
        validExpressions.add(expr);
      }else {
        log.info("Invalid expression found in PLM:" + expr + " " + o);
        isValid = false;
      }
    }
    return validExpressions;
  }
  
  public boolean isValid() {
    return this.isValid;
  }
  
}
