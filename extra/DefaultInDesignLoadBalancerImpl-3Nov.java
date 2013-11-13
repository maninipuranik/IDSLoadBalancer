package com.cs.loadbalancer.indesign;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;

import org.apache.commons.io.FileUtils;

import com.cs.loadbalancer.indesign.bo.InDesignRequestResponseInfo;
import com.cs.loadbalancer.indesign.bo.InDesignServerInstance;
import com.cs.loadbalancer.indesign.exception.ErrorProcessingRequestOrResponseException;
import com.cs.loadbalancer.indesign.exception.INDSNotReachableException;
import com.cs.loadbalancer.indesign.exception.NotAbletoCheckINDSServersAvailabilityException;
import com.cs.loadbalancer.indesign.helpers.DefaultLoggerImpl;
import com.cs.loadbalancer.indesign.helpers.InDesignServerListLoader;
import com.cs.loadbalancer.indesign.utils.http.HTTPClient;
import com.cs.loadbalancer.indesign.utils.http.exception.ConnectionException;


/**
 * @author manini
 *
 */
public class DefaultInDesignLoadBalancerImpl 
	implements InDesignLoadBalancer 
{
	private static DefaultLoggerImpl loadBalancerLogger = new DefaultLoggerImpl("loadBalancerLogger");
	private static DefaultLoggerImpl idlkLogger = new DefaultLoggerImpl("idlkLogger");
	private static String serverListXmlPath = "res/indesignservers.xml";
	
	public ArrayList<InDesignServerInstance> freeServerList = new ArrayList<InDesignServerInstance>();
	public ArrayList<InDesignServerInstance> occupiedInDesignServersList = new ArrayList<InDesignServerInstance>();
	public ArrayList<InDesignServerInstance> allServerList = new ArrayList<InDesignServerInstance>();
	
	protected String errorResponseWhenNoINDSIsAvailable = "";
	protected String errorResponseWhenRequestResponseCouldNotBeProcessed = "";
	
	
	public DefaultInDesignLoadBalancerImpl() throws Throwable {
		super();
		loadBalancerLogger.debug("*************************Starting loadBalancerLogger************************" + new Date(System.currentTimeMillis()));
		idlkLogger.debug("*************************Starting idlkLogger************************" + new Date(System.currentTimeMillis()));
		allServerList = InDesignServerListLoader.unmarshal(new File(serverListXmlPath));
		setErrorResponseWhenNoINDSIsAvailable();
		setErrorResponseWhenRequestResponseCouldNotBeProcessed();
		testAndUpdateTheStatusOfAllINDS(true);
	}
	
	public void testAndUpdateTheStatusOfAllINDS(boolean onStartup) {
		
		//TODO need to configure paths as per the load balancer configuration
		String pingRequest = null;
		try {
			pingRequest = FileUtils.readFileToString(new File("res/requests/pingRequest.xml"), "UTF-8");
		} catch (IOException e) {
			loadBalancerLogger.error(e);
			throw new NotAbletoCheckINDSServersAvailabilityException(e);
		}
		
		InDesignRequestResponseInfo inDesignRequestResponseInfo = new InDesignRequestResponseInfo();
		inDesignRequestResponseInfo.pingFromLoadBalancer(pingRequest);
		
		InDesignServerInstance[] allServerArr = allServerList.toArray(new InDesignServerInstance[freeServerList.size()]);
		
		for (int i = 0; i < allServerArr.length; i++) {
			InDesignServerInstance inDesignServerInstance = allServerArr[i];
			
			//if this server is currently not in free or occupied server list, then only take it for testing
			// Note : this particular call isThisINDSInInFreeOrOccupiedList goes to a synchronized method
			if(!isThisINDSInInFreeOrOccupiedList(inDesignServerInstance)) {
			
				inDesignRequestResponseInfo.gotINDS(inDesignServerInstance, true);
				//send the request to the specific InDesignServer
				try {
					sendRequestToInDesignServer(inDesignRequestResponseInfo);
					inDesignRequestResponseInfo.processFileResponseFromINDS();
					
					// Note : this particular call addThisINDSToFreeServerList goes to a synchronized method
					addThisINDSToFreeServerList(inDesignServerInstance);
				} 
				catch (INDSNotReachableException e) {
					//reset its open file list
					inDesignServerInstance.openFileList = new LinkedHashSet<String>();
					loadBalancerLogger.debug("Server, " + inDesignServerInstance.url + " not available because of the following cause, hence removing it.");
					loadBalancerLogger.error(e);
				}
				catch (ErrorProcessingRequestOrResponseException e) {
					//Add this server to the occupied server list
					//Now this seems tricky, but this case will occur only when this method is called on Startup
					if(onStartup) {
						addThisINDSToOccupiedServerList(inDesignServerInstance);
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
		boolean requestSentSuccesfully = false;
		
		//But first we should check if there is any server available
		if(!isAnyINDSIsAlive()) {
			inDesignRequestResponseInfo.noINDSAvailableSendingErrorToWebserver();
			return errorResponseWhenNoINDSIsAvailable;
		}
		
		//We should keep trying to send the request until it is sent succesfully.
		while(!requestSentSuccesfully) {
			
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
				
				System.out.println("Sending Request to " + inDesignRequestResponseInfo.getInDesignServerInstance().url);
				try {
					Thread.sleep(20000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				sendRequestToInDesignServer(inDesignRequestResponseInfo);
				requestSentSuccesfully = true;
				
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
				
			} catch (INDSNotReachableException e) {
				
				//if INDS is not available, then we should remove it from the available/occupied server list
				removeUnavailableINDS(inDesignRequestResponseInfo.getInDesignServerInstance(), e);
				
				if(isAnyINDSIsAlive()) {
					inDesignRequestResponseInfo.iNDSNotAvilableGettingNewINDS();
				} 
				else {
					inDesignRequestResponseInfo.noINDSAvailableSendingErrorToWebserver();
					return errorResponseWhenNoINDSIsAvailable;
				}
				
				
			} catch (ErrorProcessingRequestOrResponseException e) {
				
				//if there was some error processing the request itself, then we should stop the processing of this request, here and now and notify the webserver
				if(inDesignRequestResponseInfo.isFileRequest()) {
					freeUpAndProcessErrorSendingFileRequestToINDS(inDesignRequestResponseInfo, e.getMessage());
				} else {
					freeUpAndProcessErrorSendingNormalRequestToINDS(inDesignRequestResponseInfo, e.getMessage());
				}
				return errorResponseWhenRequestResponseCouldNotBeProcessed;
			}
		}
		return inDesignRequestResponseInfo.getResponseData();
		
	}
	
	protected void setErrorResponseWhenNoINDSIsAvailable() {

		try {
			errorResponseWhenNoINDSIsAvailable = FileUtils.readFileToString(new File("res/response/errorResponseWhenNoINDSIsAvailable.xml"), "UTF-8");
		} catch (IOException e) {
			loadBalancerLogger.error(e);
		}
	}
	
	protected void setErrorResponseWhenRequestResponseCouldNotBeProcessed() {
		 
		try {
			errorResponseWhenRequestResponseCouldNotBeProcessed = FileUtils.readFileToString(new File("res/response/errorResponseWhenRequestResponseCouldNotBeProcessed.xml"), "UTF-8");
		} catch (IOException e) {
			loadBalancerLogger.error(e);
		}
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
	
	protected synchronized boolean isThisINDSInInFreeOrOccupiedList(InDesignServerInstance inDesignServerInstance) {
		
		if(!(freeServerList.contains(inDesignServerInstance) || occupiedInDesignServersList.contains(inDesignServerInstance))) {
			return false;
		}
		else {
			return true;
		}
	}
	
	protected synchronized void addThisINDSToFreeServerList(InDesignServerInstance inDesignServerInstance) {
		
		freeServerList.add(inDesignServerInstance);
	}
	
	protected synchronized void addThisINDSToOccupiedServerList(InDesignServerInstance inDesignServerInstance) {
		
		occupiedInDesignServersList.add(inDesignServerInstance);
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
		
		// if the file is opened but corresponding server is occupied, set it to null and return false, so that no other server can get this file
		int occupiedServerListSize = occupiedInDesignServersList.size();
		for(int i=0; i<occupiedServerListSize; i++) {
			InDesignServerInstance inDesignServerInstance = occupiedInDesignServersList.get(i);
			if(inDesignServerInstance.openFileList.contains(inDesignRequestResponseInfo.getMamFileID())) {
				return false;
			}
		}
			
		// if the file is opened and corresponding server is available, set it and return true, so that the file can quickly use this server
		int availableServerListSize = freeServerList.size();
		for(int i=0; i<availableServerListSize; i++) {
			InDesignServerInstance inDesignServerInstance = freeServerList.get(i);
			if(inDesignServerInstance.openFileList.contains(inDesignRequestResponseInfo.getMamFileID())) {
				inDesignRequestResponseInfo.gotINDS(inDesignServerInstance, false);
				return true;
			}
		}
		
		// if the file is not opened and a server is available, set it and return true, so that the file can quickly use this server
		if(availableServerListSize>0) {
			inDesignRequestResponseInfo.gotINDS(freeServerList.get(0), false);
			return true;
		}
		else {
			return false;
		}
	}
	
	protected synchronized void assignAnyAvailableServer(InDesignRequestResponseInfo inDesignRequestResponseInfo) {
		
		while(freeServerList.size()==0) {
			
			try {
				this.wait();
			} 
			catch (InterruptedException e) {
			}
		}
		inDesignRequestResponseInfo.gotINDS(freeServerList.get(0), true);
		assignINDS(inDesignRequestResponseInfo);
		
	}
	
	protected synchronized void assignINDS(InDesignRequestResponseInfo inDesignRequestResponseInfo) {
		
		freeServerList.remove(inDesignRequestResponseInfo.getInDesignServerInstance());
		occupiedInDesignServersList.add(inDesignRequestResponseInfo.getInDesignServerInstance());
		try {
			InDesignServerListLoader.marshal(allServerList, new File(serverListXmlPath));
		} catch (Throwable e) {
			loadBalancerLogger.debug("Error Saving ServerList to disk");
			loadBalancerLogger.error(e);
		}
		checkINDSOpenFileListSanity();
	}
		
	
	protected void sendRequestToInDesignServer(InDesignRequestResponseInfo inDesignRequestResponseInfo) throws INDSNotReachableException, ErrorProcessingRequestOrResponseException {
		
		HTTPClient client = new HTTPClient();
		inDesignRequestResponseInfo.sendingToINDS();
		String responseData = null;
		try {
			responseData = client.sendAndReceiveXML(inDesignRequestResponseInfo.getInDesignServerInstance().url, inDesignRequestResponseInfo.getRequestData());
		} 
		catch (Throwable e) {
			if(e instanceof ConnectionException){
				throw new INDSNotReachableException(e);
			} else {
				throw new ErrorProcessingRequestOrResponseException(e);
			}
		}
		inDesignRequestResponseInfo.receivedFromINDS(responseData);
	}
	
	
	protected synchronized void freeUpAndProcessFileResponseFromINDS(InDesignRequestResponseInfo inDesignRequestResponseInfo) {
		
		inDesignRequestResponseInfo.processFileResponseFromINDS();
		freeUpAssignedINDS(inDesignRequestResponseInfo.getInDesignServerInstance());
	}
	
	protected synchronized void freeUpAndProcessErrorSendingFileRequestToINDS(InDesignRequestResponseInfo inDesignRequestResponseInfo, String errorMessage) {
		
		inDesignRequestResponseInfo.processErrorSendingFileRequestToINDS(errorMessage);
		freeUpAssignedINDS(inDesignRequestResponseInfo.getInDesignServerInstance());
	}
	
	protected synchronized void freeUpAndProcessNormalResponseFromINDS(InDesignRequestResponseInfo inDesignRequestResponseInfo) {
		
		inDesignRequestResponseInfo.processNormalResponseFromINDS();
		freeUpAssignedINDS(inDesignRequestResponseInfo.getInDesignServerInstance());
	}
	
	protected synchronized void freeUpAndProcessErrorSendingNormalRequestToINDS(InDesignRequestResponseInfo inDesignRequestResponseInfo, String errorMessage) {
		
		inDesignRequestResponseInfo.processErrorSendingNormalRequestToINDS(errorMessage);
		freeUpAssignedINDS(inDesignRequestResponseInfo.getInDesignServerInstance());
	}
	
	protected synchronized void freeUpAssignedINDS(InDesignServerInstance inDesignServerInstance) {
		
		occupiedInDesignServersList.remove(inDesignServerInstance);
		freeServerList.add(inDesignServerInstance);
		Collections.sort(freeServerList);
		
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
		
		occupiedInDesignServersList.remove(inDesignServerInstance);
		freeServerList.remove(inDesignServerInstance);
		//reset its open file list, so that if load balancer crashes at any point, this is removed
		inDesignServerInstance.openFileList = new LinkedHashSet<String>();
	}
	
	protected synchronized void checkINDSOpenFileListSanity() {
		
		int allServerListSize = allServerList.size();
		for(int i=0; i<allServerListSize; i++) {
			InDesignServerInstance inDesignServerInstanceFrom = allServerList.get(i);
			for (int j=0; j<allServerListSize; j++) {
				InDesignServerInstance inDesignServerInstanceTo = allServerList.get(j);
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
