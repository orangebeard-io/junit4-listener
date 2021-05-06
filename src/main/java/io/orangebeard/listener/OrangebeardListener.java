package io.orangebeard.listener;

import com.nordstrom.automation.junit.ShutdownListener;

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
import io.orangebeard.listener.entity.SuiteInfo;

import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.orangebeard.client.entity.LogLevel.debug;
import static io.orangebeard.client.entity.LogLevel.error;
import static io.orangebeard.client.entity.Status.FAILED;
import static io.orangebeard.client.entity.Status.PASSED;
import static io.orangebeard.client.entity.Status.SKIPPED;
import static io.orangebeard.client.entity.Status.STOPPED;

public class OrangebeardListener extends RunListener implements ShutdownListener {

    private OrangebeardV1Client orangebeardClient;

    private UUID testRunUUID;
    private Status runStatus = PASSED;
    private final Map<String, SuiteInfo> suites = new HashMap<>();
    private final Map<String, UUID> tests = new HashMap<>();

    @Override
    public void testRunStarted(Description description) {
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
    public void testRunFinished(Result result) {
        finishTestRun();
    }

    @Override
    public void testSuiteStarted(Description description) {
        String suiteName = getSuiteName(description);

        if (!suites.containsKey(suiteName)) {
            UUID suiteUUID = orangebeardClient.startRootItem(new StartTestItem(testRunUUID, suiteName, TestItemType.SUITE));
            suites.put(suiteName, new SuiteInfo(suiteUUID));
        }
    }

    @Override
    public void testSuiteFinished(Description description) {
        String suiteName = getSuiteName(description);
        SuiteInfo suiteInfo = suites.get(suiteName);
        orangebeardClient.finishTestItem(suiteInfo.getSuiteUUID(), new FinishTestItem(testRunUUID, suiteInfo.getStatus()));
        suites.remove(suiteName);
    }

    @Override
    public void testStarted(Description description) {
        startTest(description);
    }

    @Override
    public void testFinished(Description description) {
        String testName = description.getMethodName();
        if (tests.containsKey(testName)) {
            orangebeardClient.finishTestItem(tests.get(testName), new FinishTestItem(testRunUUID, PASSED));
            tests.remove(testName);
        }
    }

    @Override
    public void testFailure(Failure failure) {
        runStatus = FAILED;
        String testName = failure.getDescription().getMethodName();
        UUID itemId = tests.get(testName);
        SuiteInfo suiteInfo = suites.get(getSuiteName(failure.getDescription()));

        if (itemId != null) {
            orangebeardClient.finishTestItem(itemId, new FinishTestItem(testRunUUID, FAILED));
            orangebeardClient.log(new Log(testRunUUID, itemId, error, failure.getMessage()));
            orangebeardClient.log(new Log(testRunUUID, itemId, debug, failure.getTrace()));
            tests.remove(testName);
        }
        if (suiteInfo != null) {
            suiteInfo.setStatus(FAILED);
        }
    }

    @Override
    public void testAssumptionFailure(Failure failure) {
        String testName = failure.getDescription().getMethodName();
        UUID itemId = tests.get(testName);
        orangebeardClient.finishTestItem(itemId, new FinishTestItem(testRunUUID, SKIPPED));
        orangebeardClient.log(new Log(testRunUUID, itemId, LogLevel.info, failure.getMessage()));
    }

    @Override
    public void testIgnored(Description description) {
        UUID testId = startTest(description);
        orangebeardClient.finishTestItem(testId, new FinishTestItem(testRunUUID, SKIPPED));
        tests.remove(getTestName(description));
    }

    @Override
    public void onShutdown() {
        finishTestRun();
    }

    private void finishTestRun() {
        //Forceful cleanup
        tests.values().forEach(testUUID -> orangebeardClient.finishTestItem(testUUID, new FinishTestItem(testRunUUID, STOPPED)));
        suites.values().forEach(suiteInfo -> orangebeardClient.finishTestItem(suiteInfo.getSuiteUUID(), new FinishTestItem(testRunUUID, STOPPED)));

        orangebeardClient.finishTestRun(testRunUUID, new FinishTestRun(runStatus));
    }

    private UUID startTest(Description description) {
        String testName = getTestName(description);

        UUID testId = orangebeardClient.startTestItem(getSuiteUUID(description), new StartTestItem(testRunUUID, testName, TestItemType.STEP));
        tests.put(testName, testId);
        return testId;
    }

    private String getTestName(Description description) {
        if (description.getMethodName() != null) {
            return description.getMethodName();
        }
        int lastDot = description.getDisplayName().lastIndexOf(".");
        return description.getDisplayName().substring(lastDot + 1);
    }

    private UUID getSuiteUUID(Description description) {
        return suites.get(getSuiteName(description)).getSuiteUUID();
    }

    private String getSuiteName(Description description) {
        String suiteName;

        if (description.getClassName() != null) {
            suiteName = description.getClassName();
        } else {
            int lastDot = description.getDisplayName().lastIndexOf(".");
            suiteName = description.getDisplayName().substring(0, lastDot - 1);
        }
        return suiteName;
    }
}
