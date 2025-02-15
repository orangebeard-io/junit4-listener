package io.orangebeard.listener;

import com.nordstrom.automation.junit.ShutdownListener;

import io.orangebeard.client.OrangebeardProperties;
import io.orangebeard.client.entity.FinishV3TestRun;
import io.orangebeard.client.entity.LogFormat;
import io.orangebeard.client.entity.StartV3TestRun;
import io.orangebeard.client.entity.log.Log;
import io.orangebeard.client.entity.suite.StartSuite;
import io.orangebeard.client.entity.test.FinishTest;
import io.orangebeard.client.entity.test.StartTest;
import io.orangebeard.client.entity.test.TestStatus;
import io.orangebeard.client.entity.test.TestType;
import io.orangebeard.client.v3.OrangebeardAsyncV3Client;

import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import javax.annotation.Nonnull;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.orangebeard.client.entity.log.LogLevel.*;

public class OrangebeardListener extends RunListener implements ShutdownListener {

    private final OrangebeardAsyncV3Client orangebeardClient;
    private final OrangebeardProperties properties;

    private UUID testRunUUID;
    private final Map<String, UUID> suites = new HashMap<>();
    private final Map<String, UUID> tests = new HashMap<>();

    /**
     * Public no-argument constructor, for use by the JUnitWatcher that uses the JUnit4 Listener.
     */
    public OrangebeardListener() {
        System.out.println("Init listener");
        properties = new OrangebeardProperties();
        System.out.println(properties.requiredValuesArePresent());
        properties.checkPropertiesArePresent();
        orangebeardClient = new OrangebeardAsyncV3Client();
    }

    /**
     * Parameterized constructor: used by component tests.
     *
     * @param orangebeardClient     A non-null instance of an Orangebeard client.
     * @param orangebeardProperties The Orangebeard properties for this listener: endpoint, access token, description, etc.
     */
    protected OrangebeardListener(@Nonnull OrangebeardAsyncV3Client orangebeardClient, @Nonnull OrangebeardProperties orangebeardProperties) {
        this.orangebeardClient = orangebeardClient;
        properties = orangebeardProperties;
    }

    @Override
    public void testRunStarted(Description description) {
        System.out.println("Starting run...");
        if (testRunUUID == null) {
            this.testRunUUID = orangebeardClient.startTestRun(new StartV3TestRun(properties.getTestSetName(), properties.getDescription(), properties.getAttributes()));
        }
        System.out.println("Started run: " + testRunUUID);
    }

    @Override
    public void testRunFinished(Result result) {
        finishTestRun();
    }

    @Override
    public void testSuiteStarted(Description description) {
        String suiteName = getSuiteName(description);
        System.out.println("Suite start: " + suiteName);

        suites.computeIfAbsent(suiteName,
                key -> {
                    StartSuite startSuite = StartSuite.builder().testRunUUID(testRunUUID).suiteNames(List.of(suiteName)).build();
                    List<UUID> createdSuites = orangebeardClient.startSuite(startSuite);
                    return createdSuites.get(createdSuites.size() - 1);
                }
        );
    }

    @Override
    public void testSuiteFinished(Description description) {
        String suiteName = getSuiteName(description);
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
            orangebeardClient.finishTest(tests.get(testName), new FinishTest(testRunUUID, TestStatus.PASSED, ZonedDateTime.now()));
            tests.remove(testName);
        }
    }

    @Override
    public void testFailure(Failure failure) {
        String testName = failure.getDescription().getMethodName();
        UUID itemId = tests.get(testName);

        if (itemId != null) {
            orangebeardClient.log(new Log(testRunUUID, itemId, null, failure.getMessage(), ERROR, ZonedDateTime.now(), LogFormat.PLAIN_TEXT));
            orangebeardClient.log(new Log(testRunUUID, itemId, null, failure.getTrace(), DEBUG, ZonedDateTime.now(), LogFormat.PLAIN_TEXT));
            orangebeardClient.finishTest(itemId, new FinishTest(testRunUUID, TestStatus.FAILED, ZonedDateTime.now()));
            tests.remove(testName);
        }
    }

    @Override
    public void testAssumptionFailure(Failure failure) {
        String testName = failure.getDescription().getMethodName();
        UUID itemId = tests.get(testName);
        orangebeardClient.finishTest(itemId, new FinishTest(testRunUUID, TestStatus.SKIPPED, ZonedDateTime.now()));
        orangebeardClient.log(new Log(testRunUUID, itemId, null, failure.getMessage(), INFO, ZonedDateTime.now(), LogFormat.PLAIN_TEXT));
    }

    @Override
    public void testIgnored(Description description) {
        UUID testId = startTest(description);
        orangebeardClient.finishTest(testId, new FinishTest(testRunUUID, TestStatus.SKIPPED, ZonedDateTime.now()));
        tests.remove(getTestName(description));
    }

    @Override
    public void onShutdown() {
        finishTestRun();
    }

    private void finishTestRun() {
        System.out.println("Finishing run: " + testRunUUID);
        tests.values().forEach(testUUID -> orangebeardClient.finishTest(testUUID, new FinishTest(testRunUUID, TestStatus.STOPPED, ZonedDateTime.now())));
        orangebeardClient.finishTestRun(testRunUUID, new FinishV3TestRun());
    }

    private UUID startTest(Description description) {
        String testName = getTestName(description);
        System.out.println("Test start: " + testName);
        UUID testId = orangebeardClient.startTest(new StartTest(testRunUUID, getSuiteUUID(description), testName, TestType.TEST, null, null, ZonedDateTime.now()));
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
        return suites.get(getSuiteName(description));
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
