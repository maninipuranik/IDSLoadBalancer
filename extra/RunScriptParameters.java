package com.cs.loadbalancer.indesign.bo.extra;

import java.util.LinkedHashMap;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "runScriptParameters")
public class RunScriptParameters {

	@XmlElement
	@XmlJavaTypeAdapter(ScriptArgumentsAdapter.class)
	public LinkedHashMap<String, ScriptArgument> scriptArgumentsMap;
}
