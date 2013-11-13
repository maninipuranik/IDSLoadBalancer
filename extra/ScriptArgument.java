package com.cs.loadbalancer.indesign.bo.extra;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

public class ScriptArgument {

	@XmlJavaTypeAdapter(AdapterCDATA.class)
	public String name;

	@XmlJavaTypeAdapter(AdapterCDATA.class)
	public String value;
}
