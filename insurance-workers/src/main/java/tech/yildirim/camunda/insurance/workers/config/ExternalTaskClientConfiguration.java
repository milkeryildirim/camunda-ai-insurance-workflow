package tech.yildirim.camunda.insurance.workers.config;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.client.ExternalTaskClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.util.StringUtils;
import tech.yildirim.camunda.insurance.workers.common.CamundaWorker;

/**
 * Configuration class for Camunda External Task Client setup and worker registration.
 * Automatically discovers all {@link CamundaWorker} beans and subscribes them to their respective topics
 * when the Spring application context is fully initialized.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class ExternalTaskClientConfiguration {

  private final List<CamundaWorker> camundaWorkers;
  private ExternalTaskClient externalTaskClient;

  /**
   * Creates and configures the Camunda External Task Client bean.
   * Validates configuration parameters and creates a properly configured client instance.
   *
   * @param camundaBaseUrl the base URL of the Camunda engine
   * @param workerId unique identifier for this worker instance
   * @param lockDuration how long to lock external tasks in milliseconds
   * @return configured ExternalTaskClient instance
   * @throws IllegalArgumentException if configuration parameters are invalid
   */
  @Bean
  public ExternalTaskClient externalTaskClient(
      @Value("${camunda.bpm.client.base-url}") String camundaBaseUrl,
      @Value("${camunda.bpm.client.worker-id:insurance-worker}") String workerId,
      @Value("${camunda.bpm.client.lock-duration:30000}") long lockDuration) {

    validateConfiguration(camundaBaseUrl, workerId, lockDuration);

    log.info("Creating ExternalTaskClient - URL: {}, Worker ID: {}, Lock Duration: {}ms",
             camundaBaseUrl, workerId, lockDuration);

    this.externalTaskClient = ExternalTaskClient.create()
        .baseUrl(camundaBaseUrl.trim())
        .workerId(workerId.trim())
        .lockDuration(lockDuration)
        .build();

    return this.externalTaskClient;
  }

  /**
   * Registers all discovered CamundaWorker beans with the External Task Client
   * when the Spring application is fully initialized.
   * Each worker is subscribed to its designated topic for processing external tasks.
   */
  @EventListener(ApplicationReadyEvent.class)
  public void registerWorkers() {
    log.info("Application ready. Discovered {} CamundaWorker implementation(s)", camundaWorkers.size());

    if (camundaWorkers.isEmpty()) {
      log.warn("No CamundaWorker implementations found. External Task Client will start but won't process any tasks.");
      return;
    }

    int successfulRegistrations = 0;
    for (CamundaWorker worker : camundaWorkers) {
      if (subscribeWorker(worker)) {
        successfulRegistrations++;
      }
    }

    log.info("Successfully registered {}/{} workers", successfulRegistrations, camundaWorkers.size());
  }

  /**
   * Subscribes a single worker to its designated topic with error handling.
   *
   * @param worker the CamundaWorker implementation to subscribe
   * @return true if subscription was successful, false otherwise
   */
  private boolean subscribeWorker(CamundaWorker worker) {
    try {
      String topicName = worker.getTopicName();

      if (!StringUtils.hasText(topicName)) {
        log.error("Worker {} has null or empty topic name - skipping subscription",
                  worker.getClass().getSimpleName());
        return false;
      }

      externalTaskClient.subscribe(topicName.trim())
          .handler(worker)
          .open();

      log.info("Successfully subscribed {} to topic '{}'",
               worker.getClass().getSimpleName(), topicName);
      return true;

    } catch (Exception e) {
      log.error("Failed to subscribe worker {}: {}",
                worker.getClass().getSimpleName(), e.getMessage(), e);
      return false;
    }
  }

  /**
   * Validates the External Task Client configuration parameters.
   *
   * @param camundaBaseUrl the Camunda base URL to validate
   * @param workerId the worker ID to validate
   * @param lockDuration the lock duration to validate
   * @throws IllegalArgumentException if any parameter is invalid
   */
  private void validateConfiguration(String camundaBaseUrl, String workerId, long lockDuration) {
    if (!StringUtils.hasText(camundaBaseUrl)) {
      throw new IllegalArgumentException("Camunda base URL cannot be null or empty. Check 'camunda.bpm.client.base-url' property.");
    }

    if (!StringUtils.hasText(workerId)) {
      throw new IllegalArgumentException("Worker ID cannot be null or empty. Check 'camunda.bpm.client.worker-id' property.");
    }

    if (lockDuration <= 0) {
      throw new IllegalArgumentException("Lock duration must be positive. Check 'camunda.bpm.client.lock-duration' property.");
    }
  }
}
