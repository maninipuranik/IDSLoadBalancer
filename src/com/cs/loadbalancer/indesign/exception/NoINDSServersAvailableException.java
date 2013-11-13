package com.cs.loadbalancer.indesign.exception;

public class NoINDSServersAvailableException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public NoINDSServersAvailableException() {
		super("None of the servers, configured are reachable");
	}
}
