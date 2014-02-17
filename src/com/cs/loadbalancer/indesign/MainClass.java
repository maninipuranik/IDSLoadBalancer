package com.cs.loadbalancer.indesign;

import java.util.concurrent.TimeUnit;

import com.cs.loadbalancer.indesign.utils.http.MultiThreadedHTTPServer;
import com.cs.loadbalancer.indesign.utils.scheduler.TimeQuantumTimer;

/**
 * @author manini
 *
 */
public class MainClass {

	static String host;
	static String port;
	static String threadPoolSize;
	static MultiThreadedHTTPServer multiThreadedHTTPServer;
	static TimeQuantumTimer timeQuantumTimer;

	public static void main(String[] args) {

		try {
			setProcessArguments(args);
			InDesignLoadBalancer inDesignLoadBalancer = new DefaultInDesignLoadBalancerImpl();
			timeQuantumTimer = new TimeQuantumTimer(5000, TimeUnit.MILLISECONDS, inDesignLoadBalancer);
			timeQuantumTimer.start();
			multiThreadedHTTPServer = new MultiThreadedHTTPServer(host, port, threadPoolSize, inDesignLoadBalancer);
			multiThreadedHTTPServer.startServer();
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	protected static void setProcessArguments(String[] args) {
		
		if(args!=null && args.length>0) {
			for (int i = 0; i < args.length; i++) {
				if(args[i].equals("-p")) {
					if(args.length > i+1 && args[i+1]!=null && !(args[i+1].trim().equals("")) && !args[i+1].startsWith("-")) {
						try{
							port = args[i+1];
						}
						catch(NumberFormatException e) {
						}
					}
				}
				else if (args[i].equals("-h")) {
					if(args.length > i+1 && args[i+1]!=null && !(args[i+1].trim().equals("")) && !args[i+1].startsWith("-")) {
						host = args[i+1];
					}
				}
				else if (args[i].equals("-t")) {
					if(args.length > i+1 && args[i+1]!=null && !(args[i+1].trim().equals("")) && !args[i+1].startsWith("-")) {
						threadPoolSize = args[i+1];
					}
				}
			}
		}
		
	}

	static class ShutdownHook extends Thread {
		public void run() {
			try {
				multiThreadedHTTPServer.stopServer();
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
	}
}
