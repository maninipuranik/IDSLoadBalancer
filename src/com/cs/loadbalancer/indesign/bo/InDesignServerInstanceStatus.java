package com.cs.loadbalancer.indesign.bo;

/**
 * @author manini
 *
 */
public enum InDesignServerInstanceStatus {

	FREE,
	OCCUPIED,
	NOT_REACHABLE, 
	IN_RETRY,
	INTERIM;

}
