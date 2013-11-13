package com.cs.loadbalancer.indesign.utils.scheduler;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class TimeQuantumTimer {

	private final List<TimeQuantumListener> listeners = new CopyOnWriteArrayList<TimeQuantumListener>();
	private final long period;
	private final TimeUnit timeUnit;
	private final ScheduledExecutorService scheduler;
	private final AtomicLong currentTimeQuantum = new AtomicLong(0);
	private final AtomicBoolean hasStarted = new AtomicBoolean(false);
	
	public TimeQuantumTimer(int period, TimeUnit timeUnit, ScheduledExecutorService scheduler) {
		this.period = period;
		this.timeUnit = timeUnit;
		this.scheduler = scheduler;
	}
	
	public TimeQuantumTimer(int period, TimeUnit timeUnit) {
		this(period, timeUnit, Executors.newSingleThreadScheduledExecutor());
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
	
	public void addListener(TimeQuantumListener listener) {
		listeners.add(listener);
	}
	
	private void fireNextTimeQuantum(long timeQuantum) {
		for (TimeQuantumListener l: listeners) {
			l.nextTimeQuantum(timeQuantum);
		}
	}
}
