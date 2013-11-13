package com.cs.loadbalancer.indesign.extra;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;

import com.cs.loadbalancer.indesign.InDesignLoadBalancer;
import com.cs.loadbalancer.indesign.bo.InDesignRequestResponseInfo;
import com.cs.loadbalancer.indesign.bo.InDesignRequestResponseStatus;
import com.cs.loadbalancer.indesign.bo.InDesignServerInstance;
import com.cs.loadbalancer.indesign.helpers.Log4jLoggerImpl;
import com.cs.loadbalancer.indesign.utils.http.HTTPClient;


public class DefaultInDesignLoadBalancerImpl22Oct 
	implements InDesignLoadBalancer 
{
	
	public ArrayList<InDesignServerInstance> availableServerList = new ArrayList<InDesignServerInstance>();
	public ArrayList<InDesignServerInstance> occupiedInDesignServersList = new ArrayList<InDesignServerInstance>();
	
	
	public DefaultInDesignLoadBalancerImpl22Oct(ArrayList<InDesignServerInstance> inDesignServerInstanceList) 
	{
		super();
		this.availableServerList.addAll(inDesignServerInstanceList);
	}
	
	
	@Override
	public String processSimpleXMLRequest(String requestData)
	{
		InDesignRequestResponseInfo inDesignRequestResponseInfo = null;
		try {
			inDesignRequestResponseInfo = new InDesignRequestResponseInfo(requestData);
			Long.parseLong(inDesignRequestResponseInfo.mamFileID);
		} 
		catch(NumberFormatException ex){
			
			getAnyAvailableServer(inDesignRequestResponseInfo);
			sendRequestToInDesignServer(inDesignRequestResponseInfo);
		}
		catch(Throwable throwable) {
			
			throwable.printStackTrace();
		}
		
		
		//wait for appropriate INDS
		assignAppropriateInDesignServer(inDesignRequestResponseInfo);
		
		//send the request to the specific InDesignServer
		sendRequestToInDesignServer(inDesignRequestResponseInfo);
		
		//process response from INDS and free it up
		processInDesignServerResponse(inDesignRequestResponseInfo);
		
		return inDesignRequestResponseInfo.responseData;
		
	}
	
	
	protected synchronized void assignAppropriateInDesignServer(InDesignRequestResponseInfo inDesignRequestResponseInfo)
	{
		//Everytime this request thread gets in to the loop it should re-check for the appropriate INDS
		while(!isAppropriateInDesignServerInstanceForThisFileAvailable(inDesignRequestResponseInfo)){
			
			//while appropriate and available server is not found, wait
			try {
				this.wait();
			} 
			catch (InterruptedException e) {
			}
		}
		
		inDesignRequestResponseInfo.inDesignServerInstance.openFileList.add(inDesignRequestResponseInfo.mamFileID);
		availableServerList.remove(inDesignRequestResponseInfo.inDesignServerInstance);
		occupiedInDesignServersList.add(inDesignRequestResponseInfo.inDesignServerInstance);
		
		checkINDSOpenFileListSanity(inDesignRequestResponseInfo.mamFileID, inDesignRequestResponseInfo.inDesignServerInstance.url, true);
	}
		
	protected synchronized boolean isAppropriateInDesignServerInstanceForThisFileAvailable(InDesignRequestResponseInfo inDesignRequestResponseInfo) {
		
		// if the file is opened but corresponding server is occupied, set it to null and return false, so that no other server can get this file
		int occupiedServerListSize = occupiedInDesignServersList.size();
		for(int i=0; i<occupiedServerListSize; i++) {
			InDesignServerInstance inDesignServerInstance = occupiedInDesignServersList.get(i);
			if(inDesignServerInstance.openFileList.contains(inDesignRequestResponseInfo.mamFileID)) {
				inDesignRequestResponseInfo.inDesignServerInstance = null;
				return false;
			}
		}
			
		// if the file is opened and corresponding server is available, set it and return true, so that the file can quickly use this server
		int availableServerListSize = availableServerList.size();
		for(int i=0; i<availableServerListSize; i++) {
			InDesignServerInstance inDesignServerInstance = availableServerList.get(i);
			if(inDesignServerInstance.openFileList.contains(inDesignRequestResponseInfo.mamFileID)) {
				inDesignRequestResponseInfo.isNewServerAssigned = false;
				inDesignRequestResponseInfo.inDesignServerInstance = inDesignServerInstance;
				return true;
			}
		}
		
		// if the file is not opened and a server is available, set it and return true, so that the file can quickly use this server
		if(availableServerListSize>0) {
			inDesignRequestResponseInfo.isNewServerAssigned = true;
			inDesignRequestResponseInfo.inDesignServerInstance = availableServerList.get(0);
			return true;
		}
		else {
			inDesignRequestResponseInfo.inDesignServerInstance = null;
			return false;
		}
	}
	
	protected synchronized void getAnyAvailableServer(InDesignRequestResponseInfo inDesignRequestResponseInfo) {
		
		while(availableServerList.size()==0) {
			
			try {
				this.wait();
			} 
			catch (InterruptedException e) {
			}
		}
		inDesignRequestResponseInfo.inDesignServerInstance = availableServerList.get(0);
		inDesignRequestResponseInfo.isNewServerAssigned = true;
		
		checkINDSOpenFileListSanity(inDesignRequestResponseInfo.mamFileID, "", true);
	}
	
	
	protected void sendRequestToInDesignServer(InDesignRequestResponseInfo inDesignRequestResponseInfo)
	{
		try {
			inDesignRequestResponseInfo.status = InDesignRequestResponseStatus.INPROGRESS;
			Log4jLoggerImpl.log(inDesignRequestResponseInfo);
			
			HTTPClient client = new HTTPClient();
			inDesignRequestResponseInfo.responseData = client.sendAndReceiveXML(inDesignRequestResponseInfo.inDesignServerInstance.url, 
																					inDesignRequestResponseInfo.requestData);
			
			inDesignRequestResponseInfo.status = InDesignRequestResponseStatus.SUCCESSFUL;
			Log4jLoggerImpl.log(inDesignRequestResponseInfo);
		} 
		catch (Throwable e) {
			//TODO what should be done on error received from INDS, crash etc
			e.printStackTrace();
			inDesignRequestResponseInfo.status = InDesignRequestResponseStatus.ERROR;
			Log4jLoggerImpl.log(inDesignRequestResponseInfo);
		}
	}
	
	
	protected synchronized void processInDesignServerResponse(InDesignRequestResponseInfo inDesignRequestResponseInfo)
	{
		
		inDesignRequestResponseInfo.inDesignServerInstance.openFileList= inDesignRequestResponseInfo.getOpenFilesFromResponse();;
		occupiedInDesignServersList.remove(inDesignRequestResponseInfo.inDesignServerInstance);
		availableServerList.add(inDesignRequestResponseInfo.inDesignServerInstance);
		Collections.sort(availableServerList);
		
		checkINDSOpenFileListSanity(inDesignRequestResponseInfo.mamFileID, inDesignRequestResponseInfo.inDesignServerInstance.url, false);
		this.notifyAll();
	}

	
	protected synchronized void checkINDSOpenFileListSanity(String mamFileID, String server,  boolean isRequest)
	{
		if(isRequest) {
			System.out.println("Request Came in From File " + mamFileID + " for Server " + server);
		} else {
			System.out.println("Response Came in For File " + mamFileID + " from Server " + server);
		}
			
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
						System.out.println("!!!!!!!!!!!!!!!!Shit Starts!!!!!!!!!!!!!!!!");
						System.out.println("Occupied Server Status ->" + occupiedInDesignServersList.toString());
						System.out.println("Free Server Status ->" + availableServerList.toString());
						System.out.println("Common files between " + inDesignServerInstanceFrom.url 
												+ " and " + inDesignServerInstanceTo.url
												+ " are " + commonFiles);
						System.out.println("!!!!!!!!!!!!!!!!Shit Ends!!!!!!!!!!!!!!!!");
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
						System.out.println("!!!!!!!!!!!!!!!!Shit Starts!!!!!!!!!!!!!!!!");
						System.out.println("Occupied Server Status ->" + occupiedInDesignServersList.toString());
						System.out.println("Free Server Status ->" + availableServerList.toString());
						System.out.println("Common files between " + inDesignServerInstanceFrom.url 
												+ " and " + inDesignServerInstanceTo.url
												+ " are " + commonFiles.toString());
						System.out.println("!!!!!!!!!!!!!!!!Shit Ends!!!!!!!!!!!!!!!!");
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
						System.out.println("!!!!!!!!!!!!!!!!Shit Starts!!!!!!!!!!!!!!!!");
						System.out.println("Occupied Server Status ->" + occupiedInDesignServersList.toString());
						System.out.println("Free Server Status ->" + availableServerList.toString());
						System.out.println("Common files between " + inDesignServerInstanceFrom.url 
												+ " and " + inDesignServerInstanceTo.url
												+ " are " + commonFiles);
						System.out.println("!!!!!!!!!!!!!!!!Shit Ends!!!!!!!!!!!!!!!!");
						isShit = true;
					}
					
				}
				
			}
		}
		
		if(!isShit){
			System.out.println("And...... We are Clean Starts!!!");
			System.out.println("Occupied Server Status ->" + occupiedInDesignServersList.toString());
			System.out.println("Free Server Status ->" + availableServerList.toString());
			System.out.println("And...... We are Clean Ends!!!");
		}
	}

}
