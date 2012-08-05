# ServiceCentre

ServiceCentre is a small library for doing basic lifecycle management for Guava Services using Guice.

## How it works

Each interface extending `Service` or class implementing `Service` can be given a `@ManagedService` annotation with an optional numerical `level` parameter. On startup, all services at the lowest level are started in parallel. Once all have started sucessfully, the next level is started. If one or more services in a level fail to start, no further levels are started and an exception is thrown giving the services that failed to start and their exceptions.

When shutting down, ServiceCentre starts at the greatest level and shuts down all services in the level. ServiceCentre will proceed shutting down subsequent levels even if some services fail in shutting down. At the end of shutdown, if any services did fail to terminate correctly an exception is thrown.

## Some examples

### Starting and stopping
    // Acquire an instance of ServiceCentre via injection , whether through constructor, field, method
    ServiceCentre serviceCentre = ...
    
    // Optional filtering, otherwise will use any found on classpath
    serviceCentre.onlyIncludePackages("package.containing.your.services"); 
    
    // ServiceCentre actually implements Guava's Service, so you can use .start() or .startAndWait()
    serviceCentre.startAndWait(); 
    
    // Do stuff
    
    // Again, just one of Guava's Service methods. You could use .stop() too and listen on the future
    serviceCentre.stopAndWait(); 
    
### Finding service failures

    try
    {
      serviceCentre.startAndWait()
    }
    catch (UncheckedExecutionException e) {
      ServicesFailedException cause = (ServicesFailedException)e.getCause();
      Map<Service, Throwable> failedServices = cause.getFailedServices();
      // You can tell which services failed and why here
    }
    
    