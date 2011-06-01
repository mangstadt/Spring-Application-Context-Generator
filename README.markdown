# Overview

Generates the bean definitions for a Spring XML application context file from the source code of Java classes.  Run it from the command line:

    java -jar appcontext.jar --source=path/to/src --package=com.example.foo --package=com.example.bar
    
It creates:

 *   A `<bean />` element for each public class
 *   A `<property />` element for each public field and public setter method.
 *   A list of `<constructor-arg />` elements if (1) there is only one constructor and (2) that constructor is not the default constructor.

# Command line arguments

    -s=PATH, --source=PATH (required)
       The directory that the Java source code is located in.
    -p=NAME, --package=NAME (required)
       All public classes in the specified packages will be added to the bean
       definition file. Use this parameter multiple times to specify
       multiple packages.
    -v=N, --springVersion=N
       The version of Spring you are using (for specifying the XML schema).
       (defaults to "2.5")
    -r, --recurse
       Recurse into sub-packages (example: specifying "-r -p=com.foo" will also
       include "com.foo.bar").
    -h, --help
       Displays this help message.

# Opening in Eclipse

To generate the necessary files needed to open the project in Eclipse, navigate to the project root and run the following Maven command:

    mvn eclipse:eclipse

# How to build

To build the project, navigate to the project root and run the following Maven command:

    mvn clean compile assembly:single
    
This will generate a runnable JAR file that contains all dependencies.
