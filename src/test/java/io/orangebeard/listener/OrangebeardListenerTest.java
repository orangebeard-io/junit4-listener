package io.orangebeard.listener;

import io.orangebeard.client.OrangebeardProperties;
import io.orangebeard.client.OrangebeardV2Client;
import io.orangebeard.client.entity.StartTestItem;
import io.orangebeard.client.entity.StartTestRun;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.junit.Test;
import org.junit.runner.Description;
import org.mockito.junit.MockitoJUnitRunner;

import static io.orangebeard.client.entity.TestItemType.SUITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class OrangebeardListenerTest {
    @Mock
    private OrangebeardV2Client orangebeardClient;

    @Mock
    private OrangebeardProperties orangebeardProperties;

    @InjectMocks
    private OrangebeardListener orangebeardListener;

    @Test
    public void when_a_run_is_started_on_the_listener_then_a_run_is_started_on_the_client() {
        Description description = Description.createSuiteDescription("Suite Description");
        when(orangebeardProperties.getTestSetName()).thenReturn("TestSetName");
        when(orangebeardProperties.getDescription()).thenReturn("Description");
        when(orangebeardProperties.getAttributes()).thenReturn(new HashSet<>());
        ArgumentCaptor<StartTestRun> captor = ArgumentCaptor.forClass(StartTestRun.class);

        orangebeardListener.testRunStarted(description);

        verify(orangebeardClient).startTestRun(captor.capture());

        assertEquals("TestSetName", captor.getValue().getName());
        assertEquals("Description", captor.getValue().getDescription());
        assertThat(captor.getValue().getAttributes()).isEmpty();
    }

    @Test
    public void when_a_suite_is_started_on_the_listener_then_an_item_is_started_on_the_client() {
        // Make sure the Testrun UUID has the value we want it to have.
        Description runDescription = Description.createSuiteDescription("Run Description");
        UUID testRunUUID = UUID.fromString("49e7186d-e14d-4eeb-bc29-e36279d3b628");
        when(orangebeardClient.startTestRun(any())).thenReturn(testRunUUID);
        // Prepare arguments and argument captor for the actual test suite.
        Description suiteDescription = Description.createSuiteDescription("Suite Description");
        ArgumentCaptor<StartTestItem> captor = ArgumentCaptor.forClass(StartTestItem.class);

        orangebeardListener.testRunStarted(runDescription);
        orangebeardListener.testSuiteStarted(suiteDescription);

        verify(orangebeardClient).startTestItem(eq(null), captor.capture());
        assertEquals(testRunUUID, captor.getValue().getTestRunUUID());
        assertEquals("Suite Description", captor.getValue().getName());
        assertEquals(SUITE, captor.getValue().getType());
    }

    @Test
    public void when_a_suite_is_started_and_not_finished_it_will_not_be_started_again() {
        // When the Suite is in the "suites" map, an attempt to start it again will skip the "start" command.

        Description suiteDescription = Description.createSuiteDescription("Suite");

        orangebeardListener.testSuiteStarted(suiteDescription);
        orangebeardListener.testSuiteStarted(suiteDescription);

        verify(orangebeardClient, times(1)).startTestItem(any(), any());
    }

    @Test
    public void multiple_suites_can_be_tested() {
        // Prepare test run and two test suites. Each suite contains two tests.
        Description runDescription = Description.createSuiteDescription("Run Description");
        UUID testRunUUID = UUID.fromString("49e7186d-e14d-4eeb-bc29-e36279d3b628");
        when(orangebeardClient.startTestRun(any())).thenReturn(testRunUUID);

        String suiteName1 = "com.example.suite1";
        Description suiteDescription1 = Description.createSuiteDescription(suiteName1);
        UUID suiteUuid1  = UUID.fromString("621d0026-0e28-47e0-921b-de5c8e9bd26f");

        String testName11 = "test11";
        Description testDescription11 = Description.createTestDescription(suiteName1, testName11);
        UUID testUuid11  = UUID.fromString("c7173102-5456-11ec-bf63-0242ac130002");

        String testName12 = "test12";
        Description testDescription12 = Description.createTestDescription(suiteName1, testName12);
        UUID testUuid12  = UUID.fromString("25af7061-cd31-4782-8513-80e6fdd30ad3");

        String suiteName2 = "com.example.suite2";
        Description suiteDescription2 = Description.createSuiteDescription(suiteName2);
        UUID suiteUuid2  = UUID.fromString("e952fd8b-9624-444f-9bbc-cccca2e32d19");

        String testName21 = "SubSuite 2.1";
        Description testDescription21 = Description.createTestDescription(suiteName2, testName21);
        UUID testUuid21  = UUID.fromString("c4be0af9-8f0f-478a-a66b-5697be40407a");

        String testName22 = "SubSuite 2.2";
        Description testDescription22 = Description.createTestDescription(suiteName2, testName22);
        UUID testUuid22  = UUID.fromString("b3650fc1-41bc-4b00-beec-8c4c1467cfd0");

        suiteDescription1.addChild(testDescription11);
        suiteDescription1.addChild(testDescription12);

        suiteDescription2.addChild(testDescription21);
        suiteDescription2.addChild(testDescription22);

        // Prepare the UUID's that orangebeardClient.startTestItem(...) should return.
        when(orangebeardClient.startTestItem(any(), any(StartTestItem.class)))
                .thenReturn(suiteUuid1)
                .thenReturn(testUuid11)
                .thenReturn(testUuid12)
                .thenReturn(suiteUuid2)
                .thenReturn(testUuid21)
                .thenReturn(testUuid22);

        // Prepare to capture the parameters passed to orangebeardClient.startTestItem(...).
        ArgumentCaptor<StartTestItem> testItemCaptor = ArgumentCaptor.forClass(StartTestItem.class);
        ArgumentCaptor<UUID> suiteUuidCaptor = ArgumentCaptor.forClass(UUID.class);


        // Start the run, the suites, and the tests contained in the suites.
        orangebeardListener.testRunStarted(runDescription);
        orangebeardListener.testSuiteStarted(suiteDescription1);
        orangebeardListener.testStarted(testDescription11);
        orangebeardListener.testStarted(testDescription12);
        orangebeardListener.testSuiteStarted(suiteDescription2);
        orangebeardListener.testStarted(testDescription21);
        orangebeardListener.testStarted(testDescription22);

        // Verify the calls, and capture the parameters that were passed.
        verify(orangebeardClient, times(6)).startTestItem(suiteUuidCaptor.capture(), testItemCaptor.capture());

        // Verify that the tests were put in the proper suites.
        List<StartTestItem> allStartTestItems = testItemCaptor.getAllValues();
        List<UUID> allUuids = suiteUuidCaptor.getAllValues();
        assertNull(allUuids.get(0));
        assertEquals(suiteUuid1, allUuids.get(1));
        assertEquals(suiteUuid1, allUuids.get(2));
        assertNull(allUuids.get(3));
        assertEquals(suiteUuid2, allUuids.get(4));
        assertEquals(suiteUuid2, allUuids.get(5));

        // Verify that the tests were registered with the right names.
        assertEquals(suiteName1, allStartTestItems.get(0).getName());
        assertEquals(testName11, allStartTestItems.get(1).getName());
        assertEquals(testName12, allStartTestItems.get(2).getName());

        assertEquals(suiteName2, allStartTestItems.get(3).getName());
        assertEquals(testName21, allStartTestItems.get(4).getName());
        assertEquals(testName22, allStartTestItems.get(5).getName());

    }

}
