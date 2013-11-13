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

import com.cs.loadbalancer.indesign.utils.http.execptions.ServerNotConfiguredException;
import com.cs.loadbalancer.indesign.utils.http.execptions.ServerNotStartedException;

public class MultiThreadedHTTPServer implements Container {

	protected Server server = null;
	protected Connection connection = null;
	protected InetSocketAddress address = null;
	protected HTTPRequestProcessor httpRequestProcessor = null;

	public MultiThreadedHTTPServer(String hostName, int port,
			int threadPoolSize, HTTPRequestProcessor httpRequestProcessor) {

		try {
			executor = Executors.newFixedThreadPool(threadPoolSize);
			this.httpRequestProcessor = httpRequestProcessor;
			this.server = new ContainerServer(this);
			this.connection = new SocketConnection(server);
			if(hostName== null) {
				this.address = new InetSocketAddress(port);
			}
			else {
				this.address = new InetSocketAddress(hostName, port);
			}
			
		} catch (Throwable e) {
			throw new ServerNotConfiguredException(e);
		}

	}

	public void startServer() {

		try {
			connection.connect(address);
			System.out.println("MultiThreadedHTTPServer started at " + address.getHostName() + ":" + address.getPort());
		} catch (IOException e) {
			throw new ServerNotStartedException(e);
		}
	}

	public void stopServer() {

		try {
			connection.close();
		} catch (IOException e) {
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
				
				//TODO reinitialize!!!
				e.printStackTrace();
			}
		}
	}

	private final Executor executor;

	/*
	 * static class ShutdownHook extends Thread { public void run() { try {
	 * connection.close(); } catch (IOException e) { // TODO Auto-generated
	 * catch block e.printStackTrace(); } }
	 * 
	 * public static void main(String[] list) throws Throwable {
	 * 
	 * Runtime.getRuntime().addShutdownHook(new ShutdownHook()); container = new
	 * MultiThreadedHTTPServer(10); server = new ContainerServer(container);
	 * 
	 * } }
	 */

}
