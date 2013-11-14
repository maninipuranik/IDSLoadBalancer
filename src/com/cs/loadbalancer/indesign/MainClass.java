package com.cs.loadbalancer.indesign;

import com.cs.loadbalancer.indesign.utils.http.MultiThreadedHTTPServer;
import com.cs.loadbalancer.indesign.utils.scheduler.TimeQuantumTimer;
import java.util.concurrent.TimeUnit;

/**
 * @author manini
 *
 */
public class MainClass {

	static String hostName = null;
	static int port = 8082;
	static int threadPoolSize = 10;
	static MultiThreadedHTTPServer multiThreadedHTTPServer;
	static TimeQuantumTimer timeQuantumTimer;

	public static void main(String[] args) {

		try {
			setProcessArguments(args);
			InDesignLoadBalancer inDesignLoadBalancer = new DefaultInDesignLoadBalancerImpl();
			timeQuantumTimer = new TimeQuantumTimer(60000, TimeUnit.MILLISECONDS, inDesignLoadBalancer);
			multiThreadedHTTPServer = new MultiThreadedHTTPServer(hostName, port, threadPoolSize, inDesignLoadBalancer);
			multiThreadedHTTPServer.startServer();
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	protected static void setProcessArguments(String[] args) {
		// String serverListXmlPath = args[0];
		// String hostName = args[1];
		// int port = Integer.parseInt(args[2]);
		// int threadPoolSize = Integer.parseInt(args[3]);
	}

	static class ShutdownHook extends Thread {
		public void run() {
			multiThreadedHTTPServer.stopServer();
		}
	}
}
