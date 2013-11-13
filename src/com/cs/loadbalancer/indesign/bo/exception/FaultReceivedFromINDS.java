package com.cs.loadbalancer.indesign.bo.exception;

public class FaultReceivedFromINDS extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public FaultReceivedFromINDS(String faultcode, String faultstring) {
		super(faultcode + " : " + faultstring);
	}
}
