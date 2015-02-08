package com.toao.servicecentre;

import com.google.common.util.concurrent.Service;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import com.toao.servicecentre.testone.ServiceOne;
import com.toao.servicecentre.testone.ServiceThree;
import com.toao.servicecentre.testone.ServiceTwo;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.Map;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.fail;
import static org.mockito.Mockito.*;

public class StartupShutdownTests {
    final ServiceOne testServiceOne = mock(ServiceOne.class);
    final ServiceTwo testServiceTwo = mock(ServiceTwo.class);
    final ServiceThree testServiceThree = mock(ServiceThree.class);
    Module testModule;

    @Before
    public void setupServices()
    {
        when(testServiceOne.startAsync()).thenReturn(testServiceOne);
        when(testServiceTwo.startAsync()).thenReturn(testServiceTwo);
        when(testServiceThree.startAsync()).thenReturn(testServiceThree);
        when(testServiceOne.startAsync()).thenReturn(testServiceOne);
        when(testServiceTwo.startAsync()).thenReturn(testServiceTwo);
        when(testServiceThree.startAsync()).thenReturn(testServiceThree);

        when(testServiceOne.stopAsync()).thenReturn(testServiceOne);
        when(testServiceTwo.stopAsync()).thenReturn(testServiceTwo);
        when(testServiceThree.stopAsync()).thenReturn(testServiceThree);
        when(testServiceOne.stopAsync()).thenReturn(testServiceOne);
        when(testServiceTwo.stopAsync()).thenReturn(testServiceTwo);
        when(testServiceThree.stopAsync()).thenReturn(testServiceThree);

        testModule = getModule(testServiceOne, testServiceTwo, testServiceThree);
    }

    private Module getModule(ServiceOne testServiceOne, ServiceTwo testServiceTwo,
        ServiceThree testServiceThree) {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(ServiceOne.class).toInstance(testServiceOne);
                bind(ServiceTwo.class).toInstance(testServiceTwo);
                bind(ServiceThree.class).toInstance(testServiceThree);

                Multibinder<Service> activeServices = Multibinder
                    .newSetBinder(binder(), Service.class, Names.named("activeServices"));

                activeServices.addBinding().toInstance(testServiceOne);
                activeServices.addBinding().toInstance(testServiceTwo);
                activeServices.addBinding().toInstance(testServiceThree);
            }
        };
    }

    @Test
    public void testAllOk() {
        Injector injector = Guice.createInjector(testModule);

        ServiceCentre serviceCentre = injector.getInstance(ServiceCentre.class);

        serviceCentre.startAsync().awaitRunning();

        serviceCentre.shutDown();

        InOrder inOrder = inOrder(testServiceOne, testServiceTwo, testServiceThree);

        inOrder.verify(testServiceOne).startAsync();
        inOrder.verify(testServiceTwo).startAsync();
        inOrder.verify(testServiceThree).startAsync();
        inOrder.verify(testServiceThree).stopAsync();
        inOrder.verify(testServiceTwo).stopAsync();
        inOrder.verify(testServiceOne).stopAsync();
    }

    @Test
    public void testFailingStart() {
        doThrow(new IllegalStateException()).when(testServiceThree).awaitRunning();

        Injector injector = Guice.createInjector(testModule);

        ServiceCentre serviceCentre = injector.getInstance(ServiceCentre.class);

        try {
            serviceCentre.startAsync().awaitRunning();
            // If we don't get an exception, we've failed as one of our
            // mocked services throws an exception on startup
            fail();
        } catch (IllegalStateException e) {
            ServiceCentre.ServicesFailedException cause =
                (ServiceCentre.ServicesFailedException) e.getCause();
            Map<Service, Throwable> failedServices = cause.getFailedServices();

            assertEquals("number of failed services is 1", 1, failedServices.size());

            for (Map.Entry<Service, Throwable> entry : failedServices.entrySet()) {
                Service service = entry.getKey();

                assertEquals("Failed service is ServiceThree", true,
                    service.equals(testServiceThree));
            }
        }
    }

    @Test
    public void testShutdownException() {
        doThrow(new IllegalStateException()).when(testServiceThree).awaitTerminated();

        Injector injector = Guice.createInjector(testModule);

        ServiceCentre serviceCentre = injector.getInstance(ServiceCentre.class);

        serviceCentre.startAsync().awaitRunning();

        try {
            serviceCentre.stopAsync();
            serviceCentre.awaitTerminated();

            // If we don't get an exception, we've failed as one of our
            // mocked services throws an exception on shutdown
            fail();
        } catch (IllegalStateException e) {
            ServiceCentre.ServicesFailedException cause =
                (ServiceCentre.ServicesFailedException) e.getCause();
            Map<Service, Throwable> failedServices = cause.getFailedServices();

            assertEquals("number of failed services is 1", 1, failedServices.size());

            for (Map.Entry<Service, Throwable> entry : failedServices.entrySet()) {
                Service service = entry.getKey();

                assertEquals("Failed service is ServiceThree", true,
                    service.equals(testServiceThree));
            }
        }
    }
}
