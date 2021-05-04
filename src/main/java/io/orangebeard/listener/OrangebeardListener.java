package io.orangebeard.listener;

import io.orangebeard.client.OrangebeardProperties;
import io.orangebeard.client.OrangebeardV1Client;
import io.orangebeard.client.entity.FinishTestItem;
import io.orangebeard.client.entity.FinishTestRun;
import io.orangebeard.client.entity.Log;
import io.orangebeard.client.entity.LogLevel;
import io.orangebeard.client.entity.StartTestItem;
import io.orangebeard.client.entity.StartTestRun;
import io.orangebeard.client.entity.Status;
import io.orangebeard.client.entity.TestItemType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import static io.orangebeard.client.entity.LogLevel.debug;
import static io.orangebeard.client.entity.LogLevel.error;

public class OrangebeardListener extends RunListener {

    private OrangebeardV1Client orangebeardClient;

    private UUID testRunUUID;
    private UUID currentSuite;
    private Status currentSuiteStatus = Status.PASSED;
    private final Map<String, UUID> suites = new HashMap<>();
    private final Map<String, UUID> tests = new HashMap<>();

    @Override
    public void testRunStarted(Description description) throws Exception {
        if (testRunUUID == null) {
            OrangebeardProperties orangebeardProperties = new OrangebeardProperties();
            orangebeardProperties.checkPropertiesArePresent();

            this.orangebeardClient = new OrangebeardV1Client(
                    orangebeardProperties.getEndpoint(),
                    orangebeardProperties.getAccessToken(),
                    orangebeardProperties.getProjectName(),
                    orangebeardProperties.requiredValuesArePresent());
            this.testRunUUID = orangebeardClient.startTestRun(new StartTestRun(orangebeardProperties.getTestSetName(), orangebeardProperties.getDescription(), orangebeardProperties.getAttributes()));
        }
    }

    @Override
    public void testRunFinished(Result result) throws Exception {
        System.out.println("FinishRun Called!");
        tests.values().forEach(test -> orangebeardClient.finishTestItem(test, new FinishTestItem(testRunUUID, Status.STOPPED, null, null)));
        suites.values().forEach(suite -> orangebeardClient.finishTestItem(suite, new FinishTestItem(testRunUUID, Status.STOPPED, null, null)));

        Status status = result.getFailureCount() > 0 ? Status.FAILED : Status.PASSED;
        orangebeardClient.finishTestRun(testRunUUID, new FinishTestRun(status));
    }

    @Override
    public void testSuiteStarted(Description description) throws Exception {
        getOrStartSuite(description);
    }

    @Override
    public void testSuiteFinished(Description description) throws Exception {
        orangebeardClient.finishTestItem(currentSuite, new FinishTestItem(testRunUUID, currentSuiteStatus, null, null));
        suites.values().remove(currentSuite);
        currentSuite = null;
        currentSuiteStatus = Status.PASSED;
    }

    @Override
    public void testStarted(Description description) throws Exception {
        startTest(description);
    }

    @Override
    public void testFinished(Description description) throws Exception {
        String testName = description.getMethodName();
        if (tests.containsKey(testName)) {
            orangebeardClient.finishTestItem(tests.get(testName), new FinishTestItem(testRunUUID, Status.PASSED, null, null));
            tests.remove(testName);
        }
    }

    @Override
    public void testFailure(Failure failure) throws Exception {
        System.out.println("Failure");
        String testName = failure.getDescription().getMethodName();
        UUID itemId = tests.get(testName);
        orangebeardClient.finishTestItem(itemId, new FinishTestItem(testRunUUID, Status.FAILED, null, null));
        orangebeardClient.log(new Log(testRunUUID, itemId, error, failure.getMessage()));
        orangebeardClient.log(new Log(testRunUUID, itemId, debug, failure.getTrace()));
        tests.remove(testName);
        currentSuiteStatus = Status.FAILED;
    }

    @Override
    public void testAssumptionFailure(Failure failure) {
        System.out.println("Assumption fail");
        String testName = failure.getDescription().getMethodName();
        UUID itemId = tests.get(testName);
        orangebeardClient.finishTestItem(itemId, new FinishTestItem(testRunUUID, Status.SKIPPED, null, null));
        orangebeardClient.log(new Log(testRunUUID, itemId, LogLevel.info, failure.getMessage()));
    }

    @Override
    public void testIgnored(Description description) throws Exception {
        System.out.println("Skipped: " + description.getDisplayName());
        UUID testId = startTest(description);
        orangebeardClient.finishTestItem(testId, new FinishTestItem(testRunUUID, Status.SKIPPED, null, null));
    }

    private UUID getOrStartSuite(Description description) {
        String suiteName;
        if(description.getClassName() != null) {
            suiteName = description.getClassName();
        } else {
            int lastDot = description.getDisplayName().lastIndexOf(".");
            suiteName = description.getDisplayName().substring(0,lastDot - 1);
        }
        System.out.println("Start suite: " + suiteName);
        if (!suites.containsKey(suiteName)) {
            currentSuite = orangebeardClient.startTestItem(null, new StartTestItem(testRunUUID, suiteName, TestItemType.SUITE, null, null));
            currentSuiteStatus = Status.PASSED;
            suites.put(suiteName, currentSuite);
        } else {
            currentSuite = suites.get(suiteName);
        }
        return currentSuite;
    }

    private UUID startTest(Description description) {
        String testName;
        if(description.getMethodName() != null) {
            testName = description.getMethodName();
        } else {
            int lastDot = description.getDisplayName().lastIndexOf(".");
            testName = description.getDisplayName().substring(lastDot + 1);
        }
        System.out.println("Start test (" + description.getDisplayName() + "): " + testName);
        UUID testId = orangebeardClient.startTestItem(currentSuite, new StartTestItem(testRunUUID, testName, TestItemType.STEP, null, null));
        tests.put(testName, testId);
        return testId;
    }
}
