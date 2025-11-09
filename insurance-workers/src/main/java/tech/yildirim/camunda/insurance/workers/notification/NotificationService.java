package tech.yildirim.camunda.insurance.workers.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for handling notification operations. Provides functionality to send notifications to
 * customers via email.
 */
@Service
@Slf4j
public class NotificationService {

  /**
   * Sends a notification message to a customer via email. This is a dummy implementation that logs
   * the notification details. In a real implementation, this would integrate with an email service
   * provider.
   *
   * @param email the customer's email address to send the notification to
   * @param message the notification message content
   * @return true if notification was sent successfully, false otherwise
   * @throws IllegalArgumentException if email or message is null or empty
   */
  public boolean sendNotificationToCustomer(String email, String message) {
    if (email == null || email.trim().isEmpty()) {
      log.error("Cannot send notification: email address is null or empty");
      throw new IllegalArgumentException("Email address cannot be null or empty");
    }

    if (message == null || message.trim().isEmpty()) {
      log.error("Cannot send notification: message is null or empty");
      throw new IllegalArgumentException("Message cannot be null or empty");
    }

    log.info("Sending notification to customer with email: {} and message: {}", email, message);

    // For now, this is just a dummy implementation that logs the notification

    // Simulate successful notification sending for demo purposes
    return true;
  }
}
