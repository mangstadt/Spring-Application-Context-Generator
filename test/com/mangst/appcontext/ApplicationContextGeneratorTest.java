package com.mangst.appcontext;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.NamespaceContext;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

/**
 * Tests the ApplicationContextGenerator class.
 * @author mangst
 */
public class ApplicationContextGeneratorTest {
	/**
	 * Object used for XPath querying.
	 */
	private static XPath xpath;

	@BeforeClass
	public static void beforeClass() {
		AppContextNamespaceContext nc = new AppContextNamespaceContext();
		xpath = XPathFactory.newInstance().newXPath();
		xpath.setNamespaceContext(nc);
	}

	/**
	 * Test to make sure it handles the specified Spring version properly.
	 * @throws Exception
	 */
	@Test
	public void testSpringVersion() throws Exception {
		ApplicationContextGenerator generator = new ApplicationContextGenerator("2.0");
		Document document = generator.getDocument();
		NamedNodeMap attrs = document.getChildNodes().item(0).getAttributes();
		String actual = attrs.getNamedItemNS("http://www.w3.org/2001/XMLSchema-instance", "schemaLocation").getNodeValue();
		String expected = "http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-2.0.xsd";
		Assert.assertEquals(expected, actual);
	}

	/**
	 * If no Java classes are given, then there should be no "bean" elements.
	 * @throws Exception
	 */
	@Test
	public void testNoBeans() throws Exception {
		ApplicationContextGenerator generator = new ApplicationContextGenerator("2.0");
		Document document = generator.getDocument();
		NodeList nodeList = (NodeList) xpath.evaluate("/b:beans/bean", document, XPathConstants.NODESET);
		int expected = 0;
		int actual = nodeList.getLength();
		Assert.assertEquals(expected, actual);
	}

	/**
	 * A class from the default package should not have a "class" attribute in
	 * the "bean" element.
	 * @throws Exception
	 */
	@Test
	public void testDefaultPackage() throws Exception {
		ApplicationContextGenerator generator = new ApplicationContextGenerator("2.0");
		String source = "public class Clazz{}";
		StringReader reader = new StringReader(source);
		generator.addBean(reader);
		Document document = generator.getDocument();

		NodeList nodeList = (NodeList) xpath.evaluate("/b:beans/bean", document, XPathConstants.NODESET);
		Assert.assertEquals(1, nodeList.getLength());
		NamedNodeMap attrs = nodeList.item(0).getAttributes();
		Assert.assertEquals("clazz", attrs.getNamedItem("id").getNodeValue());
		Assert.assertEquals("Clazz", attrs.getNamedItem("class").getNodeValue());

		nodeList = (NodeList) xpath.evaluate("/b:beans/bean/property", document, XPathConstants.NODESET);
		Assert.assertEquals(0, nodeList.getLength());
		nodeList = (NodeList) xpath.evaluate("/b:beans/bean/constructor-arg", document, XPathConstants.NODESET);
		Assert.assertEquals(0, nodeList.getLength());
	}

	/**
	 * The package specified in the source file should be used to build the
	 * class' fully-qualified name.
	 * @throws Exception
	 */
	@Test
	public void testPackage() throws Exception {
		ApplicationContextGenerator generator = new ApplicationContextGenerator("2.0");
		String source = "package com.example; public class Clazz{}";
		StringReader reader = new StringReader(source);
		generator.addBean(reader);
		Document document = generator.getDocument();

		NodeList nodeList = (NodeList) xpath.evaluate("/b:beans/bean", document, XPathConstants.NODESET);
		Assert.assertEquals(1, nodeList.getLength());
		NamedNodeMap attrs = nodeList.item(0).getAttributes();
		Assert.assertEquals("clazz", attrs.getNamedItem("id").getNodeValue());
		Assert.assertEquals("com.example.Clazz", attrs.getNamedItem("class").getNodeValue());

		nodeList = (NodeList) xpath.evaluate("/b:beans/bean[1]/property", document, XPathConstants.NODESET);
		Assert.assertEquals(0, nodeList.getLength());
		nodeList = (NodeList) xpath.evaluate("/b:beans/bean[1]/constructor-arg", document, XPathConstants.NODESET);
		Assert.assertEquals(0, nodeList.getLength());
	}

	/**
	 * &lt;property /&gt; elements should be created from all public fields.
	 * @throws Exception
	 */
	@Test
	public void testPublicField() throws Exception {
		ApplicationContextGenerator generator = new ApplicationContextGenerator("2.0");
		String source = "public class Clazz{ private int hidden; public byte b; public byte bv = 5; public AnObject obj;}";
		StringReader reader = new StringReader(source);
		generator.addBean(reader);
		Document document = generator.getDocument();

		NodeList nodeList = (NodeList) xpath.evaluate("/b:beans/bean[1]/property", document, XPathConstants.NODESET);
		Assert.assertEquals(3, nodeList.getLength());
		NamedNodeMap attrs = nodeList.item(0).getAttributes();
		Assert.assertEquals("b", attrs.getNamedItem("name").getNodeValue());
		Assert.assertEquals("", attrs.getNamedItem("value").getNodeValue());
		Assert.assertEquals(null, attrs.getNamedItem("ref"));
		attrs = nodeList.item(1).getAttributes();
		Assert.assertEquals("bv", attrs.getNamedItem("name").getNodeValue());
		Assert.assertEquals("5", attrs.getNamedItem("value").getNodeValue());
		Assert.assertEquals(null, attrs.getNamedItem("ref"));
		attrs = nodeList.item(2).getAttributes();
		Assert.assertEquals("obj", attrs.getNamedItem("name").getNodeValue());
		Assert.assertEquals(null, attrs.getNamedItem("value"));
		Assert.assertEquals("anObject", attrs.getNamedItem("ref").getNodeValue());
	}

	/**
	 * &lt;property /&gt; elements should be created from all public setter
	 * methods.
	 * @throws Exception
	 */
	@Test
	public void testSetter() throws Exception {
		//Note: Unit test caught an error in the regex that looks for setters
		//Note: Unit test caught an error in how it parsed the info
		ApplicationContextGenerator generator = new ApplicationContextGenerator("2.0");
		String source = "public class Clazz{ private int hidden; private void setHidden(String hidden){} public void setTooManyParams(String too, String many){} public void setFoo(int f){} public void setBar(AnObject b){}}";
		StringReader reader = new StringReader(source);
		generator.addBean(reader);
		Document document = generator.getDocument();

		NodeList nodeList = (NodeList) xpath.evaluate("/b:beans/bean[1]/property", document, XPathConstants.NODESET);
		Assert.assertEquals(2, nodeList.getLength());
		NamedNodeMap attrs = nodeList.item(0).getAttributes();
		Assert.assertEquals("foo", attrs.getNamedItem("name").getNodeValue());
		Assert.assertEquals("", attrs.getNamedItem("value").getNodeValue());
		Assert.assertEquals(null, attrs.getNamedItem("ref"));
		attrs = nodeList.item(1).getAttributes();
		Assert.assertEquals("bar", attrs.getNamedItem("name").getNodeValue());
		Assert.assertEquals(null, attrs.getNamedItem("value"));
		Assert.assertEquals("anObject", attrs.getNamedItem("ref").getNodeValue());
	}

	/**
	 * If there is no constructor defined, then the default constructor is
	 * always used to create the object, so there is no need to create
	 * &lt;constructor-arg /&gt; elements.
	 * @throws Exception
	 */
	@Test
	public void testNoConstructor() throws Exception {
		ApplicationContextGenerator generator = new ApplicationContextGenerator("2.0");
		String source = "public class Clazz{}";
		StringReader reader = new StringReader(source);
		generator.addBean(reader);
		Document document = generator.getDocument();

		NodeList nodeList = (NodeList) xpath.evaluate("/b:beans/bean[1]/constructor-arg", document, XPathConstants.NODESET);
		Assert.assertEquals(0, nodeList.getLength());
	}

	/**
	 * If there is a default constructor defined, then don't create any
	 * &lt;constructor-arg /&gt; elements.
	 * @throws Exception
	 */
	@Test
	public void testDefaultConstructor() throws Exception {
		ApplicationContextGenerator generator = new ApplicationContextGenerator("2.0");
		String source;
		StringReader reader;

		source = "public class Clazz{ public Clazz(){} }";
		reader = new StringReader(source);
		generator.addBean(reader);
		source = "public class Clazz2{ public Clazz2(){} public Clazz2(int arg){} }"; //if there is a default constructor, then it should ignore all other constructors
		reader = new StringReader(source);
		generator.addBean(reader);
		Document document = generator.getDocument();

		NodeList nodeList = (NodeList) xpath.evaluate("/b:beans/bean[1]/constructor-arg", document, XPathConstants.NODESET);
		Assert.assertEquals(0, nodeList.getLength());
		nodeList = (NodeList) xpath.evaluate("/b:beans/bean[2]/constructor-arg", document, XPathConstants.NODESET);
		Assert.assertEquals(0, nodeList.getLength());
	}

	/**
	 * If there is no default constructor, then only create &lt;constructor-arg
	 * /&gt; elements if there is only one constructor.
	 * @throws Exception
	 */
	@Test
	public void testNoDefaultConstructor() throws Exception {
		//Note: Unit test found a bug with the regex
		ApplicationContextGenerator generator = new ApplicationContextGenerator("2.0");
		String source;
		StringReader reader;

		source = "public class Clazz{ public Clazz(int arg, String arg2, AnObject obj){} }";
		reader = new StringReader(source);
		generator.addBean(reader);
		source = "public class Clazz2{ public Clazz2(String arg){} public Clazz2(int arg){} }"; //don't create <constructor-arg /> elements
		reader = new StringReader(source);
		generator.addBean(reader);
		Document document = generator.getDocument();

		NodeList nodeList = (NodeList) xpath.evaluate("/b:beans/bean[1]/constructor-arg", document, XPathConstants.NODESET);
		Assert.assertEquals(3, nodeList.getLength());
		NamedNodeMap attrs = nodeList.item(0).getAttributes();
		Assert.assertEquals("0", attrs.getNamedItem("index").getNodeValue());
		Assert.assertEquals("int", attrs.getNamedItem("type").getNodeValue());
		Assert.assertEquals("", attrs.getNamedItem("value").getNodeValue());
		Assert.assertNull(attrs.getNamedItem("ref"));
		attrs = nodeList.item(1).getAttributes();
		Assert.assertEquals("1", attrs.getNamedItem("index").getNodeValue());
		Assert.assertEquals("java.lang.String", attrs.getNamedItem("type").getNodeValue());
		Assert.assertEquals("", attrs.getNamedItem("value").getNodeValue());
		Assert.assertNull(attrs.getNamedItem("ref"));
		attrs = nodeList.item(2).getAttributes();
		Assert.assertEquals("2", attrs.getNamedItem("index").getNodeValue());
		Assert.assertNull(attrs.getNamedItem("type"));
		Assert.assertNull(attrs.getNamedItem("value"));
		Assert.assertEquals("anObject", attrs.getNamedItem("ref").getNodeValue());

		nodeList = (NodeList) xpath.evaluate("/b:beans/bean[2]/constructor-arg", document, XPathConstants.NODESET);
		Assert.assertEquals(0, nodeList.getLength());
	}

	/**
	 * Generates an XML string from a Document.
	 * @param document the document
	 * @return the XML string
	 * @throws Exception
	 */
	@SuppressWarnings("unused")
	private String getXmlString(Document document) throws Exception {
		TransformerFactory transfac = TransformerFactory.newInstance();
		Transformer trans = transfac.newTransformer();
		trans.setOutputProperty(OutputKeys.INDENT, "yes");
		StringWriter sw = new StringWriter();
		StreamResult result = new StreamResult(sw);
		DOMSource domSource = new DOMSource(document);
		trans.transform(domSource, result);
		return sw.toString();
	}

	/**
	 * Used for XPath queries.
	 * @author mangst
	 */
	private static class AppContextNamespaceContext implements NamespaceContext {
		private Map<String, String> namespaces = new HashMap<String, String>();

		public AppContextNamespaceContext() {
			namespaces.put("b", "http://www.springframework.org/schema/beans");
		}

		public String getNamespaceURI(String prefix) {
			return namespaces.get(prefix);
		}

		public Iterator<String> getPrefixes(String uri) {
			List<String> list = new ArrayList<String>();
			String prefix = getPrefix(uri);
			if (prefix != null) {
				list.add(prefix);
			}
			return list.iterator();
		}

		public String getPrefix(String uri) {
			for (Map.Entry<String, String> e : namespaces.entrySet()) {
				if (e.getValue().equals(uri)) {
					return e.getKey();
				}
			}
			return null;
		}
	}
}
