package io.orangebeard.listener;

import io.orangebeard.client.OrangebeardClient;
import io.orangebeard.client.OrangebeardV2Client;
import io.orangebeard.client.entity.StartTestRun;

import java.util.UUID;
import org.junit.Before;
import org.mockito.Mock;
import org.junit.Test;
import org.junit.runner.Description;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class OrangebeardListenerTest {
    @Mock
    private OrangebeardClient orangebeardClient;

    @Mock
    private OrangebeardListener orangebeardListener;

    @Before
    public void init() {
        // To test using the OrangebeardV1Client or the Orangebeard interface, simply change the mocked class here.
        orangebeardClient = mock(OrangebeardV2Client.class);
    }

    @Test
    public void when_a_run_is_started_on_the_listener_then_a_run_is_started_on_the_client() {
        UUID testRunUUID = UUID.fromString("49e7186d-e14d-4eeb-bc29-e36279d3b628");
        Description description = Description.createSuiteDescription("Suite Description");

        orangebeardListener = new OrangebeardListener(orangebeardClient);

        when(orangebeardClient.startTestRun(any(StartTestRun.class))).thenReturn(testRunUUID);

        orangebeardListener.testRunStarted(description);

        verify(orangebeardClient).startTestRun(any());
    }

    @Test
    public void when_a_suite_is_started_on_the_listener_then_an_item_is_started_on_the_client() {
        Description description = Description.createSuiteDescription("Suite Description");

        orangebeardListener = new OrangebeardListener(orangebeardClient);

        orangebeardListener.testSuiteStarted(description);

        verify(orangebeardClient).startTestItem(any(), any());
    }

    @Test
    public void when_a_suite_is_started_and_not_finished_it_will_not_be_started_again() {
        // When the Suite is in the "suites" map, an attempt to start it again will skip the "start" command.

        Description suiteDescription = Description.createSuiteDescription("Suite");
        orangebeardListener = new OrangebeardListener(orangebeardClient);

        orangebeardListener.testSuiteStarted(suiteDescription);
        orangebeardListener.testSuiteStarted(suiteDescription);

        verify(orangebeardClient, times(1)).startTestItem(any(), any());
    }

    @Test
    public void multiple_suites_can_be_tested() {
        Description runDescription = Description.createSuiteDescription("Test Run");
        Description suiteDescription1 = Description.createSuiteDescription("Suite 1");
        Description subSuiteDescription11 = Description.createSuiteDescription("SubSuite 1.1");
        Description subSuiteDescription12 = Description.createSuiteDescription("SubSuite 1.2");
        Description suiteDescription2 = Description.createSuiteDescription("Suite 2");
        Description subSuiteDescription21 = Description.createSuiteDescription("SubSuite 2.1");
        Description subSuiteDescription22 = Description.createSuiteDescription("SubSuite 2.2");

        suiteDescription1.addChild(subSuiteDescription11);
        suiteDescription1.addChild(subSuiteDescription12);

        suiteDescription2.addChild(subSuiteDescription21);
        suiteDescription2.addChild(subSuiteDescription22);

        orangebeardListener = new OrangebeardListener(orangebeardClient);
        orangebeardListener.testRunStarted(runDescription);
        orangebeardListener.testSuiteStarted(suiteDescription1);
        // Note that sub-suites are NOT automatically started.
        orangebeardListener.testSuiteStarted(subSuiteDescription11);
        orangebeardListener.testSuiteStarted(subSuiteDescription12);
        orangebeardListener.testSuiteStarted(suiteDescription2);
        orangebeardListener.testSuiteStarted(subSuiteDescription21);
        orangebeardListener.testSuiteStarted(subSuiteDescription22);

        verify(orangebeardClient, times(6)).startTestItem(any(), any());
    }
}
