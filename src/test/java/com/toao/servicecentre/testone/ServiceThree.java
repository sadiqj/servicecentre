package com.toao.servicecentre.testone;

import com.google.common.util.concurrent.Service;
import com.toao.servicecentre.annotations.ManagedService;

@ManagedService(level = 2)
public interface ServiceThree extends Service {

}
