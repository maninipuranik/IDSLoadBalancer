package com.cs.loadbalancer.indesign.bo;

import java.io.FileInputStream;
import java.io.Serializable;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashSet;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.IOUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.cs.loadbalancer.indesign.bo.exception.ErrorReceivedFromINDS;
import com.cs.loadbalancer.indesign.bo.exception.FaultReceivedFromINDS;
import com.cs.loadbalancer.indesign.helpers.DefaultLoggerImpl;

/**
 * @author manini
 *
 */
public class InDesignRequestResponseInfo implements Serializable {
	
	private static DefaultLoggerImpl requestLogger = new DefaultLoggerImpl("requestLogger");
	private static DefaultLoggerImpl pingLogger = new DefaultLoggerImpl("pingLogger");
	private static String logSeparator = ";";
	private static final long serialVersionUID = 1L;
	private static int requestCounter;

	//often used fields
	protected int requestID;
	protected String mamFileID;
	protected boolean isFileRequest;
	protected InDesignServerInstance inDesignServerInstance;
	protected boolean isNewServerAssigned;
	protected boolean isPingRequest;
	
	//fields for logging
	protected String clientIP;
	protected String sessionContext;
	protected String userId;
	protected String processID;
	protected String documentLocked;

	//read only fields to extract info
	protected String requestData;
	protected String responseData;
	protected String errorMessage;
	
	protected LinkedHashSet<String> openFilesFromResponse;
	protected InDesignRequestResponseStatus status = InDesignRequestResponseStatus.RECEIVED_FROM_WEBSERVER;
	
	
	static {
		requestLogger.debug(toStringStatic());
	}
	
	public void receivedFromWebServer(String requestData) {
		requestID = ++requestCounter;
		status = InDesignRequestResponseStatus.RECEIVED_FROM_WEBSERVER;
		this.requestData = requestData;
		Document requestDocument;
		try {
			requestDocument = getDocumentFromString(requestData);
			getInfoFromRequest(requestDocument);
		} catch (Throwable e) {
			// TODO Eat up the exception
		}
		log(toStringInstance());
	}
	
	public void pingFromLoadBalancer(String requestData) {
		isPingRequest = true;
		status = InDesignRequestResponseStatus.PING_FROM_LOADBALANCER;
		this.requestData = requestData;
		log(toStringInstance());
	}
	
	public void waitingForINDS() {
		status = InDesignRequestResponseStatus.WAITING_FOR_INDS;
		inDesignServerInstance = null;
		log(toStringInstance());
	}
	
	public void gotINDS(InDesignServerInstance inDesignServerInstance, boolean isNewServerAssigned) {
		status = InDesignRequestResponseStatus.GOT_INDS;
		this.inDesignServerInstance = inDesignServerInstance;
		this.isNewServerAssigned = isNewServerAssigned;
		
		if(isFileRequest) {
			inDesignServerInstance.openFileList.add(mamFileID);
		}
		log(toStringInstance());
	}
	
	public void sendingToINDS() {
		status = InDesignRequestResponseStatus.SENDING_TO_INDS;
		log(toStringInstance());
	}
	
	public void receivedFromINDS(String responseData) {
		status = InDesignRequestResponseStatus.RECEIVED_FROM_INDS;
		this.responseData = responseData;
		log(toStringInstance());
	}
	
	public void processNormalResponseFromINDS() {
		
		try {
			checkErrorOrFaultInResponse(responseData);
			status = InDesignRequestResponseStatus.SUCCESS_FROM_INDS;
			log(toStringInstance());
		} 
		catch (FaultReceivedFromINDS e) {
			status = InDesignRequestResponseStatus.FAULT_FROM_INDS;
			errorMessage = e.getMessage();
			log(toStringInstance());
		} 
		catch (ErrorReceivedFromINDS e) {
			status = InDesignRequestResponseStatus.ERROR_FROM_INDS;
			errorMessage = e.getMessage();
			log(toStringInstance());
		}
	}
	
	public void processFileResponseFromINDS() {

		try {
			checkErrorOrFaultInResponse(responseData);
			
			openFilesFromResponse = getOpenFilesFromResponse(responseData);
			
			if(openFilesFromResponse!=null) {
				
				if(openFilesFromResponse.size()==0) {
					log("The response ->" + responseData);
				}
				
				LinkedHashSet<String> extraFiles = new LinkedHashSet<String>();
				extraFiles.addAll(openFilesFromResponse);
				extraFiles.removeAll(inDesignServerInstance.openFileList);
				if(extraFiles.size()>0) {
					
					errorMessage = "The server, " + inDesignServerInstance.url + ", has the open files ->" + openFilesFromResponse;
					errorMessage += " And the load balancer has the open files " + inDesignServerInstance.url + "->" + inDesignServerInstance.openFileList.toString();
				}
				
				inDesignServerInstance.openFileList = openFilesFromResponse;
			}
			status = InDesignRequestResponseStatus.SUCCESS_FROM_INDS;
			log(toStringInstance());
		} 
		catch (FaultReceivedFromINDS e) {
			status = InDesignRequestResponseStatus.FAULT_FROM_INDS;
			errorMessage = e.getMessage();
			log(toStringInstance());
		} 
		catch (ErrorReceivedFromINDS e) {
			status = InDesignRequestResponseStatus.ERROR_FROM_INDS;
			errorMessage = e.getMessage();
			log(toStringInstance());
		}
	}
	
	public void processErrorSendingRequestToINDSGettingNewINDS(String message) {
		if(isFileRequest) {
			inDesignServerInstance.openFileList.remove(mamFileID);
		}
		status = InDesignRequestResponseStatus.ERROR_SENDING_TO_INDS_GETTING_NEW_INDS;
		errorMessage = message;
		log(toStringInstance());
	}
	
	public void processErrorInRequestProcessing(String message) {
		if(isFileRequest) {
			inDesignServerInstance.openFileList.remove(mamFileID);
		}
		status = InDesignRequestResponseStatus.ERROR_IN_REQUEST_PROCESSING;
		errorMessage = message;
		log(toStringInstance());
	}
	
	public void noINDSAvailableSendingErrorToWebserver() {
		status = InDesignRequestResponseStatus.NO_INDS_AVAILABLE_SENDING_ERROR_TO_WEBSERVER;
		log(toStringInstance());
	}
	
	public void processINDSBusyResponseFromINDS(String message) {
		status = InDesignRequestResponseStatus.SERVER_BUSY;
		errorMessage = message;
		log(toStringInstance());
	}
	
	public void sendingToWebserver() {
		status = InDesignRequestResponseStatus.SENT_TO_WEBSERVER;
		log(toStringInstance());
	}

	/**
	 * @return the mamFileID
	 */
	public String getMamFileID() {
		return mamFileID;
	}

	/**
	 * @return the isFileRequest
	 */
	public boolean isFileRequest() {
		return isFileRequest;
	}

	/**
	 * @return the inDesignServerInstance
	 */
	public InDesignServerInstance getInDesignServerInstance() {
		return inDesignServerInstance;
	}


	/**
	 * @return the requestData
	 */
	public String getRequestData() {
		return requestData;
	}


	/**
	 * @return the responseData
	 */
	public String getResponseData() {
		return responseData;
	}
	
	/**
	 * @return the isNewServerAssigned
	 */
	public boolean isNewServerAssigned() {
		return isNewServerAssigned;
	}

	/**
	 * @return the openFilesFromResponse
	 */
	public LinkedHashSet<String> getOpenFilesFromResponse() {
		return openFilesFromResponse;
	}
	
	protected void log(String log) {
		
		if(isPingRequest) {
			pingLogger.debug(log);
		}
		else {
			requestLogger.debug(log);
		}
	}

	protected void getInfoFromRequest(Document document) {

		XPath xpath = XPathFactory.newInstance().newXPath();
		XPathExpression expr = null;
		Object result = null;
		NodeList nodes = null;
		isFileRequest = false;

		try {
			expr = xpath
					.compile("//scriptArgs/name[text()='CurrentMamID'] ");
			result = expr.evaluate(document, XPathConstants.NODESET);
			nodes = (NodeList) result;
			if(nodes!=null && nodes.item(0)!=null){
				Node valueTag = nodes.item(0).getNextSibling();
				mamFileID = valueTag.getTextContent().trim();
				try {
					Long.parseLong(mamFileID);
					isFileRequest = true;
				} 
				catch(NumberFormatException ex){
				}
				
			}
		} catch (Throwable e) {
			// TODO Do Nothing
		} 

		try {
			expr = xpath.compile("//scriptArgs/name[text()='CurrentUserID'] ");
			result = expr.evaluate(document, XPathConstants.NODESET);
			nodes = (NodeList) result;
			if(nodes!=null && nodes.item(0)!=null){
				Node valueTag = nodes.item(0).getNextSibling();
				userId = valueTag.getTextContent();
			}
		} catch (Throwable e) {
			// TODO Do Nothing
		} 
		
		try {
			expr = xpath
					.compile("//scriptArgs/name[text()='CurrentSessionContext'] ");
			result = expr.evaluate(document, XPathConstants.NODESET);
			nodes = (NodeList) result;
			if(nodes!=null && nodes.item(0)!=null){
				Node valueTag = nodes.item(0).getNextSibling();
				sessionContext = valueTag.getTextContent();
			}
		} catch (Throwable e) {
			// TODO Do Nothing
		} 

		try {
			expr = xpath.compile("//scriptArgs/name[text()='CurrentProcessID'] ");
			result = expr.evaluate(document, XPathConstants.NODESET);
			nodes = (NodeList) result;
			if(nodes!=null && nodes.item(0)!=null){
				Node valueTag = nodes.item(0).getNextSibling();
				processID = valueTag.getTextContent();
			}
		} catch (Throwable e) {
			// TODO Do Nothing
		} 

		try {
			expr = xpath.compile("//scriptArgs/name[text()='documentLocked'] ");
			result = expr.evaluate(document, XPathConstants.NODESET);
			nodes = (NodeList) result;
			if(nodes!=null && nodes.item(0)!=null){
				Node valueTag = nodes.item(0).getNextSibling();
				documentLocked = valueTag.getTextContent();
			}
		} catch (Throwable e) {
			// TODO Do Nothing
		}

	}
	
	
	public static void main(String[] args) throws Throwable {

		testRequest();
	}
	
	
	protected void checkErrorOrFaultInResponse(String response) throws FaultReceivedFromINDS, ErrorReceivedFromINDS {
		
		if(response.contains("faultcode")) {
			String faultcode = "XYZ";
			String[] splitString1 = response.split("<faultcode>");
			
			if(splitString1.length>=2) {
				String[] splitString2 = splitString1[1].split("</faultcode>");
				faultcode = splitString2[0];
			}
			
			String faultstring = "XYZ";
			splitString1 = response.split("<faultstring>");
			
			if(splitString1.length>=2) {
				String[] splitString2 = splitString1[1].split("</faultstring>");
				faultstring = splitString2[0];
			}
			throw new FaultReceivedFromINDS(faultcode, faultstring);
		}
		
		if(response.contains("<errorNumber>")) {
			String[] splitString1 = response.split("<errorNumber>");
			String errorNumber = "0";
			if(splitString1.length>=2) {
				
				String[] splitString2 = splitString1[1].split("</errorNumber>");
				errorNumber = splitString2[0];
				if(errorNumber!=null && !errorNumber.trim().equals("0")){
					
					String errorString = "XYZ";
					splitString1 = response.split("<errorString>");
					
					if(splitString1.length>=2) {
						splitString2 = splitString1[1].split("</errorString>");
						errorString = splitString2[0];
					}
					throw new ErrorReceivedFromINDS(errorNumber, errorString);
				}
			}
		}
	}

	protected static LinkedHashSet<String> getOpenFilesFromResponse(String response) {

		LinkedHashSet<String> openFileList = new LinkedHashSet<String>();
		int fileStartIndex = response.lastIndexOf("|");
		
		if(fileStartIndex>0){
			String subString1 = response.substring(fileStartIndex + 1);
			int fileEndIndex = subString1.indexOf("</data>");

			if(fileEndIndex>0){
				String fileString = subString1.substring(0, fileEndIndex);
				String[] fileResult = fileString.split("%2C");
				int fileResultLength = fileResult.length;
				for (int i = 0; i < fileResultLength; i++) {
					String fileIdStr = fileResult[i];
					try{
						Long.parseLong(fileIdStr);
						openFileList.add(fileIdStr);
					}
					catch(NumberFormatException e) {
						System.out.println("Invalid Opne File Id->" + fileIdStr);
					}
				}
			}
		}
		
		return openFileList;
	}

	protected static Document getDocumentFromString(String requestData)
			throws Throwable {

		DocumentBuilderFactory docfactory = DocumentBuilderFactory
				.newInstance();
		docfactory.setIgnoringElementContentWhitespace(false);
		DocumentBuilder db = docfactory.newDocumentBuilder();
		InputSource is = new InputSource();
		is.setCharacterStream(new StringReader(requestData));
		Document doc = db.parse(is);

		return doc;
	}

	protected static void testResponse() throws Throwable {

		String response = getFileDataAsString("res/soap-response.xml");
		System.out.println("InDesignRequestResponseInfo.main() = " + getOpenFilesFromResponse(response));
	}

	protected static void testRequest() throws Throwable {

		String request = getFileDataAsString("res/soap-request.xml");
		System.out.println("InDesignRequestResponseInfo.testRequest() ->" + request);
	}

	protected static String getFileDataAsString(String fileName)
			throws Throwable {

		FileInputStream inputStream = new FileInputStream(fileName);
		try {
			String response = IOUtils.toString(inputStream);
			return response;
		} finally {
			inputStream.close();
		}

	}

	
	protected static String toStringStatic() {
		StringBuilder builder = new StringBuilder();
		builder.append("time");
		builder.append(logSeparator);
		builder.append("thread");
		builder.append(logSeparator);
		builder.append("requestID");
		builder.append(logSeparator);
		builder.append("mamFileID");
		builder.append(logSeparator);
		builder.append("inDesignServerInstance");
		//builder.append(logSeparator);
		//builder.append("isNewServerAssigned");
		builder.append(logSeparator);
		//builder.append("clientIP");
		//builder.append(logSeparator);
		builder.append("sessionContext");
		builder.append(logSeparator);
		/*builder.append("userId");
		builder.append(logSeparator);
		builder.append("processID");
		builder.append(logSeparator);*/
		builder.append("documentLocked");
		builder.append("logSeparator");
		/*builder.append("requestData");
		builder.append(logSeparator);
		builder.append("responseData");
		builder.append(logSeparator);*/
		builder.append("openFilesFromResponse");
		builder.append(logSeparator);
		builder.append("status");
		return builder.toString();
	}
	
	protected String toStringInstance() {
		StringBuilder builder = new StringBuilder();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		builder.append(sdf.format(new Date(System.currentTimeMillis())));
		builder.append(logSeparator);
		builder.append(Thread.currentThread().getName());
		builder.append(logSeparator);
		builder.append("!!!!"+requestID+"!!!!");
		builder.append(logSeparator);
		builder.append(mamFileID);
		builder.append(logSeparator);
		builder.append(inDesignServerInstance);
		//builder.append(logSeparator);
		//builder.append(isNewServerAssigned);
		builder.append(logSeparator);
		//builder.append(clientIP);
		//builder.append(logSeparator);
		builder.append(sessionContext);
		builder.append(logSeparator);
		//builder.append(userId);
		//builder.append(logSeparator);
		//builder.append(processID);
		//builder.append(logSeparator);
		builder.append(documentLocked);
		builder.append(logSeparator);
		/*builder.append(requestData);
		builder.append(logSeparator);
		builder.append(responseData);
		builder.append(logSeparator);*/
		builder.append(openFilesFromResponse);
		builder.append(logSeparator);
		builder.append(status);
		return builder.toString();
	}


}
