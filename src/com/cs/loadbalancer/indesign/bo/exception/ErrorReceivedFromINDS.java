package com.cs.loadbalancer.indesign.bo.exception;

public class ErrorReceivedFromINDS extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public ErrorReceivedFromINDS(String errorNumber, String errorString) {
		super(errorNumber + " : " + errorString);
	}

	
}
