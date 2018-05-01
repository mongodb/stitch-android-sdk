package com.mongodb.stitch.core.services.internal;

import com.mongodb.stitch.core.auth.internal.StitchAuthRequestClient;
import com.mongodb.stitch.core.internal.net.Method;
import com.mongodb.stitch.core.internal.net.StitchAuthDocRequest;

import org.bson.Document;
import org.bson.codecs.Decoder;
import org.bson.codecs.IntegerCodec;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

public class CoreStitchServiceUnitTests {

    @Test
    public void testCallFunctionInternal() {
        final String serviceName = "svc1";
        final StitchServiceRoutes routes = new StitchServiceRoutes("foo");
        final StitchAuthRequestClient requestClient = Mockito.mock(StitchAuthRequestClient.class);
        final CoreStitchService coreStitchService = new CoreStitchService(
                requestClient,
                routes,
                serviceName
        );

        doReturn(42).when(requestClient)
                .doAuthenticatedJSONRequest(any(), ArgumentMatchers.<Decoder<Integer>>any());
        doReturn(42).when(requestClient)
                .doAuthenticatedJSONRequest(any(), ArgumentMatchers.<Class<Integer>>any());

        final String funcName = "myFunc1";
        final List<Integer> args = Arrays.asList(1, 2, 3);
        final Document expectedRequestDoc = new Document();
        expectedRequestDoc.put("name", funcName);
        expectedRequestDoc.put("service", serviceName);
        expectedRequestDoc.put("arguments", args);

        assertEquals(42, (int) coreStitchService.callFunctionInternal(funcName, args, new IntegerCodec()));
        assertEquals(42, (int) coreStitchService.callFunctionInternal(funcName, args, Integer.class));

        final ArgumentCaptor<StitchAuthDocRequest> docArgument = ArgumentCaptor.forClass(StitchAuthDocRequest.class);
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Decoder<Integer>> decArgument = ArgumentCaptor.forClass(Decoder.class);
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Class<Integer>> clazzArgument = ArgumentCaptor.forClass(Class.class);

        verify(requestClient).doAuthenticatedJSONRequest(docArgument.capture(), decArgument.capture());
        assertEquals(docArgument.getValue().method, Method.POST);
        assertEquals(docArgument.getValue().path, routes.getFunctionCallRoute());
        assertEquals(docArgument.getValue().document, expectedRequestDoc);
        assertTrue(decArgument.getValue() instanceof IntegerCodec);

        verify(requestClient).doAuthenticatedJSONRequest(docArgument.capture(), clazzArgument.capture());
        assertEquals(docArgument.getValue().method, Method.POST);
        assertEquals(docArgument.getValue().document, expectedRequestDoc);
        assertEquals(docArgument.getValue().path, routes.getFunctionCallRoute());
        assertEquals(clazzArgument.getValue(), Integer.class);
    }
}
