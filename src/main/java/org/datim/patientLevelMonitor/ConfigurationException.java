/**
 * 
 */
package org.datim.patientLevelMonitor;

/**
 * @author Vladimer Shioshvili <vladimer.shioshvili@icf.com>
 *
 */
public class ConfigurationException extends Exception {

  /**
   * 
   */
  private static final long serialVersionUID = 1L;

  /**
   * 
   */
  public ConfigurationException() {
  }

  /**
   * @param message
   */
  public ConfigurationException(String message) {
    super(message);
  }

  /**
   * @param cause
   */
  public ConfigurationException(Throwable cause) {
    super(cause);
  }

  /**
   * @param message
   * @param cause
   */
  public ConfigurationException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * @param message
   * @param cause
   * @param enableSuppression
   * @param writableStackTrace
   */
  public ConfigurationException(String message, Throwable cause, boolean enableSuppression,
      boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }

}
