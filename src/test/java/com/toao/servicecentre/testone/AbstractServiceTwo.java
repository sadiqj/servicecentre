package com.toao.servicecentre.testone;

import com.google.common.util.concurrent.AbstractIdleService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.toao.servicecentre.annotations.ManagedService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Singleton
@ManagedService(level = 1)
public class AbstractServiceTwo extends AbstractIdleService implements ServiceTwo {
    private final ServiceOne serviceOne;
    private final ServiceThree serviceThree;

    @Inject
    public AbstractServiceTwo(ServiceOne serviceOne, ServiceThree serviceThree)
    {
        this.serviceOne = serviceOne;
        this.serviceThree = serviceThree;
    }

    @Override
    protected void startUp() throws Exception {
        assertEquals("Service one is running", State.RUNNING, serviceOne.state());
    }

    @Override
    protected void shutDown() throws Exception {
        assertTrue("Service three is shut down or failed", serviceThree.state() == State.TERMINATED || serviceThree.state() == State.FAILED);
    }
}