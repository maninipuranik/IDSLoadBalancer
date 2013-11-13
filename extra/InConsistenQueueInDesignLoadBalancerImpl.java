package com.cs.loadbalancer.indesign.extra;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

import com.cs.loadbalancer.indesign.InDesignLoadBalancer;
import com.cs.loadbalancer.indesign.bo.InDesignRequestResponseInfo;
import com.cs.loadbalancer.indesign.bo.InDesignRequestResponseStatus;
import com.cs.loadbalancer.indesign.bo.InDesignServerInstance;
import com.cs.loadbalancer.indesign.helpers.Log4jLoggerImpl;
import com.cs.loadbalancer.indesign.utils.http.HTTPClient;

public class InConsistenQueueInDesignLoadBalancerImpl implements
		InDesignLoadBalancer {

	public ArrayList<InDesignServerInstance> inDesignServerInstanceList = new ArrayList<InDesignServerInstance>();

	public ArrayList<InDesignRequestResponseInfo> ongoingRequestList = new ArrayList<InDesignRequestResponseInfo>();

	public LinkedHashMap<String, InDesignServerInstance> inDesignFileInstanceMapping = new LinkedHashMap<String, InDesignServerInstance>();

	public InConsistenQueueInDesignLoadBalancerImpl(
			ArrayList<InDesignServerInstance> inDesignServerInstanceList) {
		super();
		this.inDesignServerInstanceList = inDesignServerInstanceList;
	}

	@Override
	public String processSimpleXMLRequest(String requestData) {

		InDesignRequestResponseInfo inDesignRequestResponseInfo = null;
		try {

			inDesignRequestResponseInfo = new InDesignRequestResponseInfo(
					requestData);

		} catch (Throwable throwable) {

			throwable.printStackTrace();
		}

		// set all the fields from the requestdocument
		inDesignRequestResponseInfo.inDesignServerInstance = getOrSetInDesignServerInstanceForFile(inDesignRequestResponseInfo.mamFileID);

		Log4jLoggerImpl.log(inDesignRequestResponseInfo);

		// add the request to the queue
		addToOngoingRequestList(inDesignRequestResponseInfo);

		Log4jLoggerImpl.log(inDesignRequestResponseInfo);

		// send the request to the specific InDesignServer
		sendRequestToInDesignServer(inDesignRequestResponseInfo);

		processInDesignServerResponse(inDesignRequestResponseInfo);

		removeFromOngoingRequestList(inDesignRequestResponseInfo);

		return inDesignRequestResponseInfo.responseData;

	}

	protected synchronized void processInDesignServerResponse(
			InDesignRequestResponseInfo inDesignRequestResponseInfo) {

		LinkedHashSet<String> openFilesOnCurrentInstanceAsPerINDS = inDesignRequestResponseInfo.getOpenFilesFromResponse();

		int allOpenfileSize = inDesignFileInstanceMapping.size();
		String[] allOpenFiles = inDesignFileInstanceMapping.keySet().toArray(
				new String[allOpenfileSize]);

		for (int i = 0; i < allOpenfileSize; i++) {

			String currentFile = allOpenFiles[i];
			if (inDesignFileInstanceMapping.get(currentFile).equals(
					inDesignRequestResponseInfo.inDesignServerInstance)
					&& !openFilesOnCurrentInstanceAsPerINDS
							.contains(currentFile)) {
				inDesignFileInstanceMapping.remove(currentFile);
				System.out
						.println("mamFileID->"
								+ currentFile
								+ " released from instance ->"
								+ inDesignRequestResponseInfo.inDesignServerInstance.url);
			}
		}
		print("INDS->" + openFilesOnCurrentInstanceAsPerINDS.toString(),
				inDesignRequestResponseInfo.inDesignServerInstance.url,
				inDesignRequestResponseInfo.mamFileID);
		print("LOAD->" + inDesignFileInstanceMapping.keySet(),
				inDesignRequestResponseInfo.inDesignServerInstance.url,
				inDesignRequestResponseInfo.mamFileID);

		if (!inDesignFileInstanceMapping.keySet().containsAll(
				openFilesOnCurrentInstanceAsPerINDS)) {
			print("!!!!!!!!!!More Files open on INDS Ends!!!!!!!!!!!",
					inDesignRequestResponseInfo.inDesignServerInstance.url,
					inDesignRequestResponseInfo.mamFileID);
		} else {
		}

	}

	protected synchronized InDesignServerInstance getOrSetInDesignServerInstanceForFile(
			String mamFileID) {

		boolean isAssigned = false;
		if (!inDesignFileInstanceMapping.containsKey(mamFileID)) {
			InDesignServerInstance inDesignServerInstance = inDesignServerInstanceList
					.get(0);
			inDesignFileInstanceMapping.put(mamFileID, inDesignServerInstance);
			isAssigned = true;
		}

		InDesignServerInstance inDesignServerInstance = inDesignFileInstanceMapping
				.get(mamFileID);

		if (isAssigned) {
			print("assigned", inDesignServerInstance.url, mamFileID);
		} else {
			print("re-assigned", inDesignServerInstance.url, mamFileID);
		}

		return inDesignServerInstance;

	}

	protected synchronized void addToOngoingRequestList(
			InDesignRequestResponseInfo inDesignRequestResponseInfo) {
		ongoingRequestList.add(inDesignRequestResponseInfo);
	}

	protected synchronized void removeFromOngoingRequestList(
			InDesignRequestResponseInfo inDesignRequestResponseInfo) {
		ongoingRequestList.remove(inDesignRequestResponseInfo);
	}

	protected void sendRequestToInDesignServer(
			InDesignRequestResponseInfo inDesignRequestResponseInfo) {
		try {
			inDesignRequestResponseInfo.status = InDesignRequestResponseStatus.INPROGRESS;
			Log4jLoggerImpl.log(inDesignRequestResponseInfo);

			HTTPClient client = new HTTPClient();
			inDesignRequestResponseInfo.responseData = client
					.sendAndReceiveXML(
							inDesignRequestResponseInfo.inDesignServerInstance.url,
							inDesignRequestResponseInfo.requestData);

			inDesignRequestResponseInfo.status = InDesignRequestResponseStatus.SUCCESSFUL;
			Log4jLoggerImpl.log(inDesignRequestResponseInfo);
		} catch (Throwable e) {
			e.printStackTrace();
			inDesignRequestResponseInfo.status = InDesignRequestResponseStatus.ERROR;
			Log4jLoggerImpl.log(inDesignRequestResponseInfo);
		}
	}

	protected void print(String stringToPrint, String url, String mamFileID) {

		System.out.println(Thread.currentThread().getName() + " -> " + url
				+ " -> " + mamFileID + " -> " + stringToPrint);

	}

}
