package com.cs.loadbalancer.indesign.bo;

import java.io.Serializable;
import java.util.LinkedHashSet;

public class InDesignServerInstance implements Serializable, Comparable<InDesignServerInstance> {

	private static final long serialVersionUID = 1L;

	public String url;
	public String pathToAdmin;
	public transient InDesignServerInstanceStatus status = InDesignServerInstanceStatus.IN_RETRY;
	public LinkedHashSet<String> openFileList = new LinkedHashSet<String>();

	@Override
	public String toString() {
		return url + "->" + openFileList.toString();
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
