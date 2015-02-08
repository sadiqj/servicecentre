package com.toao.servicecentre;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import com.toao.servicecentre.ServiceCentre.ServicesFailedException;
import com.toao.servicecentre.testone.ServiceOne;
import com.toao.servicecentre.testone.ServiceThree;
import com.toao.servicecentre.testone.ServiceTwo;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Map.Entry;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

public class TestThree {
    @Test
    public void test() {
        final ServiceOne testServiceOne = Mockito.mock(ServiceOne.class);
        final ServiceTwo testServiceTwo = Mockito.mock(ServiceTwo.class);
        final ServiceThree testServiceThree = Mockito.mock(ServiceThree.class);

        Module testOneModule = new AbstractModule() {
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

        DummyFuture dummyFuture = new DummyFuture();

        when(testServiceOne.startAsync()).thenReturn(testServiceOne);
        when(testServiceTwo.startAsync()).thenReturn(testServiceTwo);
        when(testServiceThree.startAsync()).thenReturn(testServiceThree);
        when(testServiceOne.startAsync()).thenReturn(testServiceOne);
        when(testServiceTwo.startAsync()).thenReturn(testServiceTwo);
        when(testServiceThree.startAsync()).thenReturn(testServiceThree);

        doThrow(new IllegalStateException()).when(testServiceThree).awaitTerminated();

        Injector injector = Guice.createInjector(testOneModule);

        ServiceCentre serviceCentre = injector.getInstance(ServiceCentre.class);

        serviceCentre.startAsync().awaitRunning();

        try {
            serviceCentre.stopAsync();
            serviceCentre.awaitTerminated();

            // If we don't get an exception, we've failed as one of our
            // mocked services throws an exception on shutdown
            fail();
        } catch (IllegalStateException e) {
            ServicesFailedException cause = (ServicesFailedException) e.getCause();
            Map<Service, Throwable> failedServices = cause.getFailedServices();

            assertEquals("number of failed services is 1", 1, failedServices.size());

            for (Entry<Service, Throwable> entry : failedServices.entrySet()) {
                Service service = entry.getKey();

                assertEquals("Failed service is ServiceThree", true,
                    service.equals(testServiceThree));
            }
        }
    }
}
