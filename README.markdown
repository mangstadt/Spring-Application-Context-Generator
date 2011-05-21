# Overview

Generates the bean definitions for a Spring XML application context file from the source code of Java classes.  Run it from the command line:

    java com.mangst.appcontext.ApplicationContextGenerator --source=path/to/src --package=com.example.foo --package=com.example.bar
    
It creates:
*   A <bean /> element for each public class
*   A <property /> element for each public field and public setter method.
*   A list of <constructor-arg /> elements if (1) there is only one constructor and (2) that constructor is not the default constructor.

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
    -h, --help
       Displays this help message.
