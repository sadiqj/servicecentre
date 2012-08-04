package com.toao.servicecentre;

import static com.google.common.collect.Iterables.transform;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.Service.State;
import com.google.inject.ConfigurationException;
import com.google.inject.Injector;
import com.google.inject.ProvisionException;
import com.toao.servicecentre.annotations.ManagedService;

@Singleton
public class ServiceCentre {
	private static Logger sLogger = LoggerFactory.getLogger(ServiceCentre.class);
	private final Injector injector;
	private final Multimap<Integer, Service> services = ArrayListMultimap.create();

	@Inject
	public ServiceCentre(Injector injector) {
		this.injector = injector;
	}

	public void start(String... scanPackages) {
		// Build our configuration for Reflections. We're only going to look at the
		// classes in the provided scanPackages strings
		ConfigurationBuilder builder = new ConfigurationBuilder();
		FilterBuilder filterBuilder = new FilterBuilder();

		for (String scanPackage : scanPackages) {
			filterBuilder.include(FilterBuilder.prefix(scanPackage));
		}

		builder.filterInputsBy(filterBuilder);
		Reflections reflections = new Reflections(builder);

		// Now find classes that do both
		Set<Class<?>> managedServices = reflections.getTypesAnnotatedWith(ManagedService.class);
		
		// Check that each managed service implements Service and has a Singleton annotation
		for( Class<?> managedService : managedServices )
		{
			boolean hasJavaxSingleton = (managedService.getAnnotation(javax.inject.Singleton.class) != null);
			boolean hasGuiceSingleton = (managedService.getAnnotation(com.google.inject.Singleton.class) != null);
			
			if( !(hasJavaxSingleton || hasGuiceSingleton) )
			{
				throw new ServiceCentreInitialisationException("ManagedService " + managedService + " is not a Singleton.", null);
			}
			
			boolean hasService = Arrays.asList(managedService.getInterfaces()).contains(Service.class);
			
			if( !hasService )
			{
				throw new ServiceCentreInitialisationException("ManagedService " + managedService + " does not implement the Guava Service interface", null);
			}
		}

		// Add all the classes to their appropriate level
		for (Class<?> interfaceKlass : managedServices) {
			// Figure out which level, from the annotation
			ManagedService managedServiceAnnotation = interfaceKlass.getAnnotation(ManagedService.class);

			int level = managedServiceAnnotation.level();
			// We have the level, now we need to get an instance of the class that actually
			// is bound to this interface in Guice.
			try {
				Service serviceKlass = (Service) injector.getInstance(interfaceKlass);

				services.put(level, serviceKlass);
			} catch (ConfigurationException e) {
				String msg = "Unable to find binding for service " + interfaceKlass + " in Guice: " + e.getMessage();
				sLogger.error(msg, e);
				throw new ServiceCentreInitialisationException(msg, e);
			} catch (ProvisionException e) {
				final String msg = "Guice unable to provide instance of service: " + interfaceKlass + ". " + e.getMessage();
				sLogger.error(msg, e);
				throw new ServiceCentreInitialisationException(msg, e);
			}
		}
		
		// Now we should have all of the classes implementing the service interfaces
		// in our multimap. Now we need to get a sorted list of all the levels and
		// then start each level.
		
		List<Integer> levels = Lists.newArrayList(services.keySet());
		
		Collections.sort(levels);
		
		for( int level : levels )
		{
			Collection<Service> levelServices = services.get(level);

			String names = getNiceNames(levelServices);
			
			sLogger.info("Starting level {} with services: {}", level, names);
			
			Map<Service,Throwable> failedServices = Collections.synchronizedMap(new HashMap<Service,Throwable>());
			
			Map<Service,ListenableFuture<State>> futures = Maps.newHashMap(); 
			
			// Start each service and add the ListenableFuture to the set of
			// futures to wait for
			for( final Service levelService : levelServices )
			{
				 ListenableFuture<State> startedFuture = levelService.start();
				 
				 futures.put(levelService, startedFuture);
			}
			
			// Now wait for all my lovelies to complete
			for( Entry<Service, ListenableFuture<State>> entry : futures.entrySet() )
			{
				try {
					entry.getValue().get();
				} catch (Exception e) {
					failedServices.put(entry.getKey(), e);
				}
			}
			
			// Check to see if there were any failed services
			if( failedServices.size() > 0 )
			{
				sLogger.error("Services {} failed startup at level {}", getNiceNames(failedServices.keySet()), level);
				throw new ServicesFailedException(failedServices);
			}
			
		}
		
		sLogger.info("All services stopped successfully");
	}
	
	public void shutdown()
	{
		// Need to go through all our levels backwards
		List<Integer> levels = Lists.newArrayList(services.keySet());
		
		Collections.sort(levels);
		
		Lists.reverse(levels);
		
		// Here the failed services is only thrown at the end, since we still want
		// to shutdown all of our services, even if some other ones throw an exception
		// earlier.
		Map<Service,Throwable> failedServices = Collections.synchronizedMap(new HashMap<Service,Throwable>());
		
		int previousLevelFailures = 0;
		
		for( int level : levels )
		{
			Collection<Service> levelServices = services.get(level);

			String names = getNiceNames(levelServices);
			
			sLogger.info("Shutting down level {} with services: {}", level, names);
						
			Map<Service,ListenableFuture<State>> futures = Maps.newHashMap(); 
			
			// Start each service and add the ListenableFuture to the set of
			// futures to wait for
			for( final Service levelService : levelServices )
			{
				 ListenableFuture<State> stoppedFuture = levelService.stop();
				 
				 futures.put(levelService, stoppedFuture);
			}
			
			// Now wait for all my lovelies to complete
			for( Entry<Service, ListenableFuture<State>> entry : futures.entrySet() )
			{
				try {
					entry.getValue().get();
				} catch (Exception e) {
					failedServices.put(entry.getKey(), e);
				}
			}
			
			// Check to see if there were any failed services
			if( failedServices.size() > previousLevelFailures )
			{
				sLogger.error("Services {} failed startup at level {}", getNiceNames(failedServices.keySet()), level);
				previousLevelFailures = failedServices.size();
			}
		}
		
		if( failedServices.size() > 0 )
		{
			throw new ServicesFailedException(failedServices);
		}
		
		sLogger.info("All services shut down successfully.");
	}

	private static String getNiceNames(Collection<Service> levelServices) {
		return Joiner.on(",").join(transform(levelServices, new Function<Service, String>() {
			@Nullable
			public String apply(@Nullable Service service) {
				return service.getClass().getSimpleName();
			}
		}));
	}

	@SuppressWarnings("serial")
	public static class ServicesFailedException extends RuntimeException {
		private final Map<Service,Throwable> failedServices;

		public ServicesFailedException(Map<Service, Throwable> failedServices) {
			super(getNiceNames(failedServices.keySet()));
			this.failedServices = failedServices;
		}

		@Override
		public Throwable getCause() {
			// Throw the first err we have. Not correct though.
			for( Throwable err : failedServices.values() )
			{
				return err;
			}
			
			// No errs, return null (this should never happen)
			return null;
		}
	}
	
	@SuppressWarnings("serial")
	public static class ServiceCentreInitialisationException extends RuntimeException {
		public ServiceCentreInitialisationException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
