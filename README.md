# GLADE-config-file

The GLADE.jar file needs to be where the configuration file is.
Instructions how cloning the source code and compiling it can be found at https://github.com/obastani/glade
Then copy this GitHub repo into where the compiled GLADE JAR file is.

How to compile:
javac -classpath test:glade.jar test/main/GLADE_Config.java

How to run:
java -classpath test:glade.jar GLADE_Config.Test
java -classpath test:glade.jar GLADE_Config.Test GLADE_config.txt

If run without any arguments, then the program defaults to "GLADE_config.txt" and assumes that it exists.