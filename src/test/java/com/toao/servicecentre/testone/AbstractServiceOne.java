package com.toao.servicecentre.testone;

import com.google.common.util.concurrent.AbstractIdleService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.toao.servicecentre.annotations.ManagedService;

import static org.junit.Assert.assertEquals;

@Singleton
@ManagedService(level = 0)
public class AbstractServiceOne extends AbstractIdleService implements ServiceOne {
    private final ServiceTwo serviceTwo;
    private final ServiceThree serviceThree;

    @Inject
    public AbstractServiceOne(ServiceTwo serviceTwo, ServiceThree serviceThree)
    {
        this.serviceTwo = serviceTwo;
        this.serviceThree = serviceThree;
    }

    @Override
    protected void startUp() throws Exception {

    }

    @Override
    protected void shutDown() throws Exception {
        assertEquals("Service two is shut down", State.TERMINATED, serviceTwo.state());
    }
}
