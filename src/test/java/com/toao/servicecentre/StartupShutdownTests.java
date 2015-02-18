package com.toao.servicecentre;

import com.google.common.util.concurrent.Service;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import com.toao.servicecentre.testone.*;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.fail;

public class StartupShutdownTests {
    Module testModule;

    @Before
    public void setupServices()
    {
        testModule = getModule();
    }

    private Module getModule() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(ServiceOne.class).to(AbstractServiceOne.class);
                bind(ServiceTwo.class).to(AbstractServiceTwo.class);
                bind(ServiceThree.class).to(AbstractServiceThree.class);

                Multibinder<Service> activeServices = Multibinder
                    .newSetBinder(binder(), Service.class, Names.named("activeServices"));

                activeServices.addBinding().to(AbstractServiceOne.class);
                activeServices.addBinding().to(AbstractServiceTwo.class);
                activeServices.addBinding().to(AbstractServiceThree.class);
            }
        };
    }

    @Test
    public void testAllOk() {
        Injector injector = Guice.createInjector(testModule);

        ServiceCentre serviceCentre = injector.getInstance(ServiceCentre.class);

        serviceCentre.startAsync().awaitRunning();

        serviceCentre.stopAsync().awaitTerminated();
    }

    @Test
    public void testFailingStart() {
        Injector injector = Guice.createInjector(testModule);

        ServiceThree serviceThree = injector.getInstance(AbstractServiceThree.class);
        serviceThree.setErrorOnStart();

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
                    service.equals(serviceThree));
            }
        }
    }

    @Test
    public void testShutdownException() {
        Injector injector = Guice.createInjector(testModule);

        ServiceThree serviceThree = injector.getInstance(AbstractServiceThree.class);
        serviceThree.setErrorOnShutdown();

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
                    service.equals(serviceThree));
            }
        }
    }
}
