package tech.yildirim.camunda.insurance.workers.bpmn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.complete;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.task;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.withVariables;

import java.util.List;
import java.util.Map;
import org.camunda.bpm.engine.ExternalTaskService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.externaltask.ExternalTask;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.junit5.ProcessEngineExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import tech.yildirim.camunda.insurance.workers.common.ProcInstVars;

/**
 * Integration test for claim decision rejection workflow in the insurance claim process.
 * Tests the complete path where policy is valid, claim is created, adjuster is assigned,
 * adjuster report is received, payment decision results in rejection, and customer notification is sent.
 *
 * This test validates the entire process flow from start to Event_1l4zhuh (Claim Closed - Rejected).
 *
 * Note: Message correlation for adjuster report is simulated using execution signal API
 * as a workaround for test environment limitations.
 */
class DecisionRejectionTest {

  /** Test Variables */
  private static final String POLICY_NUMBER = "AUTO-POL-VALID-123";
  private static final String CLAIM_TYPE = "AUTO";
  private static final Long CLAIM_ID = 2001L;
  private static final String CLAIM_FILE_NUMBER = "CLAIM-2001";
  private static final String CUSTOMER_FIRSTNAME = "Anna";
  private static final String CUSTOMER_LASTNAME = "Schmidt";
  private static final String CUSTOMER_EMAIL = "anna.schmidt@example.com";

  /** Adjuster Information */
  private static final Long ADJUSTER_ID = 101L;
  private static final String ADJUSTER_REPORT_URL = "https://example.com/reports/adjuster-report-2001.pdf";

  /** Decision Information */
  private static final String DECISION_TYPE_REJECT = "reject";
  private static final String DECISION_NOTES = "Claim denied due to insufficient evidence and policy exclusions.";

  /** External Task Configuration */
  private static final String TEST_WORKER_ID = "test-worker";
  private static final long LOCK_DURATION = 30000L;
  private static final String POLICY_VALIDATION_TOPIC = "insurance.claim.policy-validate";
  private static final String CLAIM_CREATION_TOPIC = "insurance.claim.create-claim";
  private static final String ASSIGN_ADJUSTER_TOPIC = "insurance.claim.assign-adjuster";
  private static final String CLAIM_REJECTION_DECISION_TOPIC = "insurance.claim.reject-by-decision";

  /** Test Messages */
  private static final String REJECTION_NOTIFICATION_SUCCESS_MESSAGE = "Customer notified of claim rejection";
  private static final String REJECTION_NOTIFICATION_FAILURE_MESSAGE = "Failed to send rejection notification";

  /** BPMN Process Configuration */
  private static final String PROCESS_DEFINITION_KEY = "insurance-claim-process";
  private static final String BPMN_RESOURCE_PATH = "bpmn/User-Insurance.bpmn";

  /** BPMN Activity IDs */
  private static final String TASK_REGISTER_CLAIM = "task_register_claim";
  private static final String ACTIVITY_POLICY_VALIDATION = "Activity_1li9ull";
  private static final String ACTIVITY_CLAIM_CREATION = "Activity_091cv7u";
  private static final String ACTIVITY_ASSIGN_ADJUSTER = "Activity_0j7jttf";
  private static final String ACTIVITY_ADJUSTER_NOTIFICATION = "Event_0h1y0wn";
  private static final String ACTIVITY_WAIT_ADJUSTER_REPORT = "Event_1bzmong";
  private static final String ACTIVITY_PAYMENT_DECISION = "Activity_02ilvf7";
  private static final String ACTIVITY_DECISION_REJECTION = "Activity_0ymb84j";
  private static final String GATEWAY_POLICY_VALID = "Gateway_1tqc0u7";
  private static final String GATEWAY_PAYMENT_APPROVED = "Gateway_12e5x6w";
  private static final String START_EVENT = "StartEvent_1";
  private static final String END_EVENT_CLAIM_REJECTED = "Event_1l4zhuh";

  @RegisterExtension
  static ProcessEngineExtension extension =
      ProcessEngineExtension.builder().configurationResource("camunda.cfg.xml").build();

  private RuntimeService runtimeService;
  private ExternalTaskService externalTaskService;

  @BeforeEach
  void setUp() {
    runtimeService = extension.getRuntimeService();
    externalTaskService = extension.getExternalTaskService();
  }

  @Test
  @Deployment(resources = BPMN_RESOURCE_PATH)
  void claimProcessingValidPolicyToAdjusterReportWaitingTest() {
    // When - Start the process
    ProcessInstance processInstance =
        runtimeService.startProcessInstanceByKey(PROCESS_DEFINITION_KEY);

    // Then - Verify initial state
    assertThat(processInstance).isStarted();
    assertThat(processInstance).isWaitingAt(TASK_REGISTER_CLAIM);

    // And - Complete claim registration task with test data
    complete(
        task(processInstance),
        withVariables(
            ProcInstVars.POLICY_NUMBER, POLICY_NUMBER,
            ProcInstVars.CLAIM_TYPE, CLAIM_TYPE,
            ProcInstVars.CLAIM_ID, CLAIM_ID,
            ProcInstVars.CLAIM_FILE_NUMBER, CLAIM_FILE_NUMBER,
            ProcInstVars.CUSTOMER_FIRSTNAME, CUSTOMER_FIRSTNAME,
            ProcInstVars.CUSTOMER_LASTNAME, CUSTOMER_LASTNAME,
            ProcInstVars.CUSTOMER_NOTIFICATION_EMAIL, CUSTOMER_EMAIL));

    // Then - Verify process is waiting at policy validation service task
    assertThat(processInstance).isWaitingAt(ACTIVITY_POLICY_VALIDATION);

    // And - Complete policy validation external task with valid policy result
    completeExternalTask(processInstance, POLICY_VALIDATION_TOPIC,
        Map.of(ProcInstVars.IS_POLICY_VALID, true));

    // Then - Verify process is waiting at claim creation service task
    assertThat(processInstance).isWaitingAt(ACTIVITY_CLAIM_CREATION);

    // And - Complete claim creation external task
    completeExternalTask(processInstance, CLAIM_CREATION_TOPIC, Map.of());

    // Then - Verify process is waiting at assign adjuster service task
    assertThat(processInstance).isWaitingAt(ACTIVITY_ASSIGN_ADJUSTER);

    // And - Complete assign adjuster external task
    completeExternalTask(processInstance, ASSIGN_ADJUSTER_TOPIC,
        Map.of(ProcInstVars.ADJUSTER_ID, ADJUSTER_ID));

    // Then - Process continues to adjuster notification
    assertThat(processInstance).isWaitingAt(ACTIVITY_ADJUSTER_NOTIFICATION);

    // And - Complete adjuster notification external task
    completeExternalTask(processInstance, "insurance.claim.notification.adjuster-assigned", Map.of());

    // Then - Process should be waiting at the adjuster report message event
    assertThat(processInstance).isWaitingAt(ACTIVITY_WAIT_ADJUSTER_REPORT);

    // Verify the complete process path has been executed correctly
    assertThat(processInstance)
        .hasPassedInOrder(
            START_EVENT,
            TASK_REGISTER_CLAIM,
            ACTIVITY_POLICY_VALIDATION,
            GATEWAY_POLICY_VALID,
            ACTIVITY_CLAIM_CREATION,
            ACTIVITY_ASSIGN_ADJUSTER,
            ACTIVITY_ADJUSTER_NOTIFICATION);

    // And - Verify all required process variables are correctly set
    assertThat(processInstance)
        .variables()
        .containsEntry(ProcInstVars.IS_POLICY_VALID, true)
        .containsEntry(ProcInstVars.POLICY_NUMBER, POLICY_NUMBER)
        .containsEntry(ProcInstVars.CLAIM_ID, CLAIM_ID)
        .containsEntry(ProcInstVars.CLAIM_TYPE, CLAIM_TYPE)
        .containsEntry(ProcInstVars.CLAIM_FILE_NUMBER, CLAIM_FILE_NUMBER)
        .containsEntry(ProcInstVars.CUSTOMER_FIRSTNAME, CUSTOMER_FIRSTNAME)
        .containsEntry(ProcInstVars.CUSTOMER_LASTNAME, CUSTOMER_LASTNAME)
        .containsEntry(ProcInstVars.CUSTOMER_NOTIFICATION_EMAIL, CUSTOMER_EMAIL)
        .containsEntry(ProcInstVars.ADJUSTER_ID, ADJUSTER_ID);

    // Process is now correctly waiting for external adjuster report message
    // In real scenario, external system would send Message_2rgk10a to continue the process
  }

  @Test
  @Deployment(resources = BPMN_RESOURCE_PATH)
  void completeClaimRejectionByDecisionPathTest() {
    // When - Start the process
    ProcessInstance processInstance =
        runtimeService.startProcessInstanceByKey(PROCESS_DEFINITION_KEY);

    // And - Complete claim registration task with test data
    complete(
        task(processInstance),
        withVariables(
            ProcInstVars.POLICY_NUMBER, POLICY_NUMBER,
            ProcInstVars.CLAIM_TYPE, CLAIM_TYPE,
            ProcInstVars.CLAIM_ID, CLAIM_ID,
            ProcInstVars.CLAIM_FILE_NUMBER, CLAIM_FILE_NUMBER,
            ProcInstVars.CUSTOMER_FIRSTNAME, CUSTOMER_FIRSTNAME,
            ProcInstVars.CUSTOMER_LASTNAME, CUSTOMER_LASTNAME,
            ProcInstVars.CUSTOMER_NOTIFICATION_EMAIL, CUSTOMER_EMAIL));

    // And - Complete all external tasks up to adjuster notification
    completeExternalTask(processInstance, POLICY_VALIDATION_TOPIC,
        Map.of(ProcInstVars.IS_POLICY_VALID, true));

    completeExternalTask(processInstance, CLAIM_CREATION_TOPIC, Map.of());

    completeExternalTask(processInstance, ASSIGN_ADJUSTER_TOPIC,
        Map.of(ProcInstVars.ADJUSTER_ID, ADJUSTER_ID));

    completeExternalTask(processInstance, "insurance.claim.notification.adjuster-assigned", Map.of());

    // Then - Process should be waiting at the adjuster report message event
    assertThat(processInstance).isWaitingAt(ACTIVITY_WAIT_ADJUSTER_REPORT);

    // And - Trigger the message event manually using execution manipulation
    // This is a workaround for testing message correlation in unit tests

    // Set the adjuster report URL variable first
    runtimeService.setVariable(processInstance.getId(), ProcInstVars.ADJUSTER_REPORT_URL, ADJUSTER_REPORT_URL);

    // Find the execution waiting at the message event and trigger it directly
    String executionId = runtimeService.createExecutionQuery()
        .processInstanceId(processInstance.getId())
        .activityId(ACTIVITY_WAIT_ADJUSTER_REPORT)
        .singleResult()
        .getId();

    // Use signal to trigger the flow continuation (alternative to message correlation)
    runtimeService.signal(executionId);

    // Then - Verify process is waiting at payment decision user task
    assertThat(processInstance).isWaitingAt(ACTIVITY_PAYMENT_DECISION);

    // And - Complete payment decision task with rejection
    complete(
        task(processInstance),
        withVariables(
            ProcInstVars.DECISION_TYPE, DECISION_TYPE_REJECT,
            ProcInstVars.DECISION_NOTES, DECISION_NOTES));

    // Then - Verify process is waiting at decision rejection service task
    assertThat(processInstance).isWaitingAt(ACTIVITY_DECISION_REJECTION);

    // And - Complete decision rejection external task with successful notification
    completeExternalTask(processInstance, CLAIM_REJECTION_DECISION_TOPIC,
        Map.of(
            ProcInstVars.CLAIM_REJECTED_ADJUSTER_DECISION_NOTIFICATION_SENT_SUCCESSFULLY, true,
            ProcInstVars.CLAIM_REJECTED_ADJUSTER_DECISION_NOTIFICATION_MESSAGE, REJECTION_NOTIFICATION_SUCCESS_MESSAGE));

    // Then - Verify process ended at the correct end event
    assertThat(processInstance).isEnded();

    // And - Verify the complete process path was executed
    assertThat(processInstance)
        .hasPassedInOrder(
            START_EVENT,
            TASK_REGISTER_CLAIM,
            ACTIVITY_POLICY_VALIDATION,
            GATEWAY_POLICY_VALID,
            ACTIVITY_CLAIM_CREATION,
            ACTIVITY_ASSIGN_ADJUSTER,
            ACTIVITY_ADJUSTER_NOTIFICATION,
            ACTIVITY_WAIT_ADJUSTER_REPORT,
            ACTIVITY_PAYMENT_DECISION,
            GATEWAY_PAYMENT_APPROVED,
            ACTIVITY_DECISION_REJECTION,
            END_EVENT_CLAIM_REJECTED);

    // And - Verify all process variables are correctly set
    assertThat(processInstance)
        .variables()
        .containsEntry(ProcInstVars.IS_POLICY_VALID, true)
        .containsEntry(ProcInstVars.POLICY_NUMBER, POLICY_NUMBER)
        .containsEntry(ProcInstVars.CLAIM_ID, CLAIM_ID)
        .containsEntry(ProcInstVars.ADJUSTER_ID, ADJUSTER_ID)
        .containsEntry(ProcInstVars.ADJUSTER_REPORT_URL, ADJUSTER_REPORT_URL)
        .containsEntry(ProcInstVars.DECISION_TYPE, DECISION_TYPE_REJECT)
        .containsEntry(ProcInstVars.CLAIM_REJECTED_ADJUSTER_DECISION_NOTIFICATION_SENT_SUCCESSFULLY, true);
  }

  @Test
  @Deployment(resources = BPMN_RESOURCE_PATH)
  void completeClaimRejectionByDecisionWithFailedNotificationTest() {
    // When - Start the process and complete all steps up to decision rejection
    ProcessInstance processInstance =
        runtimeService.startProcessInstanceByKey(PROCESS_DEFINITION_KEY);

    complete(
        task(processInstance),
        withVariables(
            ProcInstVars.POLICY_NUMBER, POLICY_NUMBER,
            ProcInstVars.CLAIM_TYPE, CLAIM_TYPE,
            ProcInstVars.CLAIM_ID, CLAIM_ID,
            ProcInstVars.CLAIM_FILE_NUMBER, CLAIM_FILE_NUMBER,
            ProcInstVars.CUSTOMER_FIRSTNAME, CUSTOMER_FIRSTNAME,
            ProcInstVars.CUSTOMER_LASTNAME, CUSTOMER_LASTNAME,
            ProcInstVars.CUSTOMER_NOTIFICATION_EMAIL, CUSTOMER_EMAIL));

    // Complete all external tasks and user tasks up to decision rejection
    completeExternalTask(processInstance, POLICY_VALIDATION_TOPIC, Map.of(ProcInstVars.IS_POLICY_VALID, true));
    completeExternalTask(processInstance, CLAIM_CREATION_TOPIC, Map.of());
    completeExternalTask(processInstance, ASSIGN_ADJUSTER_TOPIC, Map.of(ProcInstVars.ADJUSTER_ID, ADJUSTER_ID));
    completeExternalTask(processInstance, "insurance.claim.notification.adjuster-assigned", Map.of());

    // Trigger message event workaround
    runtimeService.setVariable(processInstance.getId(), ProcInstVars.ADJUSTER_REPORT_URL, ADJUSTER_REPORT_URL);
    String executionId = runtimeService.createExecutionQuery()
        .processInstanceId(processInstance.getId())
        .activityId(ACTIVITY_WAIT_ADJUSTER_REPORT)
        .singleResult()
        .getId();
    runtimeService.signal(executionId);

    // Complete payment decision with rejection
    complete(
        task(processInstance),
        withVariables(
            ProcInstVars.DECISION_TYPE, DECISION_TYPE_REJECT,
            ProcInstVars.DECISION_NOTES, DECISION_NOTES));

    // And - Complete decision rejection with failed notification
    completeExternalTask(processInstance, CLAIM_REJECTION_DECISION_TOPIC,
        Map.of(
            ProcInstVars.CLAIM_REJECTED_ADJUSTER_DECISION_NOTIFICATION_SENT_SUCCESSFULLY, false,
            ProcInstVars.CLAIM_REJECTED_ADJUSTER_DECISION_NOTIFICATION_MESSAGE, REJECTION_NOTIFICATION_FAILURE_MESSAGE));

    // Then - Verify process still completes even with notification failure
    assertThat(processInstance).isEnded();
    assertThat(processInstance)
        .variables()
        .containsEntry(ProcInstVars.IS_POLICY_VALID, true)
        .containsEntry(ProcInstVars.DECISION_TYPE, DECISION_TYPE_REJECT)
        .containsEntry(ProcInstVars.ADJUSTER_REPORT_URL, ADJUSTER_REPORT_URL)
        .containsEntry(ProcInstVars.CLAIM_REJECTED_ADJUSTER_DECISION_NOTIFICATION_SENT_SUCCESSFULLY, false);
  }

  /**
   * Helper method to complete external tasks for a given process instance and topic.
   *
   * @param processInstance The process instance
   * @param topicName The external task topic name
   * @param variables Variables to set when completing the task
   */
  private void completeExternalTask(ProcessInstance processInstance, String topicName, Map<String, Object> variables) {
    List<ExternalTask> tasks = externalTaskService
        .createExternalTaskQuery()
        .processInstanceId(processInstance.getId())
        .topicName(topicName)
        .list();

    assertThat(tasks).hasSize(1);
    ExternalTask task = tasks.getFirst();

    // Lock and complete the external task
    externalTaskService
        .fetchAndLock(1, TEST_WORKER_ID)
        .topic(topicName, LOCK_DURATION)
        .execute();

    externalTaskService.complete(task.getId(), TEST_WORKER_ID, variables);
  }
}
