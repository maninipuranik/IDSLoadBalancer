package com.cs.loadbalancer.indesign.utils.scheduler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class TimeQuantumTimer {

	private TimeQuantumListener listener;
	private final long period;
	private final TimeUnit timeUnit;
	private final ScheduledExecutorService scheduler;
	private final AtomicLong currentTimeQuantum = new AtomicLong(0);
	private final AtomicBoolean hasStarted = new AtomicBoolean(false);
	
	protected TimeQuantumTimer(int period, TimeUnit timeUnit, ScheduledExecutorService scheduler, TimeQuantumListener listener) {
		this.period = period;
		this.timeUnit = timeUnit;
		this.scheduler = scheduler;
		this.listener =  listener;
	}
	
	public TimeQuantumTimer(int period, TimeUnit timeUnit,TimeQuantumListener listener) {
		this(period, timeUnit, Executors.newSingleThreadScheduledExecutor(), listener);
	}
	
	public long getCurrentTime() {
		return currentTimeQuantum.get();
	}
	
	public void start() {
		if (!hasStarted.getAndSet(true)) {
			scheduler.scheduleAtFixedRate(new Runnable() {
				@Override public void run() {
					fireNextTimeQuantum(currentTimeQuantum.incrementAndGet());
				}			
			}, period, period, timeUnit);
		}
	}
	
	private void fireNextTimeQuantum(long timeQuantum) {
		listener.performTimedActivity();
	}
}
