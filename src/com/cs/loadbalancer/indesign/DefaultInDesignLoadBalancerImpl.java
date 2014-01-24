package com.cs.loadbalancer.indesign;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;

import org.apache.commons.io.FileUtils;

import com.cs.loadbalancer.indesign.bo.InDesignRequestResponseInfo;
import com.cs.loadbalancer.indesign.bo.InDesignServerInstance;
import com.cs.loadbalancer.indesign.bo.InDesignServerInstanceStatus;
import com.cs.loadbalancer.indesign.exception.NotAbletoCheckINDSServersAvailabilityException;
import com.cs.loadbalancer.indesign.helpers.DefaultLoggerImpl;
import com.cs.loadbalancer.indesign.helpers.InDesignServerListLoader;
import com.cs.loadbalancer.indesign.utils.http.HTTPClient;
import com.cs.loadbalancer.indesign.utils.http.exception.ConnectionException;
import com.cs.loadbalancer.indesign.utils.http.exception.ResponseTimeoutException;


/**
 * @author manini
 *
 */
public class DefaultInDesignLoadBalancerImpl 
	implements InDesignLoadBalancer 
{
	
	private static DefaultLoggerImpl loadBalancerLogger = new DefaultLoggerImpl("loadBalancerLogger");
	private static DefaultLoggerImpl idlkLogger = new DefaultLoggerImpl("idlkLogger");
	private static DefaultLoggerImpl openFilesLogger = new DefaultLoggerImpl("openFilesLogger");
	
	private static String serverListXmlPath = "res/indesignservers.xml";
	
	public ArrayList<InDesignServerInstance> freeServerList = new ArrayList<InDesignServerInstance>();
	public ArrayList<InDesignServerInstance> occupiedInDesignServersList = new ArrayList<InDesignServerInstance>();
	public ArrayList<InDesignServerInstance> allServerList = new ArrayList<InDesignServerInstance>();
	
	
	public DefaultInDesignLoadBalancerImpl() throws Throwable {
		super();
		loadBalancerLogger.debug("*************************Starting loadBalancerLogger************************");
		idlkLogger.debug("*************************Starting idlkLogger************************");
		openFilesLogger.debug("*************************Starting openFilesLogger************************");
		allServerList = InDesignServerListLoader.unmarshal(new File(serverListXmlPath));
		performTimedActivity();
	}
	
	
	public void performTimedActivity() {
		
		//TODO need to configure paths as per the load balancer configuration
		String pingRequest = null;
		try {
			pingRequest = FileUtils.readFileToString(new File("res/requests/pingRequest.xml"), "UTF-8");
		} catch (IOException e) {
			loadBalancerLogger.error(e);
			throw new NotAbletoCheckINDSServersAvailabilityException(e);
		}
		
		InDesignServerInstance[] allServerArr = allServerList.toArray(new InDesignServerInstance[allServerList.size()]);
		
		for (int i = 0; i < allServerArr.length; i++) {
			InDesignServerInstance inDesignServerInstance = allServerArr[i];
			
			//if this server is currently not in free or occupied server list, then only take it for testing
			// Note : this particular call isThisINDSInInFreeOrOccupiedList goes to a synchronized method
			if(inDesignServerInstance.status.equals(InDesignServerInstanceStatus.IN_RETRY)) {
			
				InDesignRequestResponseInfo inDesignRequestResponseInfo = new InDesignRequestResponseInfo();
				inDesignRequestResponseInfo.pingFromLoadBalancer(pingRequest);
				inDesignRequestResponseInfo.gotINDS(inDesignServerInstance, true);
				//send the request to the specific InDesignServer
				try {
					sendRequestToINDS(inDesignRequestResponseInfo);
					//If the sending/receiving of response was successful
					freeUpAndProcessResponseFromINDS(inDesignRequestResponseInfo);
				} 
				catch (Throwable e) {
					loadBalancerLogger.error(e);
					if(e instanceof ResponseTimeoutException) {
						dontFreeUpAndProcessINDSBusyResponseFromINDS(inDesignRequestResponseInfo, e.getMessage());
					}
					else if (e instanceof ConnectionException) {
						dontFreeUpAndProcessErrorSendingRequestToINDS(inDesignRequestResponseInfo, e.getMessage());
					} 
					else {
						freeUpAndProcessErrorInRequestProcessing(inDesignRequestResponseInfo, e.getMessage());
					}
				}
				//TODO inspect the response to check if plugins are installed!!!
			}
		}
		isAnyINDSIsAlive();
		checkINDSOpenFileListSanity();
	}
	
	
	@Override
	public String processSimpleXMLRequest(String requestData) {
		
		InDesignRequestResponseInfo inDesignRequestResponseInfo = new InDesignRequestResponseInfo();
		inDesignRequestResponseInfo.receivedFromWebServer(requestData);
		
		String response = processSimpleXMLRequest(inDesignRequestResponseInfo);
		inDesignRequestResponseInfo.sendingToWebserver();
		
		return response;
	}
	
	
	public String processSimpleXMLRequest(InDesignRequestResponseInfo inDesignRequestResponseInfo) {
		
		if(isAnyINDSIsAlive()) {
			
			//Assign an INDS to this request
			assignINDS(inDesignRequestResponseInfo);
			
			//send the request to the assigned INDS
			try {
				
				sendRequestToINDS(inDesignRequestResponseInfo);
				//If the sending/receiving of response was successful
				freeUpAndProcessResponseFromINDS(inDesignRequestResponseInfo);
				
			} 
			catch (Throwable e) {
				
				loadBalancerLogger.error(e);
				
				//TODO should we really do this?
				/*if (e instanceof ConnectionException) {
					//internally free up this file from open file list
					dontFreeUpAndProcessErrorSendingRequestToINDS(inDesignRequestResponseInfo, e.getMessage());
					
					if(inDesignRequestResponseInfo.isNewServerAssigned()) {
						//making a recursive call until...
						try {
							Thread.sleep(30000);
						} catch (InterruptedException e1) {
						}
						inDesignRequestResponseInfo.processPrepareForNewINDS();
						processSimpleXMLRequest(inDesignRequestResponseInfo);
					}
				} 
				else */
				if(e instanceof ResponseTimeoutException) {
					dontFreeUpAndProcessINDSBusyResponseFromINDS(inDesignRequestResponseInfo, e.getMessage());
				}
				else if (e instanceof ConnectionException) {
					dontFreeUpAndProcessErrorSendingRequestToINDS(inDesignRequestResponseInfo, e.getMessage());
				} 
				else {
					freeUpAndProcessErrorInRequestProcessing(inDesignRequestResponseInfo, e.getMessage());
				}
			} 
			
		}
		else {
			processNoINDSAvailable(inDesignRequestResponseInfo);
		}
		
		return inDesignRequestResponseInfo.getResponseData();
	}
	
	
	protected synchronized void assignINDS(InDesignRequestResponseInfo inDesignRequestResponseInfo) {
		
		//Everytime this request thread gets in to the loop it should re-check for the appropriate INDS
		while(!isAppropriateINDSAvailable(inDesignRequestResponseInfo)){
			
			//while appropriate and available server is not found, wait
			try {
				inDesignRequestResponseInfo.waitingForINDS();
				this.wait(60000);
				inDesignRequestResponseInfo.notifiedForINDS();
			} 
			catch (InterruptedException e) {
			}
		}
				
		removeThisINDSFromFreeServerList(inDesignRequestResponseInfo.getInDesignServerInstance());
		addThisINDSToOccupiedServerList(inDesignRequestResponseInfo.getInDesignServerInstance());
		
		try {
			InDesignServerListLoader.marshal(allServerList, new File(serverListXmlPath));
		} catch (Throwable e) {
			loadBalancerLogger.debug("Error Saving ServerList to disk");
			loadBalancerLogger.error(e);
		}
		checkINDSOpenFileListSanity();
	}

	
	protected synchronized boolean isAppropriateINDSAvailable(InDesignRequestResponseInfo inDesignRequestResponseInfo) {
		
		// if the file is opened and corresponding server is available, set it and return true, so that the file can quickly use this server
		InDesignServerInstance inDesignServerInstance = getINDSForThisFile(inDesignRequestResponseInfo.getMamFileID());
		
		if(inDesignServerInstance!=null) {
			if(inDesignServerInstance.status.equals(InDesignServerInstanceStatus.FREE)) {
				inDesignRequestResponseInfo.gotINDS(inDesignServerInstance, false);
				return true;
			} 
			// if the file is opened but corresponding server is occupied, set it to null and return false, so that no other server can get this file
			else {
				return false;
			}
		}
		
		else {
			if (inDesignRequestResponseInfo.isExportRequest()) {
				// if the file is not opened and a server is available, set it and return true, so that the file can quickly use this server
				inDesignServerInstance = getFirstFreeExportINDS();
			} else { 
				// if the file is not opened and a server is available, set it and return true, so that the file can quickly use this server
				inDesignServerInstance = getFirstFreeINDS();
			}
			if(inDesignServerInstance!=null) {
				inDesignRequestResponseInfo.gotINDS(inDesignServerInstance, true);
				return true;
			}
			else {
				return false;
			}
		}
			
		
	}
	
	
	protected void sendRequestToINDS(InDesignRequestResponseInfo inDesignRequestResponseInfo) throws ConnectionException, ResponseTimeoutException, Throwable {
		
		HTTPClient client = new HTTPClient();
		inDesignRequestResponseInfo.sendingToINDS();
		String responseData = null;
		try {
			responseData = client.sendAndReceiveXML(inDesignRequestResponseInfo.getInDesignServerInstance().url, inDesignRequestResponseInfo.getRequestData());
		} 
		catch (Throwable e) {
			if(e instanceof ConnectionException) {
				throw (ConnectionException) e;
			} 
			else if(e instanceof ResponseTimeoutException) {
				throw (ResponseTimeoutException)e;
			}
			else {
				throw e;
			}
		}
		inDesignRequestResponseInfo.receivedFromINDS(responseData);
	}
	
	
	protected synchronized void freeUpAndProcessResponseFromINDS(InDesignRequestResponseInfo inDesignRequestResponseInfo) {
		
		InDesignServerInstance inDesignServerInstance = inDesignRequestResponseInfo.getInDesignServerInstance();
		inDesignRequestResponseInfo.processResponseFromINDS();
		freeUpAssignedINDS(inDesignServerInstance);
	}
	
	
	protected synchronized void freeUpAndProcessErrorInRequestProcessing(InDesignRequestResponseInfo inDesignRequestResponseInfo, String errorMessage) {
		
		InDesignServerInstance inDesignServerInstance = inDesignRequestResponseInfo.getInDesignServerInstance();
		inDesignRequestResponseInfo.processErrorInRequestProcessing(errorMessage);
		freeUpAssignedINDS(inDesignServerInstance);
	}
	
	
	protected synchronized void dontFreeUpAndProcessINDSBusyResponseFromINDS(InDesignRequestResponseInfo inDesignRequestResponseInfo, String errorMessage) {
		
		InDesignServerInstance inDesignServerInstance = inDesignRequestResponseInfo.getInDesignServerInstance();
		inDesignRequestResponseInfo.processINDSBusyResponseFromINDS(errorMessage);
		addThisINDSToOccupiedServerListWithStatusRetry(inDesignServerInstance);
	}
	
	
	protected synchronized void dontFreeUpAndProcessErrorSendingRequestToINDS(InDesignRequestResponseInfo inDesignRequestResponseInfo, String errorMessage) {
		
		InDesignServerInstance inDesignServerInstance = inDesignRequestResponseInfo.getInDesignServerInstance();
		inDesignRequestResponseInfo.processErrorInRequestProcessing(errorMessage);
		addThisINDSToOccupiedServerListWithStatusRetry(inDesignServerInstance);
	}
	
	
	protected synchronized void processNoINDSAvailable(InDesignRequestResponseInfo inDesignRequestResponseInfo) {
		
		inDesignRequestResponseInfo.processNoINDSAvailableResponse();
	}
	
	
	protected synchronized void freeUpAssignedINDS(InDesignServerInstance inDesignServerInstance) {
		
		removeThisINDSFromOccupiedServerList(inDesignServerInstance);
		addThisINDSToFreeServerList(inDesignServerInstance);
		try {
			InDesignServerListLoader.marshal(allServerList, new File(serverListXmlPath));
		} catch (Throwable e) {
			loadBalancerLogger.debug("Error Saving ServerList to disk");
			loadBalancerLogger.error(e);
		}
		checkINDSOpenFileListSanity();
		this.notifyAll();
	}
	
	/*This is kept for later in case we need to ever remove an INDS in the list
	protected synchronized void removeUnavailableINDS(InDesignServerInstance inDesignServerInstance, Throwable e) {
		
		removeThisINDSFromOccupiedServerList(inDesignServerInstance);
		removeThisINDSFromFreeServerList(inDesignServerInstance);
		//reset its open file list, so that if load balancer crashes at any point, this is removed
		inDesignServerInstance.openFileList = new LinkedHashSet<String>();
	}
	*/
	
	protected synchronized boolean isAnyINDSIsAlive() {
		
		if(freeServerList.size()==0 && occupiedInDesignServersList.size()==0) {
			loadBalancerLogger.debug("No INDS are currently available");
			return false;
		}
		else {
			return true;
		}
	}
	
	
	protected synchronized boolean isAnyFreeINDSAvailable() {
		
		if(freeServerList.size()==0) {
			return false;
		}
		else {
			return true;
		}
	}
	
	
	protected synchronized boolean isThisINDSInInFreeOrOccupiedList(InDesignServerInstance inDesignServerInstance) {
		
		if(!(freeServerList.contains(inDesignServerInstance) || occupiedInDesignServersList.contains(inDesignServerInstance))) {
			return false;
		}
		else {
			return true;
		}
	}
	
	
	protected synchronized void addThisINDSToFreeServerList(InDesignServerInstance inDesignServerInstance) {
		
		if(!freeServerList.contains(inDesignServerInstance)) {
			
			freeServerList.add(inDesignServerInstance);
			Collections.sort(freeServerList);
		}
		
	}
	
	
	protected synchronized void addThisINDSToOccupiedServerList(InDesignServerInstance inDesignServerInstance) {
		
		if(!occupiedInDesignServersList.contains(inDesignServerInstance)) {
			
			occupiedInDesignServersList.add(inDesignServerInstance);
		}
		
	}
	
	
	protected synchronized void addThisINDSToOccupiedServerListWithStatusRetry(InDesignServerInstance inDesignServerInstance) {
		
		if(!occupiedInDesignServersList.contains(inDesignServerInstance)) {
			
			occupiedInDesignServersList.add(inDesignServerInstance);
		}
		
	}
	
	
	protected synchronized void removeThisINDSFromFreeServerList(InDesignServerInstance inDesignServerInstance) {
		
		freeServerList.remove(inDesignServerInstance);
	}
	
	
	protected synchronized void removeThisINDSFromOccupiedServerList(InDesignServerInstance inDesignServerInstance) {
		
		occupiedInDesignServersList.remove(inDesignServerInstance);
	}
	
	
	protected synchronized InDesignServerInstance getINDSForThisFile(String mamFileID) {
		
		Iterator<InDesignServerInstance> iterator = allServerList.iterator();
		while(iterator.hasNext()) {
			InDesignServerInstance inDesignServerInstance = iterator.next();
			if(inDesignServerInstance.hasThisFileOpen(mamFileID)) {
				return inDesignServerInstance;
			}
		}
		return null;
	}
	
	
	protected synchronized InDesignServerInstance getFirstFreeINDS() {
		if(isAnyFreeINDSAvailable()){
			Iterator<InDesignServerInstance> iterator = freeServerList.iterator();
			while(iterator.hasNext()) {
				InDesignServerInstance inDesignServerInstance = iterator.next();
				if(!inDesignServerInstance.isExportInstance) {
					return inDesignServerInstance;
				}
			}
		}
		return null;
	}
	
	
	protected synchronized InDesignServerInstance getFirstFreeExportINDS() {
		if(isAnyFreeINDSAvailable()){
			Iterator<InDesignServerInstance> iterator = freeServerList.iterator();
			while(iterator.hasNext()) {
				InDesignServerInstance inDesignServerInstance = iterator.next();
				if(inDesignServerInstance.isExportInstance) {
					return inDesignServerInstance;
				}
			}
		}
		return null;
	}
	
	 
	protected synchronized void checkINDSOpenFileListSanity() {
		
		openFilesLogger.debug("*************************Starting checkINDSOpenFileListSanity************************");
		openFilesLogger.debug("Free Servers ->" + freeServerList.toString());
		openFilesLogger.debug("Occupied Servers ->" + occupiedInDesignServersList.toString());
		openFilesLogger.debug("All Servers ->" + allServerList.toString());
		openFilesLogger.debug("*************************Starting checkINDSOpenFileListSanity************************");
		Iterator<InDesignServerInstance> iterator1 = allServerList.iterator();
		while(iterator1.hasNext()) {

			InDesignServerInstance inDesignServerInstanceFrom = iterator1.next();
			Iterator<InDesignServerInstance> iterator2 = allServerList.iterator();
			while (iterator2.hasNext()) {
				InDesignServerInstance inDesignServerInstanceTo = iterator2.next();
				if(!inDesignServerInstanceFrom.url.equals(inDesignServerInstanceTo.url)) {
					LinkedHashSet<String> commonFiles = new LinkedHashSet<String>();
					commonFiles.addAll(inDesignServerInstanceFrom.getOpenFileListSnapshot());
					commonFiles.retainAll(inDesignServerInstanceTo.getOpenFileListSnapshot());
					
					if(commonFiles.size()>0) {
						idlkLogger.debug("!!!!!!!!!!!!!!!!Shit Starts!!!!!!!!!!!!!!!!");
						idlkLogger.debug("Occupied Server Status ->" + occupiedInDesignServersList.toString());
						idlkLogger.debug("Free Server Status ->" + freeServerList.toString());
						idlkLogger.debug("Common files between " + inDesignServerInstanceFrom.url 
												+ " and " + inDesignServerInstanceTo.url
												+ " are " + commonFiles);
						idlkLogger.debug("!!!!!!!!!!!!!!!!Shit Ends!!!!!!!!!!!!!!!!");
					}
					
				}
				
			}
		}
	}

}
