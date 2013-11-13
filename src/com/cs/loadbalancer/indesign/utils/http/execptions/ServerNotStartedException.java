package com.cs.loadbalancer.indesign.utils.http.execptions;

public class ServerNotStartedException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ServerNotStartedException(Throwable cause) {
		super(cause);
	}

}
