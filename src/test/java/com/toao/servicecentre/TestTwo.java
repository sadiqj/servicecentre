package com.toao.servicecentre;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.fail;

import java.util.Map;
import java.util.Map.Entry;

import org.junit.Test;
import org.mockito.Mockito;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.toao.servicecentre.ServiceCentre.ServicesFailedException;
import com.toao.servicecentre.testone.ServiceOne;
import com.toao.servicecentre.testone.ServiceThree;
import com.toao.servicecentre.testone.ServiceTwo;

public class TestTwo {
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
		Mockito.when(testServiceThree.start()).thenReturn(new ExceptionThrowingFuture());
		Mockito.when(testServiceOne.stop()).thenReturn(dummyFuture);
		Mockito.when(testServiceTwo.stop()).thenReturn(dummyFuture);
		Mockito.when(testServiceThree.stop()).thenReturn(dummyFuture);

		Injector injector = Guice.createInjector(testOneModule);

		ServiceCentre serviceCentre = injector.getInstance(ServiceCentre.class);

		try {
			serviceCentre.onlyIncludePackages("com.toao.servicecentre.testone");
			
			serviceCentre.startAndWait();
			// If we don't get an exception, we've failed as one of our
			// mocked services throws an exception on startup 
			fail();  
		} catch (UncheckedExecutionException e) {
			ServicesFailedException cause = (ServicesFailedException)e.getCause();
			Map<Service, Throwable> failedServices = cause.getFailedServices();
			
			assertEquals("number of failed services is 1", 1, failedServices.size());
			
			for( Entry<Service, Throwable> entry : failedServices.entrySet() )
			{
				Service service = entry.getKey();
				
				assertEquals("Failed service is ServiceThree", true, service.equals(testServiceThree));
			}
		}
	}
}
