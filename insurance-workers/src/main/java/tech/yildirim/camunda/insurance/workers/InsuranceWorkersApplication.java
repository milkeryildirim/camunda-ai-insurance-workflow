package tech.yildirim.camunda.insurance.workers;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main Spring Boot application class for the Camunda Insurance Workers service.
 * This application provides external task workers for handling insurance-related
 * business processes in Camunda Platform 7.
 *
 * <p>The application automatically discovers and registers all {@link tech.yildirim.camunda.insurance.workers.common.CamundaWorker}
 * implementations as external task handlers.</p>
 */
@SpringBootApplication
public class InsuranceWorkersApplication {

  /**
   * Main method to start the Spring Boot application.
   *
   * @param args command line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(InsuranceWorkersApplication.class, args);
  }
}
