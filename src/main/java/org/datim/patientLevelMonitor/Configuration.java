package org.datim.patientLevelMonitor;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import org.datim.utils.HTTPUtil;
import org.datim.utils.User;

public class Configuration {
  private static String dhisdomain;
  private static String username;
  private static String password;
  private static String oclDomain;
  private static String oclVersion;
  private static String archivePath;
  private static String logPath;
  private static String adxPath;
  private static String inputBundleType;
  private static boolean parcialProcessingAllowed;
  private static Properties RESOURCE_BUNDLE = null;
  private static boolean archiveFilesInQueue = true;
  private static boolean importIntoHMIS = true;
  private static String schematronValidatorLocation;
  private static boolean readFromFhirServer = false;
 
  private Configuration() {}

  public static void loadConfiguration(String path) throws IOException{
    RESOURCE_BUNDLE = new Properties();    
    RESOURCE_BUNDLE.load(new FileInputStream(path));
    validate();
    dhisdomain = RESOURCE_BUNDLE.getProperty("dhisdomain");
    if(dhisdomain.length() > 0 && !dhisdomain.endsWith("/"))
      dhisdomain = dhisdomain + "/";
    username = RESOURCE_BUNDLE.getProperty("username").trim();
    password = RESOURCE_BUNDLE.getProperty("password").trim();
    oclDomain = RESOURCE_BUNDLE.getProperty("oclDomain").trim(); 
    oclVersion = RESOURCE_BUNDLE.getProperty("oclVersion").trim(); 
    archivePath = RESOURCE_BUNDLE.getProperty("archivePath").trim();    
    logPath = RESOURCE_BUNDLE.getProperty("logPath").trim();
    adxPath = RESOURCE_BUNDLE.getProperty("adxPath").trim();
    parcialProcessingAllowed = RESOURCE_BUNDLE.getProperty("parcialProcessingAllowed").trim().equalsIgnoreCase("true"); 
    archiveFilesInQueue = RESOURCE_BUNDLE.getProperty("archiveFilesInQueue").trim().equalsIgnoreCase("true");    
    importIntoHMIS = RESOURCE_BUNDLE.getProperty("importIntoHMIS").trim().equalsIgnoreCase("true"); 
    inputBundleType = RESOURCE_BUNDLE.getProperty("inputBundleType").trim();
    schematronValidatorLocation = RESOURCE_BUNDLE.getProperty("schematronValidatorLocation").trim();
    readFromFhirServer = RESOURCE_BUNDLE.getProperty("readFromFhirServer").trim().equalsIgnoreCase("true");
  }

  private static void validate(){

    //1. check that configuration has all required values
    String[] expectedVariables = { "dhisdomain", "username", "password", "archivePath", "logPath", "adxPath", "parcialProcessingAllowed", "archiveFilesInQueue", "oclDomain", "oclVersion", "importIntoHMIS", "inputBundleType", "schematronValidatorLocation", "readFromFhirServer"};
    String missing = "";
    for (String key : expectedVariables) {
      if (!RESOURCE_BUNDLE.containsKey(key)) {
        missing += key + "; ";
      }
    }
    if (missing.length() > 0)
      throw new RuntimeException("Configuration file does not contain expected keys: " + missing);

    // 2. check that dhis2 is accessible (no need for credentials, just the fact that url is correct)
    String dhisPath = "";
    if (RESOURCE_BUNDLE.getProperty("importIntoHMIS").trim().equalsIgnoreCase("true")) {
      dhisPath = RESOURCE_BUNDLE.getProperty("dhisdomain") + "/dhis-web-commons/security/login.action";
      try {
        if (!HTTPUtil.isOk(dhisPath, null)) {
          throw new RuntimeException("Cannot find global DHIS2 at: " + dhisPath);
        }
      } catch (RuntimeException re) {
        throw re;
      }
      
    }
    //String dhisPath = RESOURCE_BUNDLE.getProperty("dhisdomain") + "/dhis-web-commons/security/login.action";

   /* try {
      if (!HTTPUtil.isOk(dhisPath, null)) {
        throw new RuntimeException("Cannot find global DHIS2 at: " + dhisPath);
      }
    } catch (RuntimeException re) {
      throw re;
    }*/
    
  }
  
  public static User getUser(){
    return new User(getString("username"), getString("password"));
  }

  public static String getString(String key){
    if (RESOURCE_BUNDLE == null) {
      throw new RuntimeException("Configuration was not initialized");
    }
    return RESOURCE_BUNDLE.getProperty(key);
  }

  public static String getDhisdomain(){
    return dhisdomain;
  }

  public static void setDhisdomain(String dhisdomain){
    Configuration.dhisdomain = dhisdomain;
  }

  public static String getUsername(){
    return username;
  }

  public static void setUsername(String username){
    Configuration.username = username;
  }

  public static String getPassword(){
    return password;
  }

  public static void setPassword(String password){
    Configuration.password = password;
  }

  public static String getOclDomain(){
    return oclDomain;
  }

  public static void setOclDomain(String oclDomain){
    Configuration.oclDomain = oclDomain;
  }

  public static String getOclVersion(){
    return oclVersion;
  }

  public static void setOclVersion(String oclVersion){
    Configuration.oclVersion = oclVersion;
  }
  
  public static User getOCLUser(){
    return new User(oclVersion);
  }
  
  public static String getArchivePath(){
    return archivePath;
  }

  public static void setArchivePath(String archivePath){
    Configuration.archivePath = archivePath;
  }

  public static String getLogPath(){
    return logPath;
  }

  public static void setLogPath(String logPath){
    Configuration.logPath = logPath;
  }
  
  public static String getAdxPath(){
    return adxPath;
  }

  public static void setAdxPath(String adxPath){
    Configuration.adxPath = adxPath;
  }

  public static boolean isParcialProcessingAllowed(){
    return parcialProcessingAllowed;
  }
  

  public static boolean isReadFromFhirServer(){
    return readFromFhirServer;
  }

  public static void setParcialProcessingAllowed(boolean parcialProcessingAllowed){
    Configuration.parcialProcessingAllowed = parcialProcessingAllowed;
  }
  public static void setArchiveFilesInQueue(boolean ArchiveFilesInQueue){
    Configuration.archiveFilesInQueue = ArchiveFilesInQueue;
  }
  public static boolean getArchiveFilesInQueue() {
    return archiveFilesInQueue;
  }
  public static void setImportIntoHMIS(boolean importIntoHMIS){
    Configuration.importIntoHMIS = importIntoHMIS;
  }
  public static boolean getImportIntoHMIS() {
    return importIntoHMIS;
  }
  
  public static String getInputBundleType(){
    return inputBundleType;
  }

  public static void setInputBundleType(String inputBundleType){
    Configuration.inputBundleType = inputBundleType;
  }
  public static String getSchematronValidatorLocation() {
		return schematronValidatorLocation;
  }
  public static void setSchematronValidatorLocation(String schematronValidatorLocation) {
		Configuration.schematronValidatorLocation = schematronValidatorLocation;
  }
  
}
