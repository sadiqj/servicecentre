package com.toao.servicecentre;

import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.toao.servicecentre.testone.ServiceOne;
import com.toao.servicecentre.testone.ServiceTwo;

public class TestOne {
	@Test
	public void test()
	{
		final ServiceOne testServiceOne = Mockito.mock(ServiceOne.class);
		final ServiceTwo testServiceTwo = Mockito.mock(ServiceTwo.class);
		
		Module testOneModule = new AbstractModule(){
			@Override
			protected void configure() {
				bind(ServiceOne.class).toInstance(testServiceOne);
				bind(ServiceTwo.class).toInstance(testServiceTwo);
			}
		};
		
		Injector injector = Guice.createInjector(testOneModule);
		
		ServiceCentre serviceCentre = injector.getInstance(ServiceCentre.class);
		
		serviceCentre.start("com.toao.servicecentre.testone");
		
		serviceCentre.shutdown();
		
		InOrder inOrder = Mockito.inOrder(testServiceOne, testServiceTwo);
		
		inOrder.verify(testServiceOne).start();
		inOrder.verify(testServiceTwo).start();
		inOrder.verify(testServiceTwo).stop();
		inOrder.verify(testServiceOne).stop();
	}
}
