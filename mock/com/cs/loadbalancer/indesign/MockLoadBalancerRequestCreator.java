package com.cs.loadbalancer.indesign;

import java.io.File;
import java.io.StringWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.w3c.dom.Document;

public class MockLoadBalancerRequestCreator implements Runnable {

	public static void main(String[] args) throws Throwable {

		/*for (int i = 0; i < 1; i++) {
			runEveryFewMins();
		}*/
		
		MockLoadBalancerRequestCreator mockLoadBalancerRequestCreator = 
									new MockLoadBalancerRequestCreator("res/requests/connect.xml");
		mockLoadBalancerRequestCreator.run();

	}

	protected static String getRequestDocumentAsString(int requestId)
			throws Throwable {

		String xmlString = getRequestDocumentAsString("res/requests/soap-request-"+ requestId + ".xml");

		return xmlString;
	}
	
	protected static String getRequestDocumentAsString(String fileName) throws Throwable{
		
		DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory
				.newInstance();
		docBuilderFactory.setIgnoringElementContentWhitespace(true);
		DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
		Document doc = docBuilder.parse(new File(fileName));

		TransformerFactory transfac = TransformerFactory.newInstance();
		Transformer trans = transfac.newTransformer();
		trans.setOutputProperty(OutputKeys.METHOD, "xml");
		trans.setOutputProperty(OutputKeys.INDENT, "yes");
		trans.setOutputProperty("{http://xml.apache.org/xslt}indent-amount",
				Integer.toString(2));

		StringWriter sw = new StringWriter();
		StreamResult result = new StreamResult(sw);
		DOMSource source = new DOMSource(doc.getDocumentElement());

		trans.transform(source, result);
		String xmlString = sw.toString();

		return xmlString;
	}

	public static void runEveryFewMins() throws Throwable {

		ExecutorService executor1 = Executors.newFixedThreadPool(5);
		ExecutorService executor2 = Executors.newFixedThreadPool(5);
		ExecutorService executor3 = Executors.newFixedThreadPool(5);
		ExecutorService executor4 = Executors.newFixedThreadPool(5);
		ExecutorService executor5 = Executors.newFixedThreadPool(5);

		for (int i = 1; i <= 20; i++) {
			Runnable worker = new MockLoadBalancerRequestCreator(i);
			executor1.execute(worker);
			executor2.execute(worker);
			executor3.execute(worker);
			executor4.execute(worker);
			executor5.execute(worker);
		}

		executor1.shutdown();
		executor2.shutdown();
		executor3.shutdown();
		executor4.shutdown();
		executor5.shutdown();

		while (!executor1.isTerminated() && !executor2.isTerminated()
				&& !executor3.isTerminated() && !executor4.isTerminated()
				&& !executor4.isTerminated()) {

		}
	}

	protected static Document getSampleResponseDocument() throws Throwable {

		DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory
				.newInstance();
		DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
		Document doc = docBuilder.parse(new File(
				"res/response/response-template.xml"));

		return doc;
	}

	protected String requestData;

	public MockLoadBalancerRequestCreator(int requestID) throws Throwable {
		super();
		this.requestData = getRequestDocumentAsString(requestID);
	}
	
	public MockLoadBalancerRequestCreator(String fileName) throws Throwable {
		super();
		this.requestData = getRequestDocumentAsString(fileName);
	}

	@Override
	public void run() {
		CloseableHttpClient httpclient = HttpClients.createDefault();
		try {
			HttpPost httppost = new HttpPost("http://localhost:8080");

			StringEntity reqEntity = new StringEntity(requestData);
			reqEntity.setContentType("application/xml; charset=UTF-8");
			//reqEntity.setContentEncoding("UTF-8");
			httppost.setEntity(reqEntity);

			System.out
					.println("executing request " + httppost.getRequestLine());
			CloseableHttpResponse response = httpclient.execute(httppost);
			try {
				HttpEntity resEntity = response.getEntity();

				System.out.println("----------------------------------------");
				System.out.println(response.getStatusLine());
				if (resEntity != null) {
					System.out.println("Response content length: "
							+ resEntity.getContentLength());
				}

				// System.out.println("MockLoadBalancerRequestCreator.run()" +
				// EntityUtils.toString(resEntity));

			} catch (Throwable e) {
				e.printStackTrace();
			} finally {
				response.close();
			}
		} catch (Throwable e) {
			e.printStackTrace();
		} finally {
			try {
				httpclient.close();
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
	}

}
