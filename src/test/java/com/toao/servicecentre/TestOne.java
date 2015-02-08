package com.toao.servicecentre;

import com.google.common.util.concurrent.Service;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.toao.servicecentre.testone.ServiceOne;
import com.toao.servicecentre.testone.ServiceThree;
import com.toao.servicecentre.testone.ServiceTwo;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import static org.mockito.Mockito.*;

public class TestOne {
    @Test
    public void test() {
        final ServiceOne testServiceOne = mock(ServiceOne.class);
        final ServiceTwo testServiceTwo = mock(ServiceTwo.class);
        final ServiceThree testServiceThree = mock(ServiceThree.class);

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

        when(testServiceOne.startAsync()).thenReturn(testServiceOne);
        when(testServiceTwo.startAsync()).thenReturn(testServiceTwo);
        when(testServiceThree.startAsync()).thenReturn(testServiceThree);
        when(testServiceOne.startAsync()).thenReturn(testServiceOne);
        when(testServiceTwo.startAsync()).thenReturn(testServiceTwo);
        when(testServiceThree.startAsync()).thenReturn(testServiceThree);


        Injector injector = Guice.createInjector(testOneModule);

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
}
