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
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
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

//http://static.springsource.org/spring/docs/2.5.x/reference/beans.html
//http://stackoverflow.com/questions/6060475/spring-xml-from-existing-beans-how
//TODO support public fields like "public int a, b, c;"
//TODO refactor regex searches into classes so you can do regex.getType() regex.getName(), implement Iterator ?
//TODO what if: "class Foo{ public class Bar {} }"
//TODO handle arrays ("public String[] strs;", "public void setFoo(String[] foo){}"
//TODO ignore comments!
/**
 * Spring Application Context Generator.<br>
 * Generates the bean definitions for a Spring XML application context file from
 * the source code of Java classes.
 * @author mangst
 */
public class ApplicationContextGenerator {
	/**
	 * Runs this utility from the command line.
	 * @param args the command line arguments
	 */
	public static void main(String[] args) throws Exception {
		Arguments arguments = new Arguments(args);

		//display help message
		if (arguments.exists("h", "--help")) {
			System.out.println("Spring Application Context Generator");
			System.out.println("by Michael Angstadt - github.com/mangstadt");
			System.out.println();
			System.out.println("Generates the bean definitions for a Spring XML application context file from");
			System.out.println("the source code of Java classes.");
			System.out.println("It creates:");
			System.out.println(" * A <bean /> element for each public class");
			System.out.println(" * A <property /> element for each public field and public setter method.");
			System.out.println(" * A list of <constructor-arg /> elements if (1) there is only one constructor");
			System.out.println("   and (2) that constructor is not the default constructor.");
			System.out.println();
			System.out.println("Example");
			System.out.println("java com.mangst.appcontext.ApplicationContextGenerator \\");
			System.out.println(" --source=path/to/src \\");
			System.out.println(" --package=com.example.foo --package=com.example.bar");
			System.out.println();
			System.out.println("Arguments");
			System.out.println("-s=PATH, --source=PATH (required)");
			System.out.println("   The directory that the Java source code is located in.");
			System.out.println("-p=NAME, --package=NAME (required)");
			System.out.println("   All public classes in the specified packages will be added to the bean");
			System.out.println("   definition file. Use this parameter multiple times to specify. Use a blank");
			System.out.println("   value for the default package (\"-p=\")");
			System.out.println("   multiple packages.");
			System.out.println("-v=N, --springVersion=N");
			System.out.println("   The version of Spring you are using (for specifying the XML schema).");
			System.out.println("   (defaults to \"2.5\")");
			System.out.println("-r, --recurse");
			System.out.println("   Recurse into sub-packages (example: specifying \"-r -p=com.foo\" will also");
			System.out.println("   include \"com.foo.bar\").");
			System.out.println("-h, --help");
			System.out.println("   Displays this help message.");
			System.exit(0);
		}

		List<String> errors = new ArrayList<String>();

		//get the source directory
		String source = arguments.value("s", "source");
		if (source == null) {
			errors.add("The source directory must be specified (example: \"--source=path/to/src\").");
		}

		//get the Spring version
		String springVersion = arguments.value("v", "--springVersion", "2.5");

		//get the packages
		Collection<String> packages = arguments.valueList("p", "package");
		if (packages.isEmpty()) {
			errors.add("At least one package must be specified (example: \"--package=com.example\").  Use a blank value for the default package (example: \"--package=\").");
		}

		//recurse into sub-packages?
		boolean recurse = arguments.exists("r", "recurse");

		//display an error message if any of the required fields were not specified
		if (!errors.isEmpty()) {
			for (String error : errors) {
				System.err.println(error);
			}
			System.err.println("Type \"--help\" for help.");
			System.exit(1);
		}

		//create File objects for each package
		File sourceDir = new File(source);
		Queue<File> packageDirs = new LinkedList<File>();
		for (String packageStr : packages) {
			if (packageStr == null) {
				packageStr = "";
			}
			packageStr = packageStr.replaceAll("\\.", File.separator);
			packageDirs.add(new File(sourceDir, packageStr));
		}

		//generate the application context XML
		ApplicationContextGenerator generator = new ApplicationContextGenerator(springVersion);
		JavaFileFilter javaFileFilter = new JavaFileFilter();
		while (!packageDirs.isEmpty()) {
			File directory = packageDirs.poll();
			File files[] = directory.listFiles(javaFileFilter);

			//iterate over each file
			for (File file : files) {
				if (recurse && file.isDirectory()) {
					//if recurse is on, append this directory to the list of packages
					packageDirs.add(file);
				} else if (file.isFile()) {
					generator.addBean(new FileReader(file));
				}
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
	 * A file filter that only returns directories and .java files.
	 * @author mangstadt
	 */
	private static class JavaFileFilter implements FileFilter {
		@Override
		public boolean accept(File file) {
			//ignore SVN directories
			if (file.isDirectory() && !file.getName().equals(".svn")) {
				return true;
			}

			//include all Java files
			if (file.getName().endsWith(".java")) {
				return true;
			}

			return false;
		}
	}

	/**
	 * Regex that is used to find the class' package.
	 */
	private static final Pattern packageRegex = Pattern.compile("^\\s*package\\s+(.*?)\\s*;", Pattern.DOTALL);

	/**
	 * Regex that is used to find the class' name.
	 */
	private static final Pattern classNameRegex = Pattern.compile("public\\s+class\\s+(\\w+)");

	/**
	 * Regex that is used to pull parameters out of a method's parameter list.
	 */
	private static final Pattern parameterRegex = Pattern.compile("([a-zA-Z_0-9<>\\.]+)\\s+(\\w+)");

	/**
	 * Regex that is used to find a class' setter methods.
	 */
	private static final Pattern setterRegex = Pattern.compile("public\\s+\\w+\\s+set(\\w+)\\s*\\(\\s*([a-zA-Z_0-9\\.]+)\\s+\\w+\\s*\\)");

	/**
	 * Regex that is used to find a class' public fields.
	 */
	private static final Pattern publicFieldRegex = Pattern.compile("public\\s+([a-zA-Z_0-9\\.]+)\\s+(\\w+)(\\s*=\\s*(.*?))?;", Pattern.DOTALL);

	/**
	 * The list of Java primative types.
	 */
	private static final List<String> primatives = Arrays.asList(new String[] { "byte", "short", "char", "int", "long", "float", "double", "boolean" });

	/**
	 * The list of Java wrapper classes (includes String).
	 */
	private static final List<String> wrappers = Arrays.asList(new String[] { "Byte", "Short", "Character", "Integer", "Long", "Float", "Double", "Boolean", "String" });

	/**
	 * The XML document.
	 */
	private final Document document;

	/**
	 * The XML root element.
	 */
	private final Element root;

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
	 * Adds a bean to the application context using a Java source file. Only
	 * public classes are added.
	 * @param reader the input stream to the Java source file (this is closed
	 * after it is read)
	 * @return this
	 * @throws IOException if there's a problem reading the file
	 */
	public ApplicationContextGenerator addBean(Reader reader) throws IOException {
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

	/**
	 * Creates the &lt;bean /&gt; element.
	 * @param javaSource the Java source code
	 * @return the &lt;bean /&gt; element or null if there were no public
	 * classes.
	 */
	private Element buildBeanElement(String javaSource) {
		Matcher matcher;

		//get the name of the class
		String className = null;
		matcher = classNameRegex.matcher(javaSource);
		if (matcher.find()) {
			className = matcher.group(1);
		} else {
			return null;
		}

		//get the name of the package
		String packageName = null;
		matcher = packageRegex.matcher(javaSource);
		if (matcher.find()) {
			packageName = matcher.group(1);
		}

		//create <bean /> element
		Element beanElement = document.createElement("bean");
		String classNameLower = className.substring(0, 1).toLowerCase() + className.substring(1);
		beanElement.setAttribute("id", classNameLower);
		String classAttr = (packageName == null) ? className : packageName + "." + className;
		beanElement.setAttribute("class", classAttr);

		//create <constructor-arg /> elements
		Pattern constructorRegex = Pattern.compile("public\\s+" + className + "\\s*\\(\\s*(.*?)\\s*\\)");
		matcher = constructorRegex.matcher(javaSource);
		List<String> constructors = new ArrayList<String>();
		boolean defaultConstructor = false;
		while (matcher.find()) {
			String parameters = matcher.group(1);
			if (parameters.isEmpty()) {
				//there is a default constructor, so we won't create <constructor-arg /> elements
				defaultConstructor = true;
				break;
			}
			constructors.add(parameters);
		}
		if (!defaultConstructor && constructors.size() == 1) {
			//if there is only one constructor and that constructor is not a default constructor, then generate the <constructor-arg /> elements
			matcher = parameterRegex.matcher(constructors.get(0));
			int index = 0;
			while (matcher.find()) {
				String type = matcher.group(1);
				//String name = matcher.group(2);

				Element constructorArgElement = document.createElement("constructor-arg");

				if (wrappers.contains(type) || primatives.contains(type)) {
					if (wrappers.contains(type)) {
						type = "java.lang." + type;
					}
					constructorArgElement.setAttribute("type", type);
					constructorArgElement.setAttribute("value", "");
				} else {
					String typeLower = type.substring(0, 1).toLowerCase() + type.substring(1);
					constructorArgElement.setAttribute("ref", typeLower);
				}

				constructorArgElement.setAttribute("index", index + "");
				index++;

				beanElement.appendChild(constructorArgElement);
			}
		}

		//generate <property /> elements from public fields
		matcher = publicFieldRegex.matcher(javaSource);
		while (matcher.find()) {
			String type = matcher.group(1);
			String name = matcher.group(2);
			String value = matcher.group(4);

			Element propertyElement = document.createElement("property");
			propertyElement.setAttribute("name", name);
			if (primatives.contains(type) || wrappers.contains(type)) {
				propertyElement.setAttribute("value", value);
			} else if ("List".equals(type) || "java.util.List".equals(type)){
				Element listElement = document.createElement("list");
				propertyElement.appendChild(listElement);
			} else if ("Set".equals(type) || "java.util.Set".equals(type)){
				Element listElement = document.createElement("set");
				propertyElement.appendChild(listElement);
			} else if ("Map".equals(type) || "java.util.Map".equals(type)){
				Element listElement = document.createElement("map");
				propertyElement.appendChild(listElement);
			} else if ("Properties".equals(type) || "java.util.Properties".equals(type)){
				Element listElement = document.createElement("props");
				propertyElement.appendChild(listElement);
			} else {
				String typeLower = type.substring(0, 1).toLowerCase() + type.substring(1);
				propertyElement.setAttribute("ref", typeLower);
			}
			beanElement.appendChild(propertyElement);
		}

		//generate <property /> elements from setters
		matcher = setterRegex.matcher(javaSource);
		while (matcher.find()) {
			String name = matcher.group(1);
			String type = matcher.group(2);

			Element propertyElement = document.createElement("property");
			name = name.substring(0, 1).toLowerCase() + name.substring(1);
			propertyElement.setAttribute("name", name);
			if (primatives.contains(type) || wrappers.contains(type)) {
				propertyElement.setAttribute("value", "");
			} else if ("List".equals(type) || "java.util.List".equals(type)){
				Element listElement = document.createElement("list");
				propertyElement.appendChild(listElement);
			} else if ("Set".equals(type) || "java.util.Set".equals(type)){
				Element listElement = document.createElement("set");
				propertyElement.appendChild(listElement);
			} else if ("Map".equals(type) || "java.util.Map".equals(type)){
				Element listElement = document.createElement("map");
				propertyElement.appendChild(listElement);
			} else if ("Properties".equals(type) || "java.util.Properties".equals(type)){
				Element listElement = document.createElement("props");
				propertyElement.appendChild(listElement);
			} else {
				String typeLower = type.substring(0, 1).toLowerCase() + type.substring(1);
				propertyElement.setAttribute("ref", typeLower);
			}
			beanElement.appendChild(propertyElement);
		}

		return beanElement;
	}
}
