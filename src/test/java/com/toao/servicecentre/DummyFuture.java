package com.toao.servicecentre;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Service.State;

public class DummyFuture implements ListenableFuture<State> {
	public boolean cancel(boolean arg0) {
		return false;
	}

	public State get() throws InterruptedException, ExecutionException {
		return State.RUNNING;
	}

	public State get(long arg0, TimeUnit arg1) throws InterruptedException, ExecutionException, TimeoutException {
		return null;
	}

	public boolean isCancelled() {
		return false;
	}

	public boolean isDone() {
		return false;
	}

	public void addListener(Runnable arg0, Executor arg1) {
	}
}
