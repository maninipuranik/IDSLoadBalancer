package com.cs.loadbalancer.indesign.utils.http;

import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.simpleframework.http.Request;
import org.simpleframework.http.Response;
import org.simpleframework.http.core.Container;
import org.simpleframework.http.core.ContainerServer;
import org.simpleframework.transport.Server;
import org.simpleframework.transport.connect.Connection;
import org.simpleframework.transport.connect.SocketConnection;

import com.cs.loadbalancer.indesign.helpers.DefaultLoggerImpl;
import com.cs.loadbalancer.indesign.utils.http.execptions.ServerNotConfiguredException;
import com.cs.loadbalancer.indesign.utils.http.execptions.ServerNotStartedException;

public class MultiThreadedHTTPServer implements Container {

	private static DefaultLoggerImpl httpServerLogger = new DefaultLoggerImpl("httpServerLogger");
	
	protected static final String DEFAULT_HOST = "0.0.0.0";
	protected static final int DEFAULT_PORT = 8888;
	protected static final int DEFAULT_THREAD_POOL_SIZE = 10;
	protected Server server = null;
	protected Connection connection = null;
	protected InetSocketAddress address = null;
	protected HTTPRequestProcessor httpRequestProcessor = null;
	protected Executor executor = null;
	protected String host;
	protected int port;
	private int threadPoolSize;

	public MultiThreadedHTTPServer(String host, String port,
									String threadPoolSize, 
									HTTPRequestProcessor httpRequestProcessor) {

		validateAndSetHostAndPort(host, port);
		validateAndSetThreadPoolSize(threadPoolSize);
		
		try {
			executor = Executors.newFixedThreadPool(this.threadPoolSize);
			this.httpRequestProcessor = httpRequestProcessor;
			this.server = new ContainerServer(this);
			this.connection = new SocketConnection(server);
			this.address = new InetSocketAddress(this.host, this.port);
			
		} catch (Throwable e) {
			httpServerLogger.error(e);
			throw new ServerNotConfiguredException(e);
		}

	}

	public void startServer() {

		try {
			connection.connect(address);
			System.out.println("MultiThreadedHTTPServer started at " + address.getHostName() + ":" + address.getPort() + " with the thread pool of " + threadPoolSize);
		} catch (IOException e) {
			httpServerLogger.error(e);
			throw new ServerNotStartedException(e);
		}
	}

	public void stopServer() {

		try {
			connection.close();
		} catch (IOException e) {
			httpServerLogger.error(e);
			throw new ServerNotStartedException(e);
		}
	}

	public void handle(Request request, Response response) {
		Task task = new Task(request, response);
		executor.execute(task);
	}

	public class Task implements Runnable {

		private final Response response;
		private final Request request;

		public Task(Request request, Response response) {
			this.response = response;
			this.request = request;
		}

		public void run() {
			try {

				String requestStr = request.getContent();
				String responseData = httpRequestProcessor
						.processSimpleXMLRequest(requestStr);
				PrintStream body = response.getPrintStream();
				long time = System.currentTimeMillis();
				response.setValue("Content-Type", "application/xml");
				response.setValue("Server",
						"CS InDesign Server Load Balancer 1.0");
				response.setDate("Date", time);
				response.setDate("Last-Modified", time);
				body.println(responseData);
				body.close();

			} catch (Exception e) {
				
				httpServerLogger.error(e);
			}
		}
	}
	
	protected void validateAndSetHostAndPort(String host, String port) {
		
		//utility function, picked up from net to check the validity of the host and port combo
		if(host==null) {
			System.out.println("No host (option: -h), provided, using default "+DEFAULT_HOST);
			this.host = DEFAULT_HOST;
		} else {
			this.host = host;
		}
		if(port==null) {
			System.out.println("No port (option: -p), provided, using default "+DEFAULT_PORT);
			this.port = DEFAULT_PORT;
		} 
		else {
			try {
				int portNo = Integer.parseInt(port);
				if(portNo>0) {
					this.port = portNo;
				} else {
					System.out.println("Invalid port, using default "+DEFAULT_PORT);
					this.port = DEFAULT_PORT;
				}
			} catch (NumberFormatException e) {
				System.out.println("Invalid port, using default "+DEFAULT_PORT);
				this.port = DEFAULT_PORT;
			}
		}
	}
	
	protected void validateAndSetThreadPoolSize(String threadPoolSize) {

		if(threadPoolSize==null) {
			System.out.println("No thread pool size (option: -t), provided, using default "+DEFAULT_THREAD_POOL_SIZE);
			this.threadPoolSize = DEFAULT_THREAD_POOL_SIZE;
		} else {
			
			try {
				int size = Integer.parseInt(threadPoolSize);
				if(size>0) {
					this.threadPoolSize = size;
				} else {
					System.out.println("Invalid thread pool size, using default "+DEFAULT_THREAD_POOL_SIZE);
					this.threadPoolSize = DEFAULT_THREAD_POOL_SIZE;
				}
			} catch (NumberFormatException e) {
				System.out.println("Invalid thread pool size, using default "+DEFAULT_THREAD_POOL_SIZE);
				this.threadPoolSize = DEFAULT_THREAD_POOL_SIZE;
			}
	    }
	}

}
