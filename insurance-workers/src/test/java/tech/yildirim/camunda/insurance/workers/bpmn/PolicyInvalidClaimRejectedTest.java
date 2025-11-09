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

class PolicyInvalidClaimRejectedTest {

  /** Test Variables */
  private static final String POLICY_NUMBER = "AUTO-POL-TEST";

  private static final String CLAIM_TYPE = "AUTO";
  private static final Long CLAIM_ID = 1001L;
  private static final String CLAIM_FILE_NUMBER = "CLAIM-1001";
  private static final String CUSTOMER_FIRSTNAME = "Max";
  private static final String CUSTOMER_LASTNAME = "Mustermann";
  private static final String CUSTOMER_EMAIL = "max.mustermann@example.com";

  /** External Task Configuration */
  private static final String TEST_WORKER_ID = "test-worker";

  private static final long LOCK_DURATION = 30000L;
  private static final String POLICY_VALIDATION_TOPIC = "insurance.claim.policy-validate";
  private static final String CLAIM_REJECTION_TOPIC = "insurance.claim.reject-invalid-policy";

  /** Test Messages */
  private static final String NOTIFICATION_SUCCESS_MESSAGE = "Customer notified of rejection";

  private static final String NOTIFICATION_FAILURE_MESSAGE = "Failed to send notification";

  /** BPMN Process Configuration */
  private static final String PROCESS_DEFINITION_KEY = "insurance-claim-process";

  private static final String BPMN_RESOURCE_PATH = "bpmn/User-Insurance.bpmn";

  /** BPMN Activity IDs */
  private static final String TASK_REGISTER_CLAIM = "task_register_claim";

  private static final String ACTIVITY_POLICY_VALIDATION = "Activity_1li9ull";
  private static final String ACTIVITY_CLAIM_REJECTION = "Activity_1jnny9b";
  private static final String ACTIVITY_CLAIM_CREATION = "Activity_091cv7u";
  private static final String GATEWAY_POLICY_VALID = "Gateway_1tqc0u7";
  private static final String START_EVENT = "StartEvent_1";
  private static final String END_EVENT_POLICY_INVALID = "Event_0vfvzzg";

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
  void policyInvalidClaimRejectedTest() {
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

    // And - Complete policy validation external task with invalid policy result
    List<ExternalTask> policyValidationTasks =
        externalTaskService
            .createExternalTaskQuery()
            .processInstanceId(processInstance.getId())
            .topicName(POLICY_VALIDATION_TOPIC)
            .list();

    assertThat(policyValidationTasks).hasSize(1);
    ExternalTask policyTask = policyValidationTasks.getFirst();

    // Lock and complete the external task
    externalTaskService
        .fetchAndLock(1, TEST_WORKER_ID)
        .topic(POLICY_VALIDATION_TOPIC, LOCK_DURATION)
        .execute();

    externalTaskService.complete(
        policyTask.getId(), TEST_WORKER_ID, Map.of(ProcInstVars.IS_POLICY_VALID, false));

    // Then - Verify process is waiting at claim rejection service task
    assertThat(processInstance).isWaitingAt(ACTIVITY_CLAIM_REJECTION);

    // And - Complete claim rejection external task
    List<ExternalTask> rejectionTasks =
        externalTaskService
            .createExternalTaskQuery()
            .processInstanceId(processInstance.getId())
            .topicName(CLAIM_REJECTION_TOPIC)
            .list();

    assertThat(rejectionTasks).hasSize(1);
    ExternalTask rejectionTask = rejectionTasks.getFirst();

    // Lock and complete the external task
    externalTaskService
        .fetchAndLock(1, TEST_WORKER_ID)
        .topic(CLAIM_REJECTION_TOPIC, LOCK_DURATION)
        .execute();

    externalTaskService.complete(
        rejectionTask.getId(),
        TEST_WORKER_ID,
        Map.of(
            ProcInstVars.CLAIM_REJECTED_INVALID_POLICY_NOTIFICATION_SENT_SUCCESSFULLY,
            true,
            ProcInstVars.CLAIM_REJECTED_INVALID_POLICY_NOTIFICATION_MESSAGE,
            NOTIFICATION_SUCCESS_MESSAGE));

    // Then - Verify process ended at the correct end event
    assertThat(processInstance).isEnded();
    assertThat(processInstance)
        .hasPassedInOrder(
            START_EVENT,
            TASK_REGISTER_CLAIM,
            ACTIVITY_POLICY_VALIDATION, // Check Policy Validity
            GATEWAY_POLICY_VALID, // Policy Valid? gateway
            ACTIVITY_CLAIM_REJECTION, // Inform Customer and Close
            END_EVENT_POLICY_INVALID // File Closed - Policy Not Valid
            );

    // And - Verify process variables are set correctly
    assertThat(processInstance)
        .variables()
        .containsEntry(ProcInstVars.IS_POLICY_VALID, false)
        .containsEntry(ProcInstVars.POLICY_NUMBER, POLICY_NUMBER)
        .containsEntry(ProcInstVars.CLAIM_ID, CLAIM_ID)
        .containsEntry(
            ProcInstVars.CLAIM_REJECTED_INVALID_POLICY_NOTIFICATION_SENT_SUCCESSFULLY, true);
  }

  @Test
  @Deployment(resources = BPMN_RESOURCE_PATH)
  void policyInvalidClaimRejectedWithNotificationFailureTest() {
    // When - Start the process
    ProcessInstance processInstance =
        runtimeService.startProcessInstanceByKey(PROCESS_DEFINITION_KEY);

    // And - Complete claim registration task
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

    // And - Complete policy validation with invalid result
    List<ExternalTask> policyValidationTasks =
        externalTaskService
            .createExternalTaskQuery()
            .processInstanceId(processInstance.getId())
            .topicName(POLICY_VALIDATION_TOPIC)
            .list();

    ExternalTask policyTask = policyValidationTasks.getFirst();

    // Lock and complete the external task
    externalTaskService
        .fetchAndLock(1, TEST_WORKER_ID)
        .topic(POLICY_VALIDATION_TOPIC, LOCK_DURATION)
        .execute();

    externalTaskService.complete(
        policyTask.getId(), TEST_WORKER_ID, Map.of(ProcInstVars.IS_POLICY_VALID, false));

    // And - Complete claim rejection with notification failure
    List<ExternalTask> rejectionTasks =
        externalTaskService
            .createExternalTaskQuery()
            .processInstanceId(processInstance.getId())
            .topicName(CLAIM_REJECTION_TOPIC)
            .list();

    ExternalTask rejectionTask = rejectionTasks.getFirst();

    // Lock and complete the external task
    externalTaskService
        .fetchAndLock(1, TEST_WORKER_ID)
        .topic(CLAIM_REJECTION_TOPIC, LOCK_DURATION)
        .execute();

    externalTaskService.complete(
        rejectionTask.getId(),
        TEST_WORKER_ID,
        Map.of(
            ProcInstVars.CLAIM_REJECTED_INVALID_POLICY_NOTIFICATION_SENT_SUCCESSFULLY,
            false,
            ProcInstVars.CLAIM_REJECTED_INVALID_POLICY_NOTIFICATION_MESSAGE,
            NOTIFICATION_FAILURE_MESSAGE));

    // Then - Verify process still completes even with notification failure
    assertThat(processInstance).isEnded();
    assertThat(processInstance)
        .variables()
        .containsEntry(ProcInstVars.IS_POLICY_VALID, false)
        .containsEntry(
            ProcInstVars.CLAIM_REJECTED_INVALID_POLICY_NOTIFICATION_SENT_SUCCESSFULLY, false);
  }

  @Test
  @Deployment(resources = BPMN_RESOURCE_PATH)
  void policyValidClaimProcessingTest() {
    // When - Start the process
    ProcessInstance processInstance =
        runtimeService.startProcessInstanceByKey(PROCESS_DEFINITION_KEY);

    // And - Complete claim registration task
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

    // And - Complete policy validation with valid result
    List<ExternalTask> policyValidationTasks =
        externalTaskService
            .createExternalTaskQuery()
            .processInstanceId(processInstance.getId())
            .topicName(POLICY_VALIDATION_TOPIC)
            .list();

    ExternalTask policyTask = policyValidationTasks.getFirst();

    // Lock and complete the external task
    externalTaskService
        .fetchAndLock(1, TEST_WORKER_ID)
        .topic(POLICY_VALIDATION_TOPIC, LOCK_DURATION)
        .execute();

    externalTaskService.complete(
        policyTask.getId(), TEST_WORKER_ID, Map.of(ProcInstVars.IS_POLICY_VALID, true));

    // Then - Verify process continues to claim creation (valid policy path)
    assertThat(processInstance).isWaitingAt(ACTIVITY_CLAIM_CREATION); // Create Claim service task

    // And - Verify process variables are set correctly
    assertThat(processInstance)
        .variables()
        .containsEntry(ProcInstVars.IS_POLICY_VALID, true)
        .containsEntry(ProcInstVars.POLICY_NUMBER, POLICY_NUMBER)
        .containsEntry(ProcInstVars.CLAIM_ID, CLAIM_ID);
  }
}
