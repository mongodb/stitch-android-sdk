CLASSES = \
	src/CodeExampleManifestDoclet.java

.SUFFIXES: .java .class
.java.class:
	javac -d bin $*.java

default: classes

classes: $(CLASSES:.java=.class)
