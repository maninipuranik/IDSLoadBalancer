package com.cs.loadbalancer.indesign.bo;

import java.util.ArrayList;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "indesignServerInstanceList")
public class InDesignServerInstanceList {

	@XmlElement(name = "indesignServerInstance", type = InDesignServerInstance.class)
	private ArrayList<InDesignServerInstance> inDesignServerInstanceList = new ArrayList<InDesignServerInstance>();

	public InDesignServerInstanceList() {
	}

	public ArrayList<InDesignServerInstance> getInDesignServerInstanceList() {
		return inDesignServerInstanceList;
	}

	public void setInDesignServerInstanceList(
			ArrayList<InDesignServerInstance> inDesignServerInstanceList) {
		this.inDesignServerInstanceList = inDesignServerInstanceList;
	}

	public InDesignServerInstanceList(ArrayList<InDesignServerInstance> inDesignServerInstanceList) {
		this.inDesignServerInstanceList = inDesignServerInstanceList;
	}

}
