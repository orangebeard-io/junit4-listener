package io.orangebeard.listener;

import io.orangebeard.client.OrangebeardV1Client;

import com.nordstrom.automation.junit.AtomicTest;
import org.mockito.Mockito;
import org.junit.Test;
import org.junit.runner.Description;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class OrangebeardListenerTest {

    private final OrangebeardV1Client client = Mockito.mock(OrangebeardV1Client.class);
    private final AtomicTest test = Mockito.mock(AtomicTest.class);
    private final OrangebeardListener listener = new OrangebeardListener(client);

    @Test
    public void one_test_results_in_suite_and_test() throws Exception {
        when(test.getDescription()).thenReturn(Description.createTestDescription(getClass(), getClass().getName()));

        listener.testStarted(test);

        verify(client, times(2)).startTestItem(any(), any());
    }

    @Test
    public void finish_testrun_force_finishes_unfinished_tests_and_suites() throws Exception {
        when(test.getDescription()).thenReturn(Description.createTestDescription(getClass(), getClass().getName()));

        listener.testStarted(test);
        listener.runFinished(null);

        verify(client, times(2)).finishTestItem(any(), any());
        verify(client, times(1)).finishTestRun(any(), any());
    }
}
