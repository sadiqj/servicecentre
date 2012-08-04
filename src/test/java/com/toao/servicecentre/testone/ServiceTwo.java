package com.toao.servicecentre.testone;

import javax.inject.Singleton;

import com.google.common.util.concurrent.Service;
import com.toao.servicecentre.annotations.ManagedService;

@ManagedService(level=1)
@Singleton
public interface ServiceTwo extends Service {

}
