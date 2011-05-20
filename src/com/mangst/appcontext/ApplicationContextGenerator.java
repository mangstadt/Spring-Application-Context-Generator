package com.mangst.appcontext;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

//Spring Application Context Generator
//Generates the XML for a Spring application context file by looking at the source code of the classes you want to inject. 
//http://static.springsource.org/spring/docs/2.5.x/reference/beans.html
//http://stackoverflow.com/questions/6060475/spring-xml-from-existing-beans-how
public class ApplicationContextGenerator {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		Arguments arguments = new Arguments(args);
		List<String> errors = new ArrayList<String>();

		//display help message
		if (arguments.exists("h", "--help")) {
			System.out.println("Spring Application Context Generator");
			System.out.println("by Michael Angstadt - github.com/mangstadt");
			System.out.println();
			System.out.println("Generates a Spring bean XML application context file using a specified list");
			System.out.println("of Java classes.  Looks at the public fields and setter methods.");
			System.out.println();
			System.out.println("Arguments");
			System.out.println("-s=PATH, --source=PATH (required)");
			System.out.println("   The directory that the Java source code is located in.");
			System.out.println("-p=NAME, --package=NAME (required)");
			System.out.println("   All public classes in the specified packages will be added to the bean");
			System.out.println("   definition file. Use this parameter multiple times to specify");
			System.out.println("   multiple packages.");
			System.out.println("-v=N, --springVersion=N");
			System.out.println("   The version of Spring you are using");
			System.out.println("   (defaults to \"2.5\")");
			System.out.println("-h, --help");
			System.out.println("   Displays this help message.");
			System.exit(0);
		}

		//get the source directory
		String source = arguments.value("s", "source");
		if (source == null) {
			errors.add("The source directory must be specified (example: \"--source=path/to/src\").");
		}

		//get the Spring version
		String springVersion = arguments.value("v", "--springVersion", "2.5");

		//get the packages
		Collection<String> packages = arguments.valueList("p", "package");
		if (packages == null) {
			errors.add("At least one package must be specified (example: \"--package=com.example\").  Use a blank value for the default package (example: \"--package=\").");
		}

		//display an error message if any of the required fields were not specified
		if (!errors.isEmpty()) {
			for (String error : errors) {
				System.err.println(error);
			}
			System.err.println("Type \"--help\" for help.");
			System.exit(1);
		}

		File sourceDir = new File(source);
		File packageDirs[] = new File[packages.size()];
		int i = 0;
		for (String packageStr : packages) {
			packageStr = packageStr.replaceAll("\\.", File.separator);
			packageDirs[i] = new File(sourceDir, packageStr);
		}

		//generate the application context XML
		ApplicationContextGenerator generator = new ApplicationContextGenerator(springVersion);
		JavaFileFilter javaFileFilter = new JavaFileFilter();
		for (File directory : packageDirs) {
			File files[] = directory.listFiles(javaFileFilter);

			//iterate over each file
			for (File file : files) {
				generator.processSourceFile(new FileReader(file));
			}
		}
		Document document = generator.getDocument();

		//output the XML
		String xmlString;
		{
			TransformerFactory transfac = TransformerFactory.newInstance();
			Transformer trans = transfac.newTransformer();
			trans.setOutputProperty(OutputKeys.INDENT, "yes");
			StringWriter sw = new StringWriter();
			StreamResult result = new StreamResult(sw);
			DOMSource domSource = new DOMSource(document);
			trans.transform(domSource, result);
			xmlString = sw.toString();
		}
		System.out.println(xmlString);
	}
	
	/**
	 * A file filter that only returns .java files.
	 * @author mangstadt
	 */
	private static class JavaFileFilter implements FileFilter {
		@Override
		public boolean accept(File file) {
			return file.isFile() && file.getName().endsWith(".java");
		}
	}

	private static final Pattern packageRegex = Pattern.compile("^\\s*package\\s+(.*?)\\s*;", Pattern.DOTALL);
	private static final Pattern classNameRegex = Pattern.compile("public\\s+class\\s+(\\w+)");
	private static final Pattern parameterRegex = Pattern.compile("([a-zA-Z_0-9<>\\.]+)\\s+(\\w+)");
	private static final Pattern setterRegex = Pattern.compile("public\\s+\\w+\\s+set(\\w+)\\s*\\(\\s*(\\w+)\\s+\\w+\\s*\\)");
	private static final Pattern publicFieldRegex = Pattern.compile("public\\s+(\\w+)\\s+(\\w+)(\\s*=\\s*(.*?))?;", Pattern.DOTALL);
	private static final List<String> primatives = Arrays.asList(new String[] { "byte", "short", "char", "int", "long", "float", "double", "boolean" });
	private static final List<String> wrappers = Arrays.asList(new String[] { "Byte", "Short", "Character", "Integer", "Long", "Float", "Double", "Boolean", "String" });

	/**
	 * The XML document.
	 */
	private Document document;

	/**
	 * The XML root element.
	 */
	private Element root;

	/**
	 * Constructs a new application context generator.
	 * @param springVersion the Spring version
	 */
	public ApplicationContextGenerator(String springVersion) {
		//create the XML document
		DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = null;
		try {
			docBuilder = dbfac.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			//never thrown in my case, so ignore it
		}
		document = docBuilder.newDocument();
		
		//create the root element
		root = document.createElementNS("http://www.springframework.org/schema/beans", "beans");
		root.setAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "schemaLocation", "http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-" + springVersion + ".xsd");
		document.appendChild(root);
	}

	/**
	 * Gets the generated XML document.
	 * @return the XML document
	 */
	public Document getDocument() {
		return document;
	}

	/**
	 * Adds the class contained within the specified file to the application
	 * context.
	 * @param reader the input stream to the file (it is closed)
	 * @return this
	 * @throws IOException if there's a problem reading the file
	 */
	public ApplicationContextGenerator processSourceFile(Reader reader) throws IOException {
		String contentsString = getFileContents(reader);

		//build a "bean" element for each class
		Element beanElement = buildBeanElement(contentsString);
		if (beanElement != null) {
			root.appendChild(beanElement);
		} else {
			//System.err.println("Warning: Could not find public class in \"" + file + "\".");
		}
		return this;
	}

	/**
	 * Gets the entire contents of a text file.
	 * @param reader the input stream to the file
	 * @return the entire context of the file
	 * @throws IOException if there's a problem reading the file
	 */
	private String getFileContents(Reader reader) throws IOException {
		BufferedReader in = null;
		try {
			in = new BufferedReader(reader);
			StringBuilder sb = new StringBuilder();
			char buffer[] = new char[1024];
			int len;
			while ((len = in.read(buffer)) != -1) {
				sb.append(buffer, 0, len);
			}
			return sb.toString();
		} finally {
			if (in != null) in.close();
		}
	}

	private Element buildBeanElement(String contentsString) {
		//TODO: look at constructor too
		//if there is a default constructor, don't generate <constructor-args>
		//if there is not a default constructor and exactly one non-default construct, generate <constructor-args>
		//if there is not a default constrcutor and more than one non-default constructor, leave a comment in the XML and output a warning?

		//get the name of the package
		String packageName = null;
		Matcher matcher = packageRegex.matcher(contentsString);
		if (matcher.find()) {
			packageName = matcher.group(1);
		}

		//get the name of the class
		String className = null;
		matcher = classNameRegex.matcher(contentsString);
		if (matcher.find()) {
			className = matcher.group(1);
		} else {
			return null;
		}

		//create <bean /> element
		Element beanElement = document.createElement("bean");
		String classNameLower = className.substring(0, 1).toLowerCase() + className.substring(1);
		beanElement.setAttribute("id", classNameLower);
		if (packageName != null) {
			//if it's not in the default package
			beanElement.setAttribute("class", packageName + "." + className);
		}

		//create <constructor-arg /> elements
		Pattern constructorRegex = Pattern.compile("public\\s+" + className + "\\s+\\((.*?)\\)");
		matcher = constructorRegex.matcher(contentsString);
		List<String> constructors = new ArrayList<String>();
		boolean defaultConstructor = false;
		while (matcher.find()) {
			String parameters = matcher.group(1).trim();
			if (parameters.isEmpty()) {
				//there is a default constructor, so we won't create <constructor-arg /> elements
				defaultConstructor = true;
				break;
			}
			constructors.add(parameters);
		}
		if (!defaultConstructor && constructors.size() == 1) {
			//if there is only one constructor and that constructor is not a default constructor, then generate the <constructor-arg /> elements
			//TODO take into account the fact that the parameter types might be fully-qualified
			matcher = parameterRegex.matcher(constructors.get(0));
			int index = 0;
			while (matcher.find()) {
				String type = matcher.group(1);
				String name = matcher.group(2);

				Element constructorArgElement = document.createElement("constructor-arg");

				if (wrappers.contains(type) || primatives.contains(type)) {
					if (wrappers.contains(type)) {
						type = "java.lang." + type;
					}
					constructorArgElement.setAttribute("type", type);
					constructorArgElement.setAttribute("value", "");
				} else {
					constructorArgElement.setAttribute("ref", name);
				}

				constructorArgElement.setAttribute("index", index + "");
				index++;

				beanElement.appendChild(constructorArgElement);
			}
		}

		//generate <property /> elements from public fields
		matcher = publicFieldRegex.matcher(contentsString);
		while (matcher.find()) {
			String type = matcher.group(1);
			String name = matcher.group(2);
			String value = matcher.group(4);

			Element propertyElement = document.createElement("property");
			propertyElement.setAttribute("name", name);
			if (primatives.contains(type) || wrappers.contains(type)) {
				propertyElement.setAttribute("value", value);
			} else {
				String typeLower = type.substring(0, 1).toLowerCase() + type.substring(1);
				propertyElement.setAttribute("ref", typeLower);
			}
			beanElement.appendChild(propertyElement);
		}

		//generate <property /> elements from setters
		matcher = setterRegex.matcher(contentsString);
		while (matcher.find()) {
			String name = matcher.group(1);
			String type = matcher.group(2);

			Element propertyElement = document.createElement("property");
			name = name.substring(0, 1).toLowerCase() + name.substring(1);
			propertyElement.setAttribute("name", name);
			if (primatives.contains(type) || wrappers.contains(type)) {
				propertyElement.setAttribute("value", "");
			} else {
				String typeLower = type.substring(0, 1).toLowerCase() + type.substring(1);
				propertyElement.setAttribute("ref", typeLower);
			}
			beanElement.appendChild(propertyElement);
		}

		return beanElement;
	}
}
