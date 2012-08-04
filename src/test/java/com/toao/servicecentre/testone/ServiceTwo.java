package com.toao.servicecentre.testone;

import com.google.common.util.concurrent.Service;
import com.toao.servicecentre.annotations.ManagedService;

@ManagedService(level = 1)
public interface ServiceTwo extends Service {

}
