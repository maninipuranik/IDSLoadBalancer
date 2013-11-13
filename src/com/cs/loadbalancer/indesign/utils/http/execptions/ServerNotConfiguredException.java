package com.cs.loadbalancer.indesign.utils.http.execptions;

public class ServerNotConfiguredException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ServerNotConfiguredException(Throwable cause) {
		super(cause);
	}

}
