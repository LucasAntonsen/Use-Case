package com.function;

import com.microsoft.azure.functions.*;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.*;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.apache.commons.io.FileUtils;
import java.io.File;

/**
 * Unit test for Function class.
 * 
 * To run tests successfully locally, add the access token for Salesforce to:
 * 
 * final Optional<String> queryBody = Optional.of("accessToken");
 * 
 * and add the connection string for the Azure File Share in Function.java to:
 * 
 * String connectStr = "connectionString";
 * 
 * Otherwise, comment the test out, as it will stop the build process
 */
public class FunctionTest {
    /**
     * Unit test for HttpTriggerJava method.
     */
    @Test
    public void testHttpTriggerJava() throws Exception {
        // Setup
        @SuppressWarnings("unchecked")
        final HttpRequestMessage<Optional<String>> req = mock(HttpRequestMessage.class);

        // Set query body to Salesforce access token. Access token is left out for security reasons 
        final Optional<String> queryBody = Optional.of("accessToken");
        doReturn(queryBody).when(req).getBody();

        doAnswer(new Answer<HttpResponseMessage.Builder>() {
            @Override
            public HttpResponseMessage.Builder answer(InvocationOnMock invocation) {
                HttpStatus status = (HttpStatus) invocation.getArguments()[0];
                return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
            }
        }).when(req).createResponseBuilder(any(HttpStatus.class));

        final ExecutionContext context = mock(ExecutionContext.class);
        doReturn(Logger.getGlobal()).when(context).getLogger();

        // Invoke
        final HttpResponseMessage ret = new Function().run(req, context);

        // Get desired file output
        File testContacts = new File("testContacts.csv");

        // Verify
        assertEquals(ret.getStatus(), HttpStatus.OK);

        File contacts = new File("contacts.csv");
        assertTrue(FileUtils.contentEquals(testContacts, contacts));
    }
}
