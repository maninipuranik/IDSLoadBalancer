package com.cs.loadbalancer.indesign.utils.http.exception;

public class ResponseTimeoutException extends Exception{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public ResponseTimeoutException(Throwable cause) {
		super(cause);
	}
}
