package com.cs.loadbalancer.indesign;

import com.cs.loadbalancer.indesign.utils.http.HTTPRequestProcessor;
import com.cs.loadbalancer.indesign.utils.scheduler.TimeQuantumListener;

/**
 * @author manini
 *
 */
public interface InDesignLoadBalancer extends HTTPRequestProcessor, TimeQuantumListener {
	
	//public void synchronizeWithAllINDS();

}