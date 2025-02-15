package io.orangebeard.listener;

import io.orangebeard.client.OrangebeardProperties;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import io.orangebeard.client.entity.StartV3TestRun;
import io.orangebeard.client.entity.suite.StartSuite;
import io.orangebeard.client.v3.OrangebeardAsyncV3Client;

import org.junit.runner.RunWith;
import org.junit.Test;
import org.junit.runner.Description;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class OrangebeardListenerTest {
    @Mock
    private OrangebeardAsyncV3Client orangebeardClient;

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
        ArgumentCaptor<StartV3TestRun> captor = ArgumentCaptor.forClass(StartV3TestRun.class);

        orangebeardListener.testRunStarted(description);

        verify(orangebeardClient).startTestRun(captor.capture());

        assertThat(captor.getValue().getTestSetName()).isEqualTo("TestSetName");
        assertThat(captor.getValue().getDescription()).isEqualTo("Description");
        assertThat(captor.getValue().getAttributes()).isEmpty();
    }

    @Test
    public void when_a_suite_is_started_on_the_listener_then_an_item_is_started_on_the_client() {
        Description runDescription = Description.createSuiteDescription("Run Description");
        UUID testRunUUID = UUID.fromString("49e7186d-e14d-4eeb-bc29-e36279d3b628");
        when(orangebeardClient.startTestRun(any())).thenReturn(testRunUUID);
        when(orangebeardClient.startSuite(any())).thenReturn(List.of(UUID.randomUUID()));

        Description suiteDescription = Description.createSuiteDescription("Suite Description");
        ArgumentCaptor<StartSuite> captor = ArgumentCaptor.forClass(StartSuite.class);

        orangebeardListener.testRunStarted(runDescription);
        orangebeardListener.testSuiteStarted(suiteDescription);

        verify(orangebeardClient).startSuite(captor.capture());
        assertThat(captor.getValue().getTestRunUUID()).isEqualTo(testRunUUID);
        assertThat(captor.getValue().getSuiteNames().get(0)).isEqualTo("Suite Description");
    }

    @Test
    public void when_a_suite_is_started_and_not_finished_it_will_not_be_started_again() {
        // When the Suite is in the "suites" map, an attempt to start it again will skip the "start" command.
        when(orangebeardClient.startSuite(any())).thenReturn(List.of(UUID.randomUUID()));
        Description suiteDescription = Description.createSuiteDescription("Suite");

        orangebeardListener.testSuiteStarted(suiteDescription);
        orangebeardListener.testSuiteStarted(suiteDescription);

        verify(orangebeardClient, times(1)).startSuite(any());
    }
}
