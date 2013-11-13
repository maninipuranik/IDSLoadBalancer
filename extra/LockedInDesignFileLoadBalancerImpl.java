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

public class LockedInDesignFileLoadBalancerImpl implements InDesignLoadBalancer {

	public ArrayList<InDesignServerInstance> inDesignServerInstanceList = new ArrayList<InDesignServerInstance>();
	public ArrayList<InDesignServerInstance> occupiedInDesignServersList = new ArrayList<InDesignServerInstance>();

	public LinkedHashMap<String, InDesignServerInstance> inDesignFileInstanceMapping = new LinkedHashMap<String, InDesignServerInstance>();

	public LockedInDesignFileLoadBalancerImpl(
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

		Log4jLoggerImpl.log(inDesignRequestResponseInfo);

		// send the request to the specific InDesignServer
		sendRequestToInDesignServer(inDesignRequestResponseInfo);

		processInDesignServerResponse(inDesignRequestResponseInfo);

		return inDesignRequestResponseInfo.responseData;

	}

	protected InDesignServerInstance getOrSetInDesignServerInstanceForFile(
			String mamFileID) {

		InDesignServerInstance inDesignServerInstance = null;

		synchronized (inDesignServerInstanceList) {

			if (!inDesignFileInstanceMapping.containsKey(mamFileID)) {
				int counter = 0;
				while (inDesignServerInstanceList.size() == 0) {
					try {
						if (counter == 0) {
							print("Waiting for any INDS", null, mamFileID);
						}
						inDesignServerInstanceList.wait();
						counter++;
					} catch (InterruptedException e) {
					}
				}

				inDesignServerInstance = inDesignServerInstanceList.get(0);
				inDesignFileInstanceMapping.put(mamFileID,
						inDesignServerInstance);
			}

			else {
				inDesignServerInstance = inDesignFileInstanceMapping
						.get(mamFileID);
				int counter = 0;
				while (!inDesignServerInstanceList
						.contains(inDesignServerInstance)) {
					try {
						if (counter == 0) {
							print("Waiting for my INDS",
									inDesignServerInstance.url, mamFileID);
						}
						inDesignServerInstanceList.wait();
					} catch (InterruptedException e) {
					}
				}
			}
			print("INDS Server Taken", inDesignServerInstance.url, mamFileID);
			inDesignServerInstanceList.remove(inDesignServerInstance);
			occupiedInDesignServersList.add(inDesignServerInstance);
		}

		return inDesignServerInstance;

	}

	protected void processInDesignServerResponse(
			InDesignRequestResponseInfo inDesignRequestResponseInfo) {

		LinkedHashSet<String> openFilesOnCurrentInstanceAsPerINDS = inDesignRequestResponseInfo.getOpenFilesFromResponse();
		synchronized (inDesignServerInstanceList) {

			int allOpenfileSize = inDesignFileInstanceMapping.size();
			String[] allOpenFiles = inDesignFileInstanceMapping.keySet()
					.toArray(new String[allOpenfileSize]);

			for (int i = 0; i < allOpenfileSize; i++) {

				String currentFile = allOpenFiles[i];
				if (inDesignFileInstanceMapping.get(currentFile).equals(
						inDesignRequestResponseInfo.inDesignServerInstance)
						&& !openFilesOnCurrentInstanceAsPerINDS
								.contains(currentFile)) {
					inDesignFileInstanceMapping.remove(currentFile);
				}
			}

			inDesignRequestResponseInfo.inDesignServerInstance.openFileList = openFilesOnCurrentInstanceAsPerINDS;
			print("INDS Server Released",
					inDesignRequestResponseInfo.inDesignServerInstance.url, "");
			occupiedInDesignServersList
					.remove(inDesignRequestResponseInfo.inDesignServerInstance);
			inDesignServerInstanceList
					.add(inDesignRequestResponseInfo.inDesignServerInstance);
			print("Mapping Status", inDesignFileInstanceMapping.toString(), "");
			print("Occupied Server Status",
					occupiedInDesignServersList.toString(), "");
			print("Free Server Status", inDesignServerInstanceList.toString(),
					"");
			inDesignServerInstanceList.notifyAll();
		}
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

		System.out.println(stringToPrint + " -> " + url + " -> " + mamFileID);

	}

}
