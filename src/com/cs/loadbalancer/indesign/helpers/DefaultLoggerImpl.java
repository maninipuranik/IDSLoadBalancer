package com.cs.loadbalancer.indesign.helpers;

import org.apache.log4j.Logger;


public class DefaultLoggerImpl {

	protected Logger debugLogger;
	
	public DefaultLoggerImpl(String loggerName) {
		debugLogger = Logger.getLogger(loggerName);
	}
	
	public void debug(String log) {
		debugLogger.debug(log);
	}
	
	public void error(Throwable throwable) {
		debugLogger.error(throwable);
	}
	
	public void error(String log) {
		debugLogger.error(log);
	}

}
