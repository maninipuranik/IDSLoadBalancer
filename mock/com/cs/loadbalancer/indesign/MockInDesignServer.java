package com.cs.loadbalancer.indesign;

import java.io.File;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.LinkedHashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.simpleframework.http.Request;
import org.simpleframework.http.Response;
import org.simpleframework.http.core.Container;
import org.simpleframework.http.core.ContainerServer;
import org.simpleframework.transport.Server;
import org.simpleframework.transport.connect.Connection;
import org.simpleframework.transport.connect.SocketConnection;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class MockInDesignServer implements Container {

	protected int maxNoOfRequestsToReleaseCounter = 5;
	protected LinkedHashMap<String, Integer> mamFileIdRequestCounter = new LinkedHashMap<String, Integer>();

	public static void main(String[] args) throws Throwable {

		Container container = new MockInDesignServer();
		Server server = new ContainerServer(container);
		Connection connection = new SocketConnection(server);
		SocketAddress address = new InetSocketAddress(Integer.parseInt(args[0]));
		connection.connect(address);

	}

	public void handle(Request request, Response response) {
		try {
			PrintStream body = response.getPrintStream();
			long time = System.currentTimeMillis();

			response.setValue("Content-Type", "text/plain");
			response.setValue("Server", "HelloWorld/1.0 (Simple 4.0)");
			response.setDate("Date", time);
			response.setDate("Last-Modified", time);

			Document requestDocument = getRequestAsDocument(request
					.getContent());
			processRequest(requestDocument);
			Document responseDocument = getSampleResponseDocument();

			if (mamFileIdRequestCounter.size() > 0) {
				String openFileListStr = getOpenMamFileIdsAsString(mamFileIdRequestCounter);
				setOpenMamFileIdsInDocument(responseDocument, openFileListStr);
			}

			String responseString = transformDocumentToString(responseDocument);

			body.println(responseString);
			body.close();
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	protected static Document getRequestAsDocument(String requestData)
			throws Throwable {

		DocumentBuilderFactory docfactory = DocumentBuilderFactory
				.newInstance();
		docfactory.setIgnoringElementContentWhitespace(true);
		DocumentBuilder db = docfactory.newDocumentBuilder();
		InputSource is = new InputSource();
		is.setCharacterStream(new StringReader(requestData));
		Document doc = db.parse(is);

		return doc;
	}

	protected synchronized void processRequest(Document document)
			throws Throwable {

		XPath xpath = XPathFactory.newInstance().newXPath();

		XPathExpression expr = xpath
				.compile("//scriptArgs/name[text()='CurrentMamID'] ");
		Object result = expr.evaluate(document, XPathConstants.NODESET);
		NodeList nodes = (NodeList) result;
		String mamFileID = nodes.item(0).getNextSibling().getParentNode()
				.getChildNodes().item(3).getTextContent();

		System.out.println("mamFileID : " + mamFileID);

		if (!mamFileIdRequestCounter.containsKey(mamFileID)) {
			mamFileIdRequestCounter.put(mamFileID, 0);
		}

		if (mamFileIdRequestCounter.get(mamFileID).intValue() < maxNoOfRequestsToReleaseCounter) {
			mamFileIdRequestCounter.put(mamFileID,
					mamFileIdRequestCounter.get(mamFileID).intValue() + 1);
		} else {
			System.out.println("******************* 5 Done for" + mamFileID);
			mamFileIdRequestCounter.remove(mamFileID);
		}

	}

	protected static Document getSampleResponseDocument() throws Throwable {

		DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory
				.newInstance();
		docBuilderFactory.setIgnoringElementContentWhitespace(true);
		DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
		Document doc = docBuilder.parse(new File(
				"res/response/response-template.xml"));

		return doc;
	}

	protected static String getOpenMamFileIdsAsString(
			LinkedHashMap<String, Integer> mamFileIdRequestCounter) {
		StringBuffer openFileListStr = new StringBuffer("|");
		int openFileListLength = mamFileIdRequestCounter.size();
		String[] mamFileIds = mamFileIdRequestCounter.keySet().toArray(
				new String[openFileListLength]);
		for (int i = 0; i < openFileListLength - 1; i++) {
			openFileListStr.append(mamFileIds[i]);
			openFileListStr.append("%2C");
		}
		openFileListStr.append(mamFileIds[openFileListLength - 1]);

		return openFileListStr.toString();
	}

	protected static void setOpenMamFileIdsInDocument(Document doc,
			String openFileListStr) throws Throwable {

		NodeList dataNodes = doc.getElementsByTagName("data");
		dataNodes.item(0).setTextContent(openFileListStr.toString());
	}

	protected static String transformDocumentToString(Document doc)
			throws Throwable {

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

	public static void testResponseCreation() throws Throwable {

		LinkedHashMap<String, Integer> mamFileIdRequestCounter = new LinkedHashMap<String, Integer>();

		mamFileIdRequestCounter.put("1", 1);
		mamFileIdRequestCounter.put("2", 2);
		mamFileIdRequestCounter.put("3", 3);
		mamFileIdRequestCounter.put("4", 4);
		mamFileIdRequestCounter.put("5", 5);

		Document responseDocument = getSampleResponseDocument();

		if (mamFileIdRequestCounter.size() > 0) {
			String openFileListStr = getOpenMamFileIdsAsString(mamFileIdRequestCounter);
			System.out.println("MockInDesignServer.main()" + openFileListStr);
			setOpenMamFileIdsInDocument(responseDocument, openFileListStr);
		}

		String responseString = transformDocumentToString(responseDocument);

		System.out.println("MockInDesignServer.main()" + responseString);
	}

}
