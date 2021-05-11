package org.datim.patientLevelMonitor;

import java.util.HashMap;

public class CategoryOption {
  private String id;
  private String code;
  private String expression;
  private HashMap<String, String> mapIndicatorProfileToExpression = new HashMap<String, String>();
  
  public CategoryOption(String id, String code, String expression) {
    this.id = id;
    this.code = code;
    this.expression = expression;
  }
  
  public CategoryOption(String id, String code, HashMap<String, String> mapIndicatorProfileToExpression) {
    this.id = id;
    this.code = code;
    this.mapIndicatorProfileToExpression = mapIndicatorProfileToExpression;
  }
  public String getID() {
    return this.id;
  }
  
  public String getCode() {
    return this.code;    
  }
  public String getExpression() {
    return this.expression;
  }
  
  public HashMap<String, String>getMapIndicatorProfileToExpression(){
    return this.mapIndicatorProfileToExpression;
  }
  
  public String getCodeInXMLformat() {
    if (this.code == null || this.code.equals("")) return "";

    StringBuffer newString = new StringBuffer();

    for (int i = 0; i < this.code.length(); i++){
      char ch = this.code.charAt(i);
      switch (ch){
        case '\'': newString.append("&apos;");break;
        case '"': newString.append("&quot;");break;
        case '&': newString.append("&amp;");break;
        case '<': newString.append("&lt;");break;
        case '>': newString.append("&gt;");break;

        default:
          newString.append(ch);
        break;
      }
    }
    return (newString.toString()).trim();   
  }
  
}
