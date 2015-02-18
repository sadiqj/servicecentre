package com.toao.servicecentre.testone;

import com.google.common.util.concurrent.AbstractIdleService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.toao.servicecentre.annotations.ManagedService;

import static org.junit.Assert.assertEquals;

@Singleton
@ManagedService(level = 2)
public class AbstractServiceThree extends AbstractIdleService implements ServiceThree {
    private final ServiceTwo serviceTwo;
    private final ServiceOne serviceOne;
    private boolean errorOnStart;
    private boolean errorOnShutdown;

    @Inject
    public AbstractServiceThree(ServiceTwo serviceTwo, ServiceOne serviceOne)
    {
        this.serviceTwo = serviceTwo;
        this.serviceOne = serviceOne;
    }

    @Override
    protected void startUp() throws Exception {
        assertEquals("Service one is running", State.RUNNING, serviceOne.state());
        assertEquals("Service two is running", State.RUNNING, serviceTwo.state());

        if( errorOnStart )
        {
            throw new RuntimeException("Some error");
        }
    }

    @Override
    protected void shutDown() throws Exception {
        if( errorOnShutdown )
        {
            throw new RuntimeException("Error on shutdown");
        }
    }

    @Override
    public void setErrorOnStart() {
        errorOnStart = true;
    }
    
    @Override
    public void setErrorOnShutdown() {
        errorOnShutdown = true;
    }
}