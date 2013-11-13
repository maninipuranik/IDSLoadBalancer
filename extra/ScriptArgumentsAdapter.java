package com.cs.loadbalancer.indesign.bo.extra;

import java.util.LinkedHashMap;

import javax.xml.bind.annotation.adapters.XmlAdapter;

public class ScriptArgumentsAdapter extends
		XmlAdapter<ScriptArguments, LinkedHashMap<String, ScriptArgument>> {

	@Override
	public LinkedHashMap<String, ScriptArgument> unmarshal(
			ScriptArguments scriptArguments) {
		LinkedHashMap<String, ScriptArgument> scriptArgumentMap = new LinkedHashMap<String, ScriptArgument>();
		for (ScriptArgument arg : scriptArguments.scriptArguments)
			scriptArgumentMap.put(arg.name, arg);
		return scriptArgumentMap;
	}

	@Override
	public ScriptArguments marshal(
			LinkedHashMap<String, ScriptArgument> scriptArgumentMap) {
		ScriptArguments scriptArguments = new ScriptArguments();
		scriptArguments.scriptArguments = scriptArgumentMap.values().toArray(
				new ScriptArgument[scriptArgumentMap.size()]);
		return scriptArguments;
	}

}
