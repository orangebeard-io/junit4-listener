package io.orangebeard.listener;

import com.nordstrom.automation.junit.AtomicTest;
import com.nordstrom.automation.junit.MethodWatcher;
import com.nordstrom.automation.junit.RunWatcher;
import com.nordstrom.automation.junit.RunnerWatcher;
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

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.internal.AssumptionViolatedException;
import org.junit.internal.runners.model.ReflectiveCallable;
import org.junit.runners.model.FrameworkMethod;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.orangebeard.client.entity.LogLevel.error;
import static io.orangebeard.client.entity.LogLevel.info;

public class OrangebeardListener implements ShutdownListener, RunnerWatcher, RunWatcher, MethodWatcher<FrameworkMethod> {

    private OrangebeardV1Client orangebeardClient;

    private UUID testRunUUID;
    private final Map<String, UUID> suites = new HashMap<>();
    private final Map<String, UUID> tests = new HashMap<>();


    public OrangebeardListener() {}

    //constructor for unit test
    OrangebeardListener(OrangebeardV1Client client) {
        this.orangebeardClient = client;
    }

    @Override
    public void runStarted(Object runObject) {
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
    public void runFinished(Object runObject) {
        tests.values().forEach(test -> orangebeardClient.finishTestItem(test, new FinishTestItem(testRunUUID, Status.STOPPED, null, null)));
        suites.values().forEach(suite -> orangebeardClient.finishTestItem(suite, new FinishTestItem()));
        orangebeardClient.finishTestRun(testRunUUID, new FinishTestRun());
    }

    @Override
    public void testStarted(AtomicTest atomicTest) {
        startTest(atomicTest);
    }

    @Override
    public void testFinished(AtomicTest atomicTest) {
        String testName = atomicTest.getDescription().getMethodName();
        if (tests.containsKey(testName)) {
            orangebeardClient.finishTestItem(tests.get(testName), new FinishTestItem(testRunUUID, Status.PASSED, null, null));
            tests.remove(testName);
        }
    }

    @Override
    public void testFailure(AtomicTest atomicTest, Throwable throwable) {
        String testName = atomicTest.getDescription().getMethodName();
        UUID itemId = tests.get(testName);
        orangebeardClient.finishTestItem(itemId, new FinishTestItem(testRunUUID, Status.FAILED, null, null));
        orangebeardClient.log(new Log(testRunUUID, itemId, LogLevel.info, atomicTest.getDescription().toString()));
        orangebeardClient.log(new Log(testRunUUID, itemId, error, throwable.getMessage()));
        orangebeardClient.log(new Log(testRunUUID, itemId, info, ExceptionUtils.getStackTrace(throwable)));
        tests.remove(testName);
    }

    @Override
    public void testAssumptionFailure(AtomicTest atomicTest, AssumptionViolatedException e) {

    }

    @Override
    public void testIgnored(AtomicTest atomicTest) {
        UUID testId = startTest(atomicTest);
        orangebeardClient.finishTestItem(testId, new FinishTestItem(testRunUUID, Status.SKIPPED, null, null));
    }

    @Override
    public void onShutdown() {

    }

    @Override
    public void beforeInvocation(Object o, FrameworkMethod frameworkMethod, ReflectiveCallable reflectiveCallable) {
    }

    @Override
    public void afterInvocation(Object o, FrameworkMethod frameworkMethod, ReflectiveCallable reflectiveCallable, Throwable throwable) {

    }

    @Override
    public Class<FrameworkMethod> supportedType() {
        return FrameworkMethod.class;
    }

    private UUID startTest(AtomicTest atomicTest) {
        String suiteName = atomicTest.getDescription().getClassName();
        String testName = atomicTest.getDescription().getMethodName();
        if (!suites.containsKey(suiteName)) {
            UUID suiteId = orangebeardClient.startTestItem(null, new StartTestItem(testRunUUID, suiteName, TestItemType.SUITE, null, null));
            suites.put(suiteName, suiteId);
        }

        UUID testId = orangebeardClient.startTestItem(suites.get(suiteName), new StartTestItem(testRunUUID, testName, TestItemType.STEP, null, null));
        tests.put(testName, testId);
        return testId;
    }
}
