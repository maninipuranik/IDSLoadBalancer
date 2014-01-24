package com.cs.loadbalancer.indesign.helpers;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.LinkedHashSet;
import java.util.ArrayList;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import com.cs.loadbalancer.indesign.bo.InDesignServerInstance;
import com.cs.loadbalancer.indesign.bo.InDesignServerInstanceList;

public class InDesignServerListLoader {

	public static ArrayList<InDesignServerInstance> unmarshal(File importFile) throws Throwable {
		
		InDesignServerInstanceList inDesignServerInstanceList = new InDesignServerInstanceList();
		JAXBContext context = JAXBContext.newInstance(InDesignServerInstanceList.class);
		Unmarshaller um = context.createUnmarshaller();
		inDesignServerInstanceList = (InDesignServerInstanceList) um.unmarshal(importFile);
		return inDesignServerInstanceList.getInDesignServerInstanceList();
	}

	public static void marshal(ArrayList<InDesignServerInstance> inDesignServerInstanceList, File selectedFile) throws Throwable {
		
		JAXBContext context;
		BufferedWriter writer = null;
		writer = new BufferedWriter(new FileWriter(selectedFile));
		context = JAXBContext.newInstance(InDesignServerInstanceList.class);
		Marshaller m = context.createMarshaller();
		m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
		m.marshal(new InDesignServerInstanceList(inDesignServerInstanceList), writer);
		writer.close();
	}

	public static void main(String[] args) throws Throwable {

		ArrayList<InDesignServerInstance> inDesignServerInstanceList = new ArrayList<InDesignServerInstance>();

		InDesignServerInstance inDesignServerInstance1 = new InDesignServerInstance();
		LinkedHashSet<String> openFileList1 = new LinkedHashSet<String>();
		openFileList1.add("11");
		openFileList1.add("12");
		openFileList1.add("13");
		openFileList1.add("14");
		openFileList1.add("15");
		inDesignServerInstance1.url = "http://localhost:18385";
		inDesignServerInstance1.setOpenFileList(openFileList1);
		inDesignServerInstanceList.add(inDesignServerInstance1);

		InDesignServerInstance inDesignServerInstance2 = new InDesignServerInstance();
		LinkedHashSet<String> openFileList2 = new LinkedHashSet<String>();
		openFileList2.add("21");
		openFileList2.add("22");
		openFileList2.add("23");
		openFileList2.add("24");
		openFileList2.add("25");
		inDesignServerInstance2.url = "http://localhost:18386";
		inDesignServerInstance2.setOpenFileList(openFileList2);
		inDesignServerInstanceList.add(inDesignServerInstance2);

		InDesignServerInstance inDesignServerInstance3 = new InDesignServerInstance();
		LinkedHashSet<String> openFileList3 = new LinkedHashSet<String>();
		openFileList3.add("31");
		openFileList3.add("32");
		openFileList3.add("33");
		openFileList3.add("34");
		openFileList3.add("35");
		inDesignServerInstance3.url = "http://localhost:18387";
		inDesignServerInstance3.setOpenFileList(openFileList3);
		inDesignServerInstanceList.add(inDesignServerInstance3);

		InDesignServerInstance inDesignServerInstance4 = new InDesignServerInstance();
		LinkedHashSet<String> openFileList4 = new LinkedHashSet<String>();
		openFileList4.add("41");
		openFileList4.add("42");
		openFileList4.add("43");
		openFileList4.add("44");
		openFileList4.add("45");
		inDesignServerInstance4.url = "http://localhost:18388";
		inDesignServerInstance4.setOpenFileList(openFileList4);
		inDesignServerInstanceList.add(inDesignServerInstance4);

		InDesignServerInstance inDesignServerInstance5 = new InDesignServerInstance();
		LinkedHashSet<String> openFileList5 = new LinkedHashSet<String>();
		openFileList5.add("51");
		openFileList5.add("52");
		openFileList5.add("53");
		openFileList5.add("54");
		openFileList5.add("55");
		inDesignServerInstance5.url = "http://localhost:18389";
		inDesignServerInstance5.setOpenFileList(openFileList5);
		inDesignServerInstanceList.add(inDesignServerInstance5);

		marshal(inDesignServerInstanceList, new File("res/indesignservers.xml"));
	}

}
