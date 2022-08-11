package io.orangebeard.listener;

import com.nordstrom.automation.junit.ShutdownListener;

import io.orangebeard.client.OrangebeardClient;
import io.orangebeard.client.OrangebeardProperties;
import io.orangebeard.client.OrangebeardV2Client;
import io.orangebeard.client.entity.FinishTestItem;
import io.orangebeard.client.entity.FinishTestRun;
import io.orangebeard.client.entity.Log;
import io.orangebeard.client.entity.LogFormat;
import io.orangebeard.client.entity.LogLevel;
import io.orangebeard.client.entity.StartTestItem;
import io.orangebeard.client.entity.StartTestRun;
import io.orangebeard.client.entity.Status;
import io.orangebeard.listener.entity.SuiteInfo;

import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.orangebeard.client.entity.LogLevel.debug;
import static io.orangebeard.client.entity.LogLevel.error;
import static io.orangebeard.client.entity.Status.FAILED;
import static io.orangebeard.client.entity.Status.PASSED;
import static io.orangebeard.client.entity.Status.SKIPPED;
import static io.orangebeard.client.entity.Status.STOPPED;
import static io.orangebeard.client.entity.TestItemType.TEST;
import static io.orangebeard.client.entity.TestItemType.SUITE;

public class OrangebeardListener extends RunListener implements ShutdownListener {

    private final OrangebeardClient orangebeardClient;
    private final OrangebeardProperties properties;

    private UUID testRunUUID;
    private Status runStatus = PASSED;
    private final Map<String, SuiteInfo> suites = new HashMap<>();
    private final Map<String, UUID> tests = new HashMap<>();

    /**
     * Public no-argument constructor, for use by the JUnitWatcher that uses the JUnit4 Listener.
     */
    public OrangebeardListener() {
        properties = new OrangebeardProperties();
        properties.checkPropertiesArePresent();

        orangebeardClient = new OrangebeardV2Client(
                properties.getEndpoint(),
                properties.getAccessToken(),
                properties.getProjectName(),
                properties.requiredValuesArePresent());
    }

    /**
     * Parameterized constructor: used by component tests.
     *
     * @param orangebeardClient     A non-null instance of an Orangebeard client.
     * @param orangebeardProperties The Orangebeard properties for this listener: endpoint, access token, description, etc.
     */
    protected OrangebeardListener(@Nonnull OrangebeardClient orangebeardClient, @Nonnull OrangebeardProperties orangebeardProperties) {
        this.orangebeardClient = orangebeardClient;
        properties = orangebeardProperties;
    }

    @Override
    public void testRunStarted(Description description) {
        if (testRunUUID == null) {
            this.testRunUUID = orangebeardClient.startTestRun(new StartTestRun(properties.getTestSetName(), properties.getDescription(), properties.getAttributes()));
        }
    }

    @Override
    public void testRunFinished(Result result) {
        finishTestRun();
    }

    @Override
    public void testSuiteStarted(Description description) {
        String suiteName = getSuiteName(description);

        suites.computeIfAbsent(suiteName,
                key -> {
                    UUID suiteUUID = orangebeardClient.startTestItem(null, new StartTestItem(testRunUUID, suiteName, SUITE));
                    return new SuiteInfo(suiteUUID);
                }
        );
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
            orangebeardClient.log(new Log(testRunUUID, itemId, error, failure.getMessage(), LogFormat.PLAIN_TEXT));
            orangebeardClient.log(new Log(testRunUUID, itemId, debug, failure.getTrace(), LogFormat.PLAIN_TEXT));
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
        orangebeardClient.log(new Log(testRunUUID, itemId, LogLevel.info, failure.getMessage(), LogFormat.PLAIN_TEXT));
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

        UUID testId = orangebeardClient.startTestItem(getSuiteUUID(description), new StartTestItem(testRunUUID, testName, TEST));
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
