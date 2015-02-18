package com.toao.servicecentre;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.Service;
import com.google.inject.*;
import com.google.inject.name.Names;
import com.toao.servicecentre.annotations.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Predicate;

import static com.google.common.collect.Iterables.transform;
import static java.util.stream.Collectors.toSet;

@Singleton
public class ServiceCentre extends AbstractIdleService {
    private static Logger sLogger = LoggerFactory.getLogger(ServiceCentre.class);
    private final Injector injector;
    private final Multimap<Integer, Service> services = ArrayListMultimap.create();

    @Inject
    public ServiceCentre(Injector injector) {
        this.injector = injector;
    }

    protected void startUp() {
        long start = System.currentTimeMillis();

        TypeLiteral<Set<Service>> serviceTypeLiteral = new TypeLiteral<Set<Service>>() {
        };
        Key<Set<Service>> activeServicesKey = Key.get(serviceTypeLiteral, Names.named("activeServices"));

        Set<Service> activeServices = injector.getInstance(activeServicesKey);

        Set<Class<?>> boundClasses = activeServices.stream().map(Object::getClass).collect(toSet());

        sLogger.debug("Found {} services in active services set", activeServices.size());

        // Check that each managed service implements Service
        for (Class<?> boundService : boundClasses) {
            boolean hasService = checkHierarchy(boundService, klass -> Service.class.isAssignableFrom(klass));
            boolean hasManagedService = checkHierarchy(boundService, klass -> klass.isAnnotationPresent(ManagedService.class));

            sLogger.debug("ManageService annotated type {} implements Service interface = {}", boundService.getSimpleName(), hasService);

            if (!hasService) {
                throw new ServiceCentreInitialisationException("Service " + boundService + " does not implement the Guava Service interface", null);
            } else if (!hasManagedService) {
                throw new ServiceCentreInitialisationException("Service " + boundService + " does not contain a ManagedService annotation", null);
            }
        }

        // Add all the classes to their appropriate level
        for (Service activeService : activeServices) {
            // Figure out which level, from the annotation
            Optional<ManagedService> managedServiceAnnotationOptional = getAnnotationFromHierarchy(activeService.getClass(), ManagedService.class);

            if (managedServiceAnnotationOptional.isPresent()) {
                int level = managedServiceAnnotationOptional.get().level();
                // We have the level, now we need to get an instance of the class that actually
                // is bound to this interface in Guice.
                try {
                    if (sLogger.isDebugEnabled()) {
                        Class<? extends Service> serviceKlass = activeService.getClass();

                        boolean hasSingletonAnnotation = checkHierarchy(serviceKlass, klass -> klass.getAnnotation(javax.inject.Singleton.class) != null || klass.getAnnotation(com.google.inject.Singleton.class) != null);

                        if (!(hasSingletonAnnotation)) {
                            sLogger.debug("Instance of ManagedService " + activeService + " (" + serviceKlass + ") is not a Singleton.");
                        }
                    }

                    services.put(level, activeService);
                } catch (ConfigurationException e) {
                    String msg = "Unable to find binding for service " + activeService + " in Guice: " + e.getMessage();
                    sLogger.error(msg, e);
                    throw new ServiceCentreInitialisationException(msg, e);
                } catch (ProvisionException e) {
                    final String msg = "Guice unable to provide instance of service: " + activeService + ". " + e.getMessage();
                    sLogger.error(msg, e);
                    throw new ServiceCentreInitialisationException(msg, e);
                }
            } else {
                throw new ServiceCentreInitialisationException("Could not find managed service annotation on class " + activeService.getClass() + " or its parents", null);
            }
        }

        // Now we should have all of the classes implementing the service interfaces
        // in our multimap. Now we need to get a sorted list of all the levels and
        // then start each level.

        List<Integer> levels = Lists.newArrayList(services.keySet());

        Collections.sort(levels);

        for (int level : levels) {
            Collection<Service> levelServices = services.get(level);

            String names = getNiceNames(levelServices);

            sLogger.info("Starting level {} with services: {}", level, names);

            Map<Service, Throwable> failedServices = Collections.synchronizedMap(new HashMap<Service, Throwable>());

            Set<Service> services = Sets.newHashSet();

            // Start each service and add the ListenableFuture to the set of
            // futures to wait for
            for (final Service levelService : levelServices) {
                sLogger.debug("Starting service {} at level {}", levelService.getClass().getSimpleName(), level);
                try {
                    levelService.startAsync();
                } catch (Exception e) {
                    // We may get an error as soon as we call start(), need to deal with it
                    failedServices.put(levelService, e);
                }
            }

            // Now wait for all my lovelies to complete
            for (Service service : levelServices) {
                try {
                    sLogger.debug("startUp - awaiting startup of Service: {}", service.getClass().getSimpleName());
                    service.awaitRunning();
                } catch (Exception e) {
                    failedServices.put(service, e);
                }
            }

            // Check to see if there were any failed services
            if (failedServices.size() > 0) {
                sLogger.error("Services {} failed startup at level {}", getNiceNames(failedServices.keySet()), level);
                for (Entry<Service, Throwable> entry : failedServices.entrySet()) {
                    sLogger.error("Service " + entry.getKey() + " failed with exception: " + entry.getValue(), entry.getValue());
                }
                throw new ServicesFailedException(failedServices);
            }

        }

        sLogger.info("All services started successfully in {}ms", (System.currentTimeMillis() - start));
    }


    protected void shutDown() {
        long start = System.currentTimeMillis();
        // Need to go through all our levels backwards
        List<Integer> levels = Lists.newArrayList(services.keySet());

        Collections.sort(levels);

        levels = Lists.reverse(levels);

        // Here the failed services is only thrown at the end, since we still want
        // to shutdown all of our services, even if some other ones throw an exception
        // earlier.
        Map<Service, Throwable> failedServices = Collections.synchronizedMap(new HashMap<Service, Throwable>());

        int previousLevelFailures = 0;

        for (int level : levels) {
            Collection<Service> levelServices = services.get(level);

            String names = getNiceNames(levelServices);

            sLogger.info("Shutting down level {} with services: {}", level, names);

            // Start each service and add the ListenableFuture to the set of
            // futures to wait for
            for (final Service levelService : levelServices) {
                levelService.stopAsync();
            }

            // Now wait for all my lovelies to stop
            for (Service service : levelServices) {
                try {
                    sLogger.debug("Checking service {} has shut down..", service.getClass().getSimpleName());
                    service.awaitTerminated();
                } catch (Exception e) {
                    sLogger.info("Got exception: {} trying to shut down service {}", e.getCause(), service.getClass().getSimpleName());
                    failedServices.put(service, e);
                }
            }

            // Check to see if there were any failed services
            if (failedServices.size() > previousLevelFailures) {
                sLogger.error("Services {} failed shutdown at level {}", getNiceNames(failedServices.keySet()), level);
                previousLevelFailures = failedServices.size();
            }
        }

        if (failedServices.size() > 0) {
            throw new ServicesFailedException(failedServices);
        }

        sLogger.info("All services shut down successfully in {}ms", (System.currentTimeMillis() - start));
    }

    private static String getNiceNames(Collection<Service> levelServices) {
        return Joiner.on(",").join(transform(levelServices, new Function<Service, String>() {
            public String apply(Service service) {
                return service.getClass().getSimpleName();
            }
        }));
    }

    private <T extends Annotation> Optional<T> getAnnotationFromHierarchy(Class klass, Class<T> annotation) {
        do {
            sLogger.debug("getAnnotationFromHierachy - looking for annotation {} in {}", annotation, klass);

            if (klass.isAnnotationPresent(annotation)) {
                return Optional.of(annotation.cast(klass.getAnnotation(annotation)));
            }

            klass = klass.getSuperclass();
        }
        while (klass != null);

        return Optional.empty();
    }

    private boolean checkHierarchy(Class klass, Predicate<Class> check) {
        do {
            sLogger.debug("checkHierarchy - testing class {}", klass);
            boolean result = check.test(klass);
            sLogger.debug("checkHierarchy - result: {}", result);

            if (result) {
                return true;
            }

            klass = klass.getSuperclass();
        }
        while (klass != null);

        return false;
    }

    @SuppressWarnings("serial")
    public static class ServicesFailedException extends RuntimeException {
        private final Map<Service, Throwable> failedServices;

        public ServicesFailedException(Map<Service, Throwable> failedServices) {
            super(getNiceNames(failedServices.keySet()));
            this.failedServices = failedServices;
        }

        @Override
        public Throwable getCause() {
            // Throw the first err we have. Not correct though.
            for (Throwable err : getFailedServices().values()) {
                return err;
            }

            // No errs, return null (this should never happen)
            return null;
        }

        public Map<Service, Throwable> getFailedServices() {
            return failedServices;
        }
    }

    @SuppressWarnings("serial")
    public static class ServiceCentreInitialisationException extends RuntimeException {
        public ServiceCentreInitialisationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
