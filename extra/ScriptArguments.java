package com.cs.loadbalancer.indesign.bo.extra;

import javax.xml.bind.annotation.XmlElement;

public class ScriptArguments {

	@XmlElement(name = "scriptArgs")
	public ScriptArgument[] scriptArguments;

}
