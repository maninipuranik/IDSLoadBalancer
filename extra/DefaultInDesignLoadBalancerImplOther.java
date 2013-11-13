package com.cs.loadbalancer.indesign;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;

import org.apache.commons.io.FileUtils;

import com.cs.loadbalancer.indesign.bo.InDesignRequestResponseInfo;
import com.cs.loadbalancer.indesign.bo.InDesignServerInstance;
import com.cs.loadbalancer.indesign.exception.INDSNotReachableException;
import com.cs.loadbalancer.indesign.exception.INDSRequestProcessingException;
import com.cs.loadbalancer.indesign.exception.NoINDSServersAvailableException;
import com.cs.loadbalancer.indesign.helpers.DefaultLoggerImpl;
import com.cs.loadbalancer.indesign.utils.http.HTTPClient;


/**
 * @author manini
 *
 */
public class DefaultInDesignLoadBalancerImpl 
	implements InDesignLoadBalancer 
{
	private static DefaultLoggerImpl balancerLogger = new DefaultLoggerImpl(InDesignLoadBalancer.class.getName());
	public ArrayList<InDesignServerInstance> availableServerList = new ArrayList<InDesignServerInstance>();
	public ArrayList<InDesignServerInstance> occupiedInDesignServersList = new ArrayList<InDesignServerInstance>();
	
	
	public DefaultInDesignLoadBalancerImpl(ArrayList<InDesignServerInstance> inDesignServerInstanceList) throws NoINDSServersAvailableException 
	{
		super();
		this.availableServerList.addAll(inDesignServerInstanceList);
		try {
			synchronizeWithAllINDS();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	@Override
	public String processSimpleXMLRequest(String requestData)
	{
		InDesignRequestResponseInfo inDesignRequestResponseInfo = new InDesignRequestResponseInfo();
		inDesignRequestResponseInfo.receivedFromWebServer(requestData);
		
		if(inDesignRequestResponseInfo.isFileRequest()) {
			//wait for appropriate INDS
			assignAppropriateInDesignServer(inDesignRequestResponseInfo);
		} 
		else {
			//wait for any available INDS
			assignAnyAvailableServer(inDesignRequestResponseInfo);
		}
		
		//send the request to the specific InDesignServer
		try {
			sendRequestToInDesignServer(inDesignRequestResponseInfo);
			
			if(inDesignRequestResponseInfo.isFileRequest()) {
				//process response from INDS and free it up
				freeUpAndProcessResponseFromAppropriateINDS(inDesignRequestResponseInfo);
			} else {
				freeUpAssignedINDS(inDesignRequestResponseInfo);
			}
			
		} catch (INDSRequestProcessingException e) {
			balancerLogger.error(e);
			
			if(inDesignRequestResponseInfo.isFileRequest()) {
				//process error from INDS and free it up
				freeUpAndProcessErrorFromAppropriateINDS(inDesignRequestResponseInfo, e.getMessage());
			} else {
				freeUpAssignedINDS(inDesignRequestResponseInfo);
			}
			//TODO ask peter what to send
			return e.getMessage();
		}
		
		return inDesignRequestResponseInfo.getResponseData();
		
	}
	
	protected synchronized void synchronizeWithAllINDS() throws IOException, NoINDSServersAvailableException {
	
		//TODO need to configure paths as per the load balancer configuration
		String pingRequest = FileUtils.readFileToString(new File("res/requests/ping-request.xml"), "UTF-8");
		InDesignRequestResponseInfo inDesignRequestResponseInfo = new InDesignRequestResponseInfo();
		inDesignRequestResponseInfo.pingFromLoadBalancer(pingRequest);
		
		InDesignServerInstance[] availableServers = availableServerList.toArray(new InDesignServerInstance[availableServerList.size()]);
		
		for (int i = 0; i < availableServers.length; i++) {
			InDesignServerInstance inDesignServerInstance = availableServers[i];
			inDesignRequestResponseInfo.gotINDS(inDesignServerInstance, true);
			
			//send the request to the specific InDesignServer
			try {
				checkIfServerIsReachable(inDesignRequestResponseInfo);
			} catch (INDSNotReachableException e) {
				//Log Error and remove from InDesign Server List
				balancerLogger.debug("Server, " + inDesignServerInstance.url + " not available because of the following cause, hence removing it.");
				balancerLogger.error(e);
				availableServerList.remove(inDesignServerInstance);
			}
			
			//TODO inspect the response to check if plugins are installed!!!
		}
		
		
		if(availableServerList.size()==0) {
			throw new NoINDSServersAvailableException();
		}
		
	}
	
	
	protected void checkIfServerIsReachable(InDesignRequestResponseInfo inDesignRequestResponseInfo) throws INDSNotReachableException {
		
		try {
			sendRequestToInDesignServer(inDesignRequestResponseInfo);
			inDesignRequestResponseInfo.processResponseFromINDS();
		} catch (INDSRequestProcessingException e) {
			throw new INDSNotReachableException(e);
		}
	}
	
	
	protected synchronized void assignAppropriateInDesignServer(InDesignRequestResponseInfo inDesignRequestResponseInfo)
	{
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
	
	protected synchronized void assignAnyAvailableServer(InDesignRequestResponseInfo inDesignRequestResponseInfo) {
		
		while(availableServerList.size()==0) {
			
			try {
				this.wait();
			} 
			catch (InterruptedException e) {
			}
		}
		inDesignRequestResponseInfo.gotINDS(availableServerList.get(0), true);
		assignINDS(inDesignRequestResponseInfo);
		
	}
	
	protected synchronized void assignINDS(InDesignRequestResponseInfo inDesignRequestResponseInfo) {
		
		availableServerList.remove(inDesignRequestResponseInfo.getInDesignServerInstance());
		occupiedInDesignServersList.add(inDesignRequestResponseInfo.getInDesignServerInstance());
		
		checkINDSOpenFileListSanity();
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
		int availableServerListSize = availableServerList.size();
		for(int i=0; i<availableServerListSize; i++) {
			InDesignServerInstance inDesignServerInstance = availableServerList.get(i);
			if(inDesignServerInstance.openFileList.contains(inDesignRequestResponseInfo.getMamFileID())) {
				inDesignRequestResponseInfo.gotINDS(inDesignServerInstance, false);
				return true;
			}
		}
		
		// if the file is not opened and a server is available, set it and return true, so that the file can quickly use this server
		if(availableServerListSize>0) {
			inDesignRequestResponseInfo.gotINDS(availableServerList.get(0), false);
			return true;
		}
		else {
			return false;
		}
	}
	
	
	protected void sendRequestToInDesignServer(InDesignRequestResponseInfo inDesignRequestResponseInfo) throws INDSRequestProcessingException
	{
		HTTPClient client = new HTTPClient();
		inDesignRequestResponseInfo.sendingToINDS();
		String responseData = null;
		try {
			responseData = client.sendAndReceiveXML(inDesignRequestResponseInfo.getInDesignServerInstance().url, inDesignRequestResponseInfo.getRequestData());
		} 
		catch (Throwable e) {
			throw new INDSRequestProcessingException(e);
		}
		inDesignRequestResponseInfo.receivedFromINDS(responseData);
	}
	
	
	protected synchronized void freeUpAndProcessResponseFromAppropriateINDS(InDesignRequestResponseInfo inDesignRequestResponseInfo)
	{
		inDesignRequestResponseInfo.processResponseFromINDS();
		freeUpAssignedINDS(inDesignRequestResponseInfo);
	}
	
	protected synchronized void freeUpAndProcessErrorFromAppropriateINDS(InDesignRequestResponseInfo inDesignRequestResponseInfo, String errorMessage)
	{
		inDesignRequestResponseInfo.processErrorFromINDS(errorMessage);
		freeUpAssignedINDS(inDesignRequestResponseInfo);
	}
	
	protected synchronized void freeUpAssignedINDS(InDesignRequestResponseInfo inDesignRequestResponseInfo)
	{
		occupiedInDesignServersList.remove(inDesignRequestResponseInfo.getInDesignServerInstance());
		availableServerList.add(inDesignRequestResponseInfo.getInDesignServerInstance());
		Collections.sort(availableServerList);
		
		checkINDSOpenFileListSanity();
		this.notifyAll();
	}

	
	protected synchronized void checkINDSOpenFileListSanity()
	{
		int availableServerListSize = availableServerList.size();
		int occupiedServerListSize = occupiedInDesignServersList.size();
		boolean isShit = false;
		for(int i=0; i<availableServerListSize; i++) {
			InDesignServerInstance inDesignServerInstanceFrom = availableServerList.get(i);
			for (int j=0; j<availableServerListSize; j++) {
				InDesignServerInstance inDesignServerInstanceTo = availableServerList.get(j);
				if(!inDesignServerInstanceFrom.url.equals(inDesignServerInstanceTo.url)) {
					LinkedHashSet<String> commonFiles = new LinkedHashSet<String>();
					commonFiles.addAll(inDesignServerInstanceFrom.openFileList);
					commonFiles.retainAll(inDesignServerInstanceTo.openFileList);
					
					if(commonFiles.size()>0) {
						balancerLogger.debug("!!!!!!!!!!!!!!!!Shit Starts!!!!!!!!!!!!!!!!");
						balancerLogger.debug("Occupied Server Status ->" + occupiedInDesignServersList.toString());
						balancerLogger.debug("Free Server Status ->" + availableServerList.toString());
						balancerLogger.debug("Common files between " + inDesignServerInstanceFrom.url 
												+ " and " + inDesignServerInstanceTo.url
												+ " are " + commonFiles);
						balancerLogger.debug("!!!!!!!!!!!!!!!!Shit Ends!!!!!!!!!!!!!!!!");
						isShit = true;
					}
					
				}
				
			}
			for (int j=0; j<occupiedServerListSize; j++) {
				InDesignServerInstance inDesignServerInstanceTo = occupiedInDesignServersList.get(j);
				
				if(!inDesignServerInstanceFrom.url.equals(inDesignServerInstanceTo.url)) {
					LinkedHashSet<String> commonFiles = new LinkedHashSet<String>();
					commonFiles.addAll(inDesignServerInstanceFrom.openFileList);
					commonFiles.retainAll(inDesignServerInstanceTo.openFileList);
					
					if(commonFiles.size()>0) {
						balancerLogger.debug("!!!!!!!!!!!!!!!!Shit Starts!!!!!!!!!!!!!!!!");
						balancerLogger.debug("Occupied Server Status ->" + occupiedInDesignServersList.toString());
						balancerLogger.debug("Free Server Status ->" + availableServerList.toString());
						balancerLogger.debug("Common files between " + inDesignServerInstanceFrom.url 
												+ " and " + inDesignServerInstanceTo.url
												+ " are " + commonFiles);
						balancerLogger.debug("!!!!!!!!!!!!!!!!Shit Ends!!!!!!!!!!!!!!!!");
						isShit = true;
					}
				}
			}
		}
		
		for(int i=0; i<occupiedServerListSize; i++) {
			InDesignServerInstance inDesignServerInstanceFrom = occupiedInDesignServersList.get(i);
			for (int j=0; j<occupiedServerListSize; j++) {
				
				InDesignServerInstance inDesignServerInstanceTo = occupiedInDesignServersList.get(j);
					
				if(!inDesignServerInstanceFrom.url.equals(inDesignServerInstanceTo.url)) {
					LinkedHashSet<String> commonFiles = new LinkedHashSet<String>();
					commonFiles.addAll(inDesignServerInstanceFrom.openFileList);
					commonFiles.retainAll(inDesignServerInstanceTo.openFileList);
					
					
					if(commonFiles.size()>0) {
						balancerLogger.debug("!!!!!!!!!!!!!!!!Shit Starts!!!!!!!!!!!!!!!!");
						balancerLogger.debug("Occupied Server Status ->" + occupiedInDesignServersList.toString());
						balancerLogger.debug("Free Server Status ->" + availableServerList.toString());
						balancerLogger.debug("Common files between " + inDesignServerInstanceFrom.url 
												+ " and " + inDesignServerInstanceTo.url
												+ " are " + commonFiles);
						balancerLogger.debug("!!!!!!!!!!!!!!!!Shit Ends!!!!!!!!!!!!!!!!");
						isShit = true;
					}
					
				}
				
			}
		}
		
		if(!isShit){
			/*System.out.println("Alles Gute Starts");
			System.out.println("Occupied Server Status ->" + occupiedInDesignServersList.toString());
			System.out.println("Free Server Status ->" + availableServerList.toString());
			System.out.println("Alles Gute Ends");*/
		}
	}

}
