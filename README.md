# ServiceCentre

ServiceCentre is a small library for doing basic lifecycle management for Guava Services using Guice.

## How it works

Each interface extending `Service` or class implementing `Service` can be given a `@ManagedService` annotation with an optional numerical `level` parameter. On startup, all services at the lowest level are started in parallel. Once all have started sucessfully, the next level is started. If one or more services in a level fail to start, no further levels are started and an exception is thrown giving the services that failed to start and their exceptions.

When shutting down, ServiceCentre starts at the greatest level and shuts down all services in the level. ServiceCentre will proceed shutting down subsequent levels even if some services fail in shutting down. At the end of shutdown, if any services did fail to terminate correctly an exception is thrown.

The set of active services that ServiceCentre needs to manage is specified through a Guice Multibinding of `Set<Service>` annotated with `activeServices`. See below for an example.

## Some examples

### Starting and stopping
    // Acquire an instance of ServiceCentre via injection , whether through constructor, field, method
    ServiceCentre serviceCentre = ...
    
    // Optional filtering, otherwise will use any found on classpath
    serviceCentre.onlyIncludePackages("package.containing.your.services"); 
    
    // ServiceCentre actually implements Guava's Service, so you can use .start() or .startAndWait()
    serviceCentre.startAsync().awaitRunning();
    
    // Do stuff
    
    // Again, just one of Guava's Service methods. You could use .stop() too and listen on the future
    serviceCentre.stopAsync().awaitTerminated();
    
### Finding service failures

    try
    {
        serviceCentre.startAsync().awaitRunning();
    }
    catch (UncheckedExecutionException e) {
      ServicesFailedException cause = (ServicesFailedException)e.getCause();
      Map<Service, Throwable> failedServices = cause.getFailedServices();
      // You can tell which services failed and why here
    }
    
### Guice binding

Note that in the follow example, only ServiceOneImpl, ServiceTwoImpl and ServiceThreeImpl are managed by ServiceCentre as they are the only ones specified in the multibinding.

        new AbstractModule() {
            @Override
            protected void configure() {
                bind(ServiceOne.class).to(ServiceOneImpl.class);
                bind(ServiceTwo.class).to(ServiceTwoImpl.class);
                bind(ServiceThree.class).to(ServiceThreeImpl.class);
                bind(ServiceFour.class).to(ServiceFourImpl.class);

                Multibinder<Service> activeServices = Multibinder.newSetBinder(binder(), Service.class, Names.named("activeServices"));
                activeServices.addBinding().to(AbstractServiceOne.class);
                activeServices.addBinding().to(AbstractServiceTwo.class);
                activeServices.addBinding().to(AbstractServiceThree.class);
            }
        };
