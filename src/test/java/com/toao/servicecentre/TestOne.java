package com.toao.servicecentre;

import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.toao.servicecentre.testone.ServiceOne;
import com.toao.servicecentre.testone.ServiceThree;
import com.toao.servicecentre.testone.ServiceTwo;

public class TestOne {
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
			}
		};

		DummyFuture dummyFuture = new DummyFuture();
		
		Mockito.when(testServiceOne.start()).thenReturn(dummyFuture);
		Mockito.when(testServiceTwo.start()).thenReturn(dummyFuture);
		Mockito.when(testServiceThree.start()).thenReturn(dummyFuture);
		Mockito.when(testServiceOne.stop()).thenReturn(dummyFuture);
		Mockito.when(testServiceTwo.stop()).thenReturn(dummyFuture);
		Mockito.when(testServiceThree.stop()).thenReturn(dummyFuture);

		Injector injector = Guice.createInjector(testOneModule);

		ServiceCentre serviceCentre = injector.getInstance(ServiceCentre.class);

		serviceCentre.onlyIncludePackages("com.toao.servicecentre.testone");
		
		serviceCentre.startAndWait();
		
		serviceCentre.shutDown();

		InOrder inOrder = Mockito.inOrder(testServiceOne, testServiceTwo, testServiceThree);

		inOrder.verify(testServiceOne).start();
		inOrder.verify(testServiceTwo).start();
		inOrder.verify(testServiceThree).start();
		inOrder.verify(testServiceThree).stop();
		inOrder.verify(testServiceTwo).stop();
		inOrder.verify(testServiceOne).stop();
	}
}