package io.orangebeard.listener;

import io.orangebeard.client.OrangebeardProperties;
import io.orangebeard.client.OrangebeardV2Client;
import io.orangebeard.client.entity.StartTestItem;
import io.orangebeard.client.entity.StartTestRun;

import java.util.HashSet;
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

        assertThat(captor.getValue().getName()).isEqualTo("TestSetName");
        assertThat(captor.getValue().getDescription()).isEqualTo("Description");
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
        assertThat(captor.getValue().getTestRunUUID()).isEqualTo(testRunUUID);
        assertThat(captor.getValue().getName()).isEqualTo("Suite Description");
        assertThat(captor.getValue().getType()).isEqualTo(SUITE);
    }

    @Test
    public void when_a_suite_is_started_and_not_finished_it_will_not_be_started_again() {
        // When the Suite is in the "suites" map, an attempt to start it again will skip the "start" command.

        Description suiteDescription = Description.createSuiteDescription("Suite");

        orangebeardListener.testSuiteStarted(suiteDescription);
        orangebeardListener.testSuiteStarted(suiteDescription);

        verify(orangebeardClient, times(1)).startTestItem(any(), any());
    }
}
