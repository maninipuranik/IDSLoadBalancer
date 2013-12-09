package com.cs.loadbalancer.indesign.bo;

import java.io.Serializable;
import java.util.LinkedHashSet;

import com.cs.loadbalancer.indesign.helpers.DefaultLoggerImpl;

public class InDesignServerInstance implements Serializable, Comparable<InDesignServerInstance> {

	private static final long serialVersionUID = 1L;
	private static DefaultLoggerImpl indsLogger = new DefaultLoggerImpl("indsLogger");

	public String url;
	public String pathToAdmin;
	public transient InDesignServerInstanceStatus status = InDesignServerInstanceStatus.IN_RETRY;
	public LinkedHashSet<String> openFileList = new LinkedHashSet<String>();
	

	public void inFreeMode(String mamFileID) {
		
		status = InDesignServerInstanceStatus.FREE;
		log(mamFileID);
	}
	
	public void inOccupiedMode(String mamFileID) {
		
		status = InDesignServerInstanceStatus.OCCUPIED;
		log(mamFileID);
	}
	
	public void inRetryMode(String mamFileID) {
		
		status = InDesignServerInstanceStatus.IN_RETRY;
		log(mamFileID);
	}
	
	public void allFilesClosed(String mamFileID) {
		
		status = InDesignServerInstanceStatus.IN_RETRY;
		log(mamFileID);
	}
	
	
	public void log(String mamFileID) {

		indsLogger.debug(toLogString() + "->" + mamFileID);
	}

	@Override
	public String toString() {
		return url + "->" + openFileList.toString();
	}
	
	public String toLogString() {
		return url + "->" + status + "->" + openFileList.toString();
	}
	
	
	
	@Override
	public int compareTo(InDesignServerInstance otherServer) {
		int countOfOther = ((InDesignServerInstance) otherServer).openFileList.size(); 
		return this.openFileList.size() - countOfOther;
	}



	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		
		if(obj==null) {
			
			return false;
		}
		else {
			
			return url.equals(((InDesignServerInstance)obj).url);
		}
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		// TODO Auto-generated method stub
		return url.hashCode();
	}
}
