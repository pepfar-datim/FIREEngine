package org.datim.patientLevelMonitor;

import java.util.List;
import java.util.Map;

public interface TerminologyServiceInterface {

  public Map<String, List<String>> getMapDataElementIDtoProfiles();  
  public Map<String, List<String>> getMapDataElementIDtoCategories();
  public Map<String, List<String>> getMapCategoryIDtoOptions();
  public Map<String, CategoryOption> getMapOptionIDtoCategoryOptionObj ();
  public Map<String, String> getMapProfileToPeriodPath();
  public Map<String, String> getMapProfileToLocationPath();
  public Map<String, String> getMapFhirConceptIDToName();
  public Map<String, String> getMapHmisConceptIDToFhirConceptID();
}