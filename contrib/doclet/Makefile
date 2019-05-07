CLASSES = \
	src/CodeExampleManifestDoclet.java

.SUFFIXES: .java .class
.java.class:
	"${JAVA_HOME}/bin/javac" -d bin $*.java

default: directories classes

directories:
	mkdir -p bin

classes: $(CLASSES:.java=.class)
