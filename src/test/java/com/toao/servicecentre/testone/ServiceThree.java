package com.toao.servicecentre.testone;

import com.google.common.util.concurrent.Service;

public interface ServiceThree extends Service {
    void setErrorOnStart();

    void setErrorOnShutdown();
}
