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
	private static DefaultLoggerImpl indsAliveLogger = new DefaultLoggerImpl("indsAliveLogger");
	
	private static String serverListXmlPath = "res/indesignservers.xml";
	
	public ArrayList<InDesignServerInstance> freeServerList = new ArrayList<InDesignServerInstance>();
	public ArrayList<InDesignServerInstance> occupiedInDesignServersList = new ArrayList<InDesignServerInstance>();
	public ArrayList<InDesignServerInstance> allServerList = new ArrayList<InDesignServerInstance>();
	
	protected String errorResponseWhenNoINDSIsAvailable = "";
	protected String errorResponseWhenRequestResponseCouldNotBeProcessed = "";
	protected String errorResponseWhenINDSBusy = "";
	
	
	public DefaultInDesignLoadBalancerImpl() throws Throwable {
		super();
		loadBalancerLogger.debug("*************************Starting loadBalancerLogger************************");
		idlkLogger.debug("*************************Starting idlkLogger************************");
		openFilesLogger.debug("*************************Starting openFilesLogger************************");
		allServerList = InDesignServerListLoader.unmarshal(new File(serverListXmlPath));
		setErrorResponses();
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
					sendRequestToInDesignServer(inDesignRequestResponseInfo);
					inDesignRequestResponseInfo.processFileResponseFromINDS();
					
					// Note : this particular call freeUpAssignedINDS goes to a synchronized method
					freeUpAssignedINDS(inDesignServerInstance);
				} 
				catch (Throwable e) {
					// Dont reset the open file list even if the server is down keep this server in the retry list
					indsAliveLogger.error("DefaultInDesignLoadBalancerImpl.performTimedActivity()->"+ e.getMessage());
					loadBalancerLogger.error(e);
					loadBalancerLogger.debug("Server, " + inDesignServerInstance.url + " unavailable, hence marking it for retry");
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
		
		return processSimpleXMLRequest(inDesignRequestResponseInfo);
		
	}
	
	public String processSimpleXMLRequest(InDesignRequestResponseInfo inDesignRequestResponseInfo) {
		
		if(isAnyINDSIsAlive()) {
			
			//We should check if its a file request or a general request
			//If its a file request, we should look for an appropriate server as this file could be locked on some server
			if(inDesignRequestResponseInfo.isFileRequest()) {
				//wait for appropriate INDS
				assignInDesignServerBasedOnFile(inDesignRequestResponseInfo);
			} 
			//If its not a file request, we should simply send it to any available server
			else {
				//wait for any available INDS
				assignAnyAvailableServer(inDesignRequestResponseInfo);
			}
			
			//send the request to the assigned InDesignServer
			try {
				
				/*System.out.println("Sending Request to " + inDesignRequestResponseInfo.getInDesignServerInstance().url);
				try {
					Thread.sleep(20000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}*/
				sendRequestToInDesignServer(inDesignRequestResponseInfo);
				//If the sending/receiving of response was successful
				//If its a file request, we should free up the INDS but also synchronize the open file list on that server from its response
				if(inDesignRequestResponseInfo.isFileRequest()) {
					//process response from INDS and free it up
					freeUpAndProcessFileResponseFromINDS(inDesignRequestResponseInfo);
				} 
				//If its not, we should simply free up the INDS
				else {
					freeUpAndProcessNormalResponseFromINDS(inDesignRequestResponseInfo);
				}
			} 
			catch (Throwable e) {
				
				indsAliveLogger.error("DefaultInDesignLoadBalancerImpl.processSimpleXMLRequest()->"+ e.getMessage());
				loadBalancerLogger.error(e);
				if(e instanceof ResponseTimeoutException) {
					// Dont reset the open file list even if the server is down keep this server in the retry list
					dontFreeUpAndProcessINDSBusyResponseFromINDS(inDesignRequestResponseInfo, e.getMessage());
					return errorResponseWhenINDSBusy;
				}
				else if (e instanceof ConnectionException && inDesignRequestResponseInfo.isNewServerAssigned()) {
					//internally free up this file from open file list
					dontFreeUpAndProcessErrorSendingRequestToINDS(inDesignRequestResponseInfo, e.getMessage());
					//making a recursive call until...
					try {
						Thread.sleep(30000);
					} catch (InterruptedException e1) {
					}
					processSimpleXMLRequest(inDesignRequestResponseInfo);
				} 
				else {
					freeUpAndProcessErrorInRequestProcessing(inDesignRequestResponseInfo, e.getMessage());
					return errorResponseWhenRequestResponseCouldNotBeProcessed;
				}
			} 
			
			return inDesignRequestResponseInfo.getResponseData();
		}
		else {
			inDesignRequestResponseInfo.noINDSAvailableSendingErrorToWebserver();
			return errorResponseWhenNoINDSIsAvailable;
		}
	}
	
	protected void setErrorResponses() {

		try {
			errorResponseWhenNoINDSIsAvailable = FileUtils.readFileToString(new File("res/response/errorResponseWhenNoINDSIsAvailable.xml"), "UTF-8");
		} catch (IOException e) {
			loadBalancerLogger.error(e);
		}
		
		try {
			errorResponseWhenRequestResponseCouldNotBeProcessed = FileUtils.readFileToString(new File("res/response/errorResponseWhenRequestResponseCouldNotBeProcessed.xml"), "UTF-8");
		} catch (IOException e) {
			loadBalancerLogger.error(e);
		}
		
		try {
			errorResponseWhenINDSBusy = FileUtils.readFileToString(new File("res/response/errorResponseWhenINDSBusy.xml"), "UTF-8");
		} catch (IOException e) {
			loadBalancerLogger.error(e);
		}
	}
	
	
	protected synchronized void assignInDesignServerBasedOnFile(InDesignRequestResponseInfo inDesignRequestResponseInfo) {
		
		//Everytime this request thread gets in to the loop it should re-check for the appropriate INDS
		while(!isAppropriateInDesignServerInstanceForThisFileAvailable(inDesignRequestResponseInfo)){
			
			//while appropriate and available server is not found, wait
			try {
				inDesignRequestResponseInfo.waitingForINDS();
				this.wait();
			} 
			catch (InterruptedException e) {
			}
		}
		assignINDS(inDesignRequestResponseInfo);
	}
	
	protected synchronized boolean isAppropriateInDesignServerInstanceForThisFileAvailable(InDesignRequestResponseInfo inDesignRequestResponseInfo) {
		
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
			
			// if the file is not opened and a server is available, set it and return true, so that the file can quickly use this server
			inDesignServerInstance = getFirstFreeINDS();
			if(inDesignServerInstance!=null) {
				inDesignRequestResponseInfo.gotINDS(inDesignServerInstance, true);
				return true;
			}
			else {
				return false;
			}
		}
			
		
	}
	
	protected synchronized void assignAnyAvailableServer(InDesignRequestResponseInfo inDesignRequestResponseInfo) {
		
		while(!isAnyFreeINDSAvailable()) {
			
			try {
				this.wait();
			} 
			catch (InterruptedException e) {
			}
		}
		InDesignServerInstance inDesignServerInstance = getFirstFreeINDS();
		inDesignRequestResponseInfo.gotINDS(inDesignServerInstance, true);
		assignINDS(inDesignRequestResponseInfo);
		
	}
	
	protected synchronized void assignINDS(InDesignRequestResponseInfo inDesignRequestResponseInfo) {
		
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
		
	
	protected void sendRequestToInDesignServer(InDesignRequestResponseInfo inDesignRequestResponseInfo) throws ConnectionException, ResponseTimeoutException, Throwable {
		
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
	
	
	protected synchronized void freeUpAndProcessFileResponseFromINDS(InDesignRequestResponseInfo inDesignRequestResponseInfo) {
		
		inDesignRequestResponseInfo.processFileResponseFromINDS();
		freeUpAssignedINDS(inDesignRequestResponseInfo.getInDesignServerInstance());
	}
	
	protected synchronized void freeUpAndProcessNormalResponseFromINDS(InDesignRequestResponseInfo inDesignRequestResponseInfo) {
		
		inDesignRequestResponseInfo.processNormalResponseFromINDS();
		freeUpAssignedINDS(inDesignRequestResponseInfo.getInDesignServerInstance());
	}
	
	protected synchronized void freeUpAndProcessErrorInRequestProcessing(InDesignRequestResponseInfo inDesignRequestResponseInfo, String errorMessage) {
		
		inDesignRequestResponseInfo.processErrorInRequestProcessing(errorMessage);
		freeUpAssignedINDS(inDesignRequestResponseInfo.getInDesignServerInstance());
	}
	
	protected synchronized void dontFreeUpAndProcessINDSBusyResponseFromINDS(InDesignRequestResponseInfo inDesignRequestResponseInfo, String errorMessage) {
		
		inDesignRequestResponseInfo.processINDSBusyResponseFromINDS(errorMessage);
		addThisINDSToOccupiedServerListWithStatusRetry(inDesignRequestResponseInfo.getInDesignServerInstance());
	}
	
	protected synchronized void dontFreeUpAndProcessErrorSendingRequestToINDS(InDesignRequestResponseInfo inDesignRequestResponseInfo, String errorMessage) {
		
		inDesignRequestResponseInfo.processErrorSendingRequestToINDSGettingNewINDS(errorMessage);
		addThisINDSToOccupiedServerListWithStatusRetry(inDesignRequestResponseInfo.getInDesignServerInstance());
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
	
	protected synchronized void removeUnavailableINDS(InDesignServerInstance inDesignServerInstance, Throwable e) {
		
		removeThisINDSFromOccupiedServerList(inDesignServerInstance);
		removeThisINDSFromFreeServerList(inDesignServerInstance);
		//reset its open file list, so that if load balancer crashes at any point, this is removed
		inDesignServerInstance.openFileList = new LinkedHashSet<String>();
	}
	
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
		
		inDesignServerInstance.status = InDesignServerInstanceStatus.FREE;
	}
	
	protected synchronized void addThisINDSToOccupiedServerList(InDesignServerInstance inDesignServerInstance) {
		
		if(!occupiedInDesignServersList.contains(inDesignServerInstance)) {
			
			occupiedInDesignServersList.add(inDesignServerInstance);
		}
		
		inDesignServerInstance.status = InDesignServerInstanceStatus.OCCUPIED;
	}
	
	protected synchronized void addThisINDSToOccupiedServerListWithStatusRetry(InDesignServerInstance inDesignServerInstance) {
		
		if(!occupiedInDesignServersList.contains(inDesignServerInstance)) {
			
			occupiedInDesignServersList.add(inDesignServerInstance);
		}
		
		inDesignServerInstance.status = InDesignServerInstanceStatus.IN_RETRY;
	}
	
	protected synchronized void removeThisINDSFromFreeServerList(InDesignServerInstance inDesignServerInstance) {
		
		freeServerList.remove(inDesignServerInstance);
		inDesignServerInstance.status = InDesignServerInstanceStatus.INTERIM;
	}
	
	protected synchronized void removeThisINDSFromOccupiedServerList(InDesignServerInstance inDesignServerInstance) {
		
		occupiedInDesignServersList.remove(inDesignServerInstance);
		inDesignServerInstance.status = InDesignServerInstanceStatus.INTERIM;
	}
	
	protected synchronized InDesignServerInstance getINDSForThisFile(String mamFileID) {
		
		Iterator<InDesignServerInstance> iterator = allServerList.iterator();
		while(iterator.hasNext()) {
			InDesignServerInstance inDesignServerInstance = iterator.next();
			if(inDesignServerInstance.openFileList.contains(mamFileID)) {
				return inDesignServerInstance;
			}
		}
		return null;
	}
	
	protected synchronized InDesignServerInstance getFirstFreeINDS() {
		
		if(isAnyFreeINDSAvailable()){
			return freeServerList.get(0);
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
					commonFiles.addAll(inDesignServerInstanceFrom.openFileList);
					commonFiles.retainAll(inDesignServerInstanceTo.openFileList);
					
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
