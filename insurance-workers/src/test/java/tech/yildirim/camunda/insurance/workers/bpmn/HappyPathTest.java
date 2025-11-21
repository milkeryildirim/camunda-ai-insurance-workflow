package tech.yildirim.camunda.insurance.workers.bpmn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.complete;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.task;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.withVariables;

import java.math.BigDecimal;
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
 * Integration test for the complete happy path of insurance claim processing workflow.
 * Tests the entire process flow from start (StartEvent_1) to successful completion (Event_0c2z62e).
 *
 * This test validates the successful processing path where:
 * - Policy is valid
 * - Claim is created and adjuster assigned
 * - Payment decision is approved
 * - Invoice provider is checked (both partnered and non-partnered scenarios)
 * - Payment is calculated and executed
 * - Process ends with claim being paid
 */
class HappyPathTest {

  /** Test Variables - Basic Information */
  private static final String POLICY_NUMBER = "AUTO-POL-VALID-456";
  private static final String CLAIM_TYPE = "AUTO";
  private static final Long CLAIM_ID = 3001L;
  private static final String CLAIM_FILE_NUMBER = "CLAIM-3001";
  private static final String CUSTOMER_FIRSTNAME = "John";
  private static final String CUSTOMER_LASTNAME = "Doe";
  private static final String CUSTOMER_EMAIL = "john.doe@example.com";

  /** Adjuster Information */
  private static final Long ADJUSTER_ID = 201L;
  private static final String ADJUSTER_REPORT_URL = "https://example.com/reports/adjuster-report-3001.pdf";

  /** Decision Information */
  private static final String DECISION_TYPE_APPROVE = "approve";
  private static final String DECISION_NOTES = "Claim approved after thorough review. All documentation is in order.";

  /** Invoice and Payment Information */
  private static final BigDecimal INVOICE_AMOUNT = new BigDecimal("2500.00");
  private static final String INVOICE_DETAILS = "Vehicle repair invoice from certified garage";
  private static final BigDecimal APPROVED_AMOUNT_PARTNERED = new BigDecimal("2500.00"); // 100%
  private static final BigDecimal APPROVED_AMOUNT_NON_PARTNERED = new BigDecimal("2000.00"); // 80%
  private static final BigDecimal PAID_AMOUNT = new BigDecimal("2500.00");

  /** External Task Configuration */
  private static final String TEST_WORKER_ID = "test-worker";
  private static final long LOCK_DURATION = 30000L;

  /** External Task Topics */
  private static final String POLICY_VALIDATION_TOPIC = "insurance.claim.policy-validate";
  private static final String CLAIM_CREATION_TOPIC = "insurance.claim.create-claim";
  private static final String ASSIGN_ADJUSTER_TOPIC = "insurance.claim.assign-adjuster";
  private static final String ADJUSTER_NOTIFICATION_TOPIC = "insurance.claim.notification.adjuster-assigned";
  private static final String REPAIR_APPROVAL_TOPIC = "insurance.claim.repair-approve";
  private static final String FULL_PAYMENT_CALCULATION_TOPIC = "insurance.payment.calculate-full";
  private static final String PARTIAL_PAYMENT_CALCULATION_TOPIC = "insurance.payment.calculate-partial";
  private static final String PAYMENT_EXECUTION_TOPIC = "insurance.payment.execute";

  /** BPMN Process Configuration */
  private static final String PROCESS_DEFINITION_KEY = "insurance-claim-process";
  private static final String BPMN_RESOURCE_PATH = "bpmn/User-Insurance.bpmn";

  /** BPMN Activity IDs */
  private static final String START_EVENT = "StartEvent_1";
  private static final String TASK_REGISTER_CLAIM = "task_register_claim";
  private static final String ACTIVITY_POLICY_VALIDATION = "Activity_1li9ull";
  private static final String GATEWAY_POLICY_VALID = "Gateway_1tqc0u7";
  private static final String ACTIVITY_CLAIM_CREATION = "Activity_091cv7u";
  private static final String ACTIVITY_ASSIGN_ADJUSTER = "Activity_0j7jttf";
  private static final String ACTIVITY_ADJUSTER_NOTIFICATION = "Event_0h1y0wn";
  private static final String ACTIVITY_WAIT_ADJUSTER_REPORT = "Event_1bzmong";
  private static final String ACTIVITY_PAYMENT_DECISION = "Activity_02ilvf7";
  private static final String GATEWAY_PAYMENT_APPROVED = "Gateway_12e5x6w";
  private static final String ACTIVITY_REPAIR_APPROVAL = "Activity_0obpjlx";
  private static final String ACTIVITY_WAIT_CUSTOMER_INVOICES = "Event_1pvbff1";
  private static final String ACTIVITY_CHECK_INVOICE_PROVIDER = "Activity_14v9jny";
  private static final String GATEWAY_INVOICE_PARTNERED_PROVIDER = "Gateway_0fg3b3j";
  private static final String ACTIVITY_CALCULATE_FULL_PAYMENT = "Activity_089s81e";
  private static final String ACTIVITY_CALCULATE_PARTIAL_PAYMENT = "Activity_179wz4a";
  private static final String GATEWAY_PAYMENT_CALCULATION_MERGE = "Gateway_1qaad37";
  private static final String ACTIVITY_EXECUTE_PAYMENT = "Activity_1kp5ekz";
  private static final String END_EVENT_CLAIM_PAID = "Event_0c2z62e";

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
  void completeHappyPathWithPartneredProviderTest() {
    // When - Start the insurance claim process
    ProcessInstance processInstance =
        runtimeService.startProcessInstanceByKey(PROCESS_DEFINITION_KEY);

    // Then - Verify process started and is waiting at claim registration
    assertThat(processInstance).isStarted();
    assertThat(processInstance).isWaitingAt(TASK_REGISTER_CLAIM);

    // And - Complete claim registration with customer and claim details
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

    // Then - Process should be waiting at policy validation
    assertThat(processInstance).isWaitingAt(ACTIVITY_POLICY_VALIDATION);

    // And - Complete policy validation with valid result
    completeExternalTask(processInstance, POLICY_VALIDATION_TOPIC,
        Map.of(ProcInstVars.IS_POLICY_VALID, true));

    // Then - Process should be waiting at claim creation
    assertThat(processInstance).isWaitingAt(ACTIVITY_CLAIM_CREATION);

    // And - Complete claim creation
    completeExternalTask(processInstance, CLAIM_CREATION_TOPIC, Map.of());

    // Then - Process should be waiting at adjuster assignment
    assertThat(processInstance).isWaitingAt(ACTIVITY_ASSIGN_ADJUSTER);

    // And - Complete adjuster assignment
    completeExternalTask(processInstance, ASSIGN_ADJUSTER_TOPIC,
        Map.of(ProcInstVars.ADJUSTER_ID, ADJUSTER_ID));

    // Then - Process should be waiting at adjuster notification
    assertThat(processInstance).isWaitingAt(ACTIVITY_ADJUSTER_NOTIFICATION);

    // And - Complete adjuster notification
    completeExternalTask(processInstance, ADJUSTER_NOTIFICATION_TOPIC, Map.of());

    // Then - Process should be waiting for adjuster report
    assertThat(processInstance).isWaitingAt(ACTIVITY_WAIT_ADJUSTER_REPORT);

    // And - Simulate receiving adjuster report (using signal workaround)
    runtimeService.setVariable(processInstance.getId(), ProcInstVars.ADJUSTER_REPORT_URL, ADJUSTER_REPORT_URL);
    String executionId = runtimeService.createExecutionQuery()
        .processInstanceId(processInstance.getId())
        .activityId(ACTIVITY_WAIT_ADJUSTER_REPORT)
        .singleResult()
        .getId();
    runtimeService.signal(executionId);

    // Then - Process should be waiting at payment decision
    assertThat(processInstance).isWaitingAt(ACTIVITY_PAYMENT_DECISION);

    // And - Complete payment decision with approval
    complete(
        task(processInstance),
        withVariables(
            ProcInstVars.DECISION_TYPE, DECISION_TYPE_APPROVE,
            ProcInstVars.DECISION_NOTES, DECISION_NOTES));

    // Then - Process should be waiting at repair approval
    assertThat(processInstance).isWaitingAt(ACTIVITY_REPAIR_APPROVAL);

    // And - Complete repair approval
    completeExternalTask(processInstance, REPAIR_APPROVAL_TOPIC, Map.of());

    // Then - Process should be waiting for customer invoices
    assertThat(processInstance).isWaitingAt(ACTIVITY_WAIT_CUSTOMER_INVOICES);

    // And - Simulate receiving customer invoices (using signal workaround)
    runtimeService.setVariable(processInstance.getId(), ProcInstVars.INVOICE_AMOUNT, INVOICE_AMOUNT);
    runtimeService.setVariable(processInstance.getId(), ProcInstVars.INVOICE_DETAILS, INVOICE_DETAILS);
    String invoiceExecutionId = runtimeService.createExecutionQuery()
        .processInstanceId(processInstance.getId())
        .activityId(ACTIVITY_WAIT_CUSTOMER_INVOICES)
        .singleResult()
        .getId();
    runtimeService.signal(invoiceExecutionId);

    // Then - Process should be waiting at invoice provider check
    assertThat(processInstance).isWaitingAt(ACTIVITY_CHECK_INVOICE_PROVIDER);

    // And - Complete invoice provider check indicating partnered provider
    complete(
        task(processInstance),
        withVariables(ProcInstVars.IS_PARTNERED_PROVIDER, true));

    // Then - Process should be waiting at full payment calculation (100%)
    assertThat(processInstance).isWaitingAt(ACTIVITY_CALCULATE_FULL_PAYMENT);

    // And - Complete full payment calculation
    completeExternalTask(processInstance, FULL_PAYMENT_CALCULATION_TOPIC,
        Map.of(ProcInstVars.APPROVED_AMOUNT, APPROVED_AMOUNT_PARTNERED));

    // Then - Process should be waiting at payment execution
    assertThat(processInstance).isWaitingAt(ACTIVITY_EXECUTE_PAYMENT);

    // And - Complete payment execution
    completeExternalTask(processInstance, PAYMENT_EXECUTION_TOPIC,
        Map.of(
            "payment_executed", true,
            "paid_amount", PAID_AMOUNT,
            "payment_status", "COMPLETED"));

    // Then - Process should be completed successfully
    assertThat(processInstance).isEnded();

    // And - Verify the complete happy path was executed
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
            ACTIVITY_REPAIR_APPROVAL,
            ACTIVITY_WAIT_CUSTOMER_INVOICES,
            ACTIVITY_CHECK_INVOICE_PROVIDER,
            GATEWAY_INVOICE_PARTNERED_PROVIDER,
            ACTIVITY_CALCULATE_FULL_PAYMENT,
            GATEWAY_PAYMENT_CALCULATION_MERGE,
            ACTIVITY_EXECUTE_PAYMENT,
            END_EVENT_CLAIM_PAID);

    // And - Verify all process variables are correctly set
    assertThat(processInstance)
        .variables()
        .containsEntry(ProcInstVars.IS_POLICY_VALID, true)
        .containsEntry(ProcInstVars.POLICY_NUMBER, POLICY_NUMBER)
        .containsEntry(ProcInstVars.CLAIM_ID, CLAIM_ID)
        .containsEntry(ProcInstVars.CLAIM_TYPE, CLAIM_TYPE)
        .containsEntry(ProcInstVars.ADJUSTER_ID, ADJUSTER_ID)
        .containsEntry(ProcInstVars.ADJUSTER_REPORT_URL, ADJUSTER_REPORT_URL)
        .containsEntry(ProcInstVars.DECISION_TYPE, DECISION_TYPE_APPROVE)
        .containsEntry(ProcInstVars.IS_PARTNERED_PROVIDER, true)
        .containsEntry(ProcInstVars.INVOICE_AMOUNT, INVOICE_AMOUNT)
        .containsEntry(ProcInstVars.APPROVED_AMOUNT, APPROVED_AMOUNT_PARTNERED)
        .containsEntry("payment_executed", true)
        .containsEntry("paid_amount", PAID_AMOUNT);
  }

  @Test
  @Deployment(resources = BPMN_RESOURCE_PATH)
  void completeHappyPathWithNonPartneredProviderTest() {
    // When - Start the insurance claim process
    ProcessInstance processInstance =
        runtimeService.startProcessInstanceByKey(PROCESS_DEFINITION_KEY);

    // Complete all steps up to invoice provider check with same data as partnered test
    executeCommonStepsUntilInvoiceCheck(processInstance);

    // And - Complete invoice provider check indicating NON-partnered provider
    complete(
        task(processInstance),
        withVariables(ProcInstVars.IS_PARTNERED_PROVIDER, false));

    // Then - Process should be waiting at partial payment calculation (80%)
    assertThat(processInstance).isWaitingAt(ACTIVITY_CALCULATE_PARTIAL_PAYMENT);

    // And - Complete partial payment calculation
    completeExternalTask(processInstance, PARTIAL_PAYMENT_CALCULATION_TOPIC,
        Map.of(ProcInstVars.APPROVED_AMOUNT, APPROVED_AMOUNT_NON_PARTNERED));

    // Then - Process should be waiting at payment execution
    assertThat(processInstance).isWaitingAt(ACTIVITY_EXECUTE_PAYMENT);

    // And - Complete payment execution
    completeExternalTask(processInstance, PAYMENT_EXECUTION_TOPIC,
        Map.of(
            "payment_executed", true,
            "paid_amount", APPROVED_AMOUNT_NON_PARTNERED,
            "payment_status", "COMPLETED"));

    // Then - Process should be completed successfully
    assertThat(processInstance).isEnded();

    // And - Verify the complete happy path was executed (with partial payment branch)
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
            ACTIVITY_REPAIR_APPROVAL,
            ACTIVITY_WAIT_CUSTOMER_INVOICES,
            ACTIVITY_CHECK_INVOICE_PROVIDER,
            GATEWAY_INVOICE_PARTNERED_PROVIDER,
            ACTIVITY_CALCULATE_PARTIAL_PAYMENT,
            GATEWAY_PAYMENT_CALCULATION_MERGE,
            ACTIVITY_EXECUTE_PAYMENT,
            END_EVENT_CLAIM_PAID);

    // And - Verify process variables for non-partnered provider scenario
    assertThat(processInstance)
        .variables()
        .containsEntry(ProcInstVars.IS_POLICY_VALID, true)
        .containsEntry(ProcInstVars.DECISION_TYPE, DECISION_TYPE_APPROVE)
        .containsEntry(ProcInstVars.IS_PARTNERED_PROVIDER, false)
        .containsEntry(ProcInstVars.INVOICE_AMOUNT, INVOICE_AMOUNT)
        .containsEntry(ProcInstVars.APPROVED_AMOUNT, APPROVED_AMOUNT_NON_PARTNERED)
        .containsEntry("payment_executed", true)
        .containsEntry("paid_amount", APPROVED_AMOUNT_NON_PARTNERED);
  }

  @Test
  @Deployment(resources = BPMN_RESOURCE_PATH)
  void verifyProcessDefinitionAndStartEventTest() {
    // When - Start the process
    ProcessInstance processInstance =
        runtimeService.startProcessInstanceByKey(PROCESS_DEFINITION_KEY);

    // Then - Verify process starts correctly
    assertThat(processInstance).isStarted();
    assertThat(processInstance).isWaitingAt(TASK_REGISTER_CLAIM);

    // And - Verify process definition key
    assertThat(processInstance.getProcessDefinitionKey()).isEqualTo(PROCESS_DEFINITION_KEY);

    // And - Verify start event was triggered
    assertThat(processInstance).hasPassedInOrder(START_EVENT);
  }

  /**
   * Helper method to execute common steps from start until invoice provider check.
   * This reduces code duplication between partnered and non-partnered provider tests.
   */
  private void executeCommonStepsUntilInvoiceCheck(ProcessInstance processInstance) {
    // Complete claim registration
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

    // Complete all external tasks up to repair approval
    completeExternalTask(processInstance, POLICY_VALIDATION_TOPIC,
        Map.of(ProcInstVars.IS_POLICY_VALID, true));
    completeExternalTask(processInstance, CLAIM_CREATION_TOPIC, Map.of());
    completeExternalTask(processInstance, ASSIGN_ADJUSTER_TOPIC,
        Map.of(ProcInstVars.ADJUSTER_ID, ADJUSTER_ID));
    completeExternalTask(processInstance, ADJUSTER_NOTIFICATION_TOPIC, Map.of());

    // Simulate adjuster report
    runtimeService.setVariable(processInstance.getId(), ProcInstVars.ADJUSTER_REPORT_URL, ADJUSTER_REPORT_URL);
    String executionId = runtimeService.createExecutionQuery()
        .processInstanceId(processInstance.getId())
        .activityId(ACTIVITY_WAIT_ADJUSTER_REPORT)
        .singleResult()
        .getId();
    runtimeService.signal(executionId);

    // Complete payment decision with approval
    complete(
        task(processInstance),
        withVariables(
            ProcInstVars.DECISION_TYPE, DECISION_TYPE_APPROVE,
            ProcInstVars.DECISION_NOTES, DECISION_NOTES));

    // Complete repair approval
    completeExternalTask(processInstance, REPAIR_APPROVAL_TOPIC, Map.of());

    // Simulate customer invoices
    runtimeService.setVariable(processInstance.getId(), ProcInstVars.INVOICE_AMOUNT, INVOICE_AMOUNT);
    runtimeService.setVariable(processInstance.getId(), ProcInstVars.INVOICE_DETAILS, INVOICE_DETAILS);
    String invoiceExecutionId = runtimeService.createExecutionQuery()
        .processInstanceId(processInstance.getId())
        .activityId(ACTIVITY_WAIT_CUSTOMER_INVOICES)
        .singleResult()
        .getId();
    runtimeService.signal(invoiceExecutionId);

    // Process should now be waiting at invoice provider check
    assertThat(processInstance).isWaitingAt(ACTIVITY_CHECK_INVOICE_PROVIDER);
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
