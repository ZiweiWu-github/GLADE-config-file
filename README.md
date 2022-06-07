# GLADE-config-file

Prototype code of improving the user-friendliness of GLADE through the use of a configuration file.

The GLADE.jar file needs to be where the configuration file is.
Instructions how cloning the source code and compiling it can be found at https://github.com/obastani/glade
Then copy this GitHub repo into where the compiled GLADE JAR file is.

How to compile:
javac -classpath test:glade.jar test/main/GLADE_Config.java

How to run:
java -classpath test:glade.jar GLADE_Config.Test
java -classpath test:glade.jar GLADE_Config.Test GLADE_config.txt

If run without any arguments, then the program defaults to "GLADE_config.txt" and assumes that it exists.

Currently, the program assumes that all folders are created by the user and checks for their existence. That is, you will need to create the output folder for where the serialized grammar will be outputted into. 

The "save_CFG?" option does nothing as of now. Its purpose was to output the inferred CFG as text so that other grammar-based fuzzers can use it. Future work can be to find a good format to do so and implement it.
