package org.datim.patientLevelMonitor;

import java.io.File;

import org.datim.utils.HTTPUtilException;

/**
 * @author Vladimer Shioshvili <vladimer.shioshvili@icf.com>
 *
 */
public interface HMISService {
  public String importData(File importFile) throws DataProcessingException, HTTPUtilException;
}
