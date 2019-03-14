import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

import jdk.javadoc.doclet.*;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.SimpleElementVisitor9;

/**
 * A doclet for creating a manifest json file of available code examples for the entities in the project.
 */
public final class CodeExampleManifestDoclet implements Doclet {
    private Doclet doclet;
    private Path examplesPath;
    private Path manifestPath;

    public abstract class Option implements Doclet.Option, Comparable<Doclet.Option> {
        int argumentCount;
        List<String> names;
        String description;
        String parameters;

        Option(int argumentCount, List<String> names, String description, String parameters) {
            this.argumentCount = argumentCount;
            this.names = names;
            this.description = description;
            this.parameters = parameters;
        }

        @Override
        public int getArgumentCount() {
            return argumentCount;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public Kind getKind() {
            return Kind.STANDARD;
        }

        @Override
        public List<String> getNames() {
            return names;
        }

        @Override
        public String getParameters() {
            return parameters;
        }

        @Override
        public int compareTo(Doclet.Option that) {
            return this.getNames().get(0).compareTo(that.getNames().get(0));
        }
    }

    private class Method {
        ExecutableElement element;
        String name;
        String anchorName; // This is the name as it is formatted in the output <a> tags: `someMethod(Foo, List<int>)` becomes `someMethod(com.example.Foo,List)`
        Path expectedPath;
    }

    private class Type {
        TypeElement element;
        String name;
        List<Method> methods;
        Path expectedPath;
    }

    public CodeExampleManifestDoclet() {
        doclet = new StandardDoclet();
    }

    public CodeExampleManifestDoclet(Doclet doclet) {
        this.doclet = doclet;
    }

    @Override
    public void init(Locale locale, Reporter reporter) {
        doclet.init(locale, reporter);
    }

    @Override
    public String getName() {
        return "CodeExampleManifestDoclet";
    }

    @Override
    public Set<Doclet.Option> getSupportedOptions() {
        var options = new HashSet<Doclet.Option>();
        options.addAll(doclet.getSupportedOptions());
        options.add(new Option(1, Arrays.asList("-examplesPath"), "Path to example code snippets directory.", "path/to/code/examples/") {
            @Override
            public boolean process(String option, List<String> arguments) {
                examplesPath = Paths.get(arguments.get(0));
                return true;
            }
        });
        options.add(new Option(1, Arrays.asList("-manifestPath"), "Path to output example manifest.", "path/to/example/manifest") {
            @Override
            public boolean process(String option, List<String> arguments) {
                manifestPath = Paths.get(arguments.get(0));
                return true;
            }
        });
        return options;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return doclet.getSupportedSourceVersion();
    }

    Method makeMethod(Path expectedClassPath, ExecutableElement e) {
        var path = new ArrayList<String>();
        var name = e.getSimpleName().toString();
        if (name.equals("<init>")) { // constructor
            name = e.getEnclosingElement().getSimpleName().toString();
        }
        path.add(name);

        var qualifiedParameterTypes = new ArrayList<String>();
        for (var parameter : e.getParameters()) {
            var typename = parameter.asType().toString();

            // Remove type parameter(s)
            typename = typename.replaceAll("<.*>", "");

            var unqualifiedTypename = new String(typename);
            // Remove qualification
            var p = Pattern.compile(".*\\.(.*)");
            var m = p.matcher(unqualifiedTypename);
            if (m.find()) {
                unqualifiedTypename = m.group(1);
            }
            path.add(unqualifiedTypename);
            qualifiedParameterTypes.add(typename);
        }

        var method = new Method();
        method.element = e;
        method.name = name;
        method.expectedPath = expectedClassPath.resolve(String.join("-", path) + ".java");
        method.anchorName = e.getSimpleName().toString() + "(" + String.join(",", qualifiedParameterTypes) + ")";
        return method;
    }

    Method maybeMakeMethod(Path expectedClassPath, Element e) {
        return new SimpleElementVisitor9<Method, Void>() {
            @Override
            public Method visitExecutable(ExecutableElement e, Void aVoid) {
                return makeMethod(expectedClassPath, e);
            }
        }.visit(e);
    }

    Type makeType(TypeElement t) {
        var name = t.getQualifiedName().toString();

        // Build up path: com/example/package/
        var expectedPath = Paths.get("");
        var qualifiers = name.split("\\.");

        for (var i = 0; i < qualifiers.length - 1; ++i) { // shave off the simple class name
            expectedPath = expectedPath.resolve(qualifiers[i]); // add qualifier
        }

        // Expect to find methods in: com/example/package/ExampleClass/
        var expectedMethodDirectory = expectedPath.resolve(t.getSimpleName().toString());
        var methods = new ArrayList<Method>();
        for (var e : t.getEnclosedElements()) {
            var method = maybeMakeMethod(expectedMethodDirectory, e);
            if (method != null) {
                methods.add(method);
            }
        }

        var type = new Type();
        type.name = name;
        type.methods = methods;
        type.element = t;

        // Expect class example to be at: com/example/package/ExampleClass.java
        type.expectedPath = expectedPath.resolve(t.getSimpleName().toString() + ".java");
        return type;
    }

    @Override
    public boolean run(DocletEnvironment docEnv) {
        // It is NOT POSSIBLE to modify the behavior of the HTML renderer without just
        // copying its entire source. The HtmlDoclet is private to JDK.
        //
        // It is NOT POSSIBLE to modify the processed elements to inject new tags.
        // They are immutable.
        //
        // It is NOT POSSIBLE to wrap the DocletEnvironment in a new implementation
        // that returns a new tree of elements with injected tags, because something
        // in the JDK (see WorkArounds.java) downcasts to the concrete DocEnvImpl type.
        //
        // Therefore, this doclet just creates a manifest of available code examples
        // and where to put them, so a post-javadoc script can inject them into the
        // resulting HTML output.

        // Get the listing of example files from the
        if (examplesPath == null) {
            System.err.println("-examplesPath must be specified, e.g. path/to/examples/");
            return false;
        }

        if (manifestPath == null) {
            System.err.println("-manifestPath must be specified, e.g. path/to/manifest.json");
            return false;
        }

        final var EXAMPLE_FILE_EXTENSION = ".java";

        // Get the listing of examples from the example path specified by -examplesPath.
        // Directory structure should match package structure.
        // For classes, e.g.
        //
        //      com.example.package.SomeClass
        //
        // put the code example in a file under the examplesPath:
        //
        //      com/example/package/SomeClass.java
        //
        // For methods of that class, e.g.:
        //
        //      void someMethod(int, List<int>, com.example.otherpackage.SomeOtherClass);
        //
        // transform the method signature so that its name and parameter types as unqualified
        // names without type parameters are joined with dashes, e.g.:
        //
        //      someMethod-int-List-SomeOtherClass.java
        //
        // and store this in a directory under the class name, e.g.:
        //
        //      com/example/package/SomeClass/someMethod-int-List-SomeOtherClass.java
        //
        // Hopefully you have no overloads on generic types or same-named classes
        // from different packages. Limitation: 1 code example per class or method.

        var examplePaths = new HashSet<Path>();
        try {
            Files.walk(examplesPath).filter((path) -> {
                // Check if file extension is .java
                var name = path.getFileName().toString();
                int lastIndexOf = name.lastIndexOf(".");
                if (lastIndexOf == -1) {
                    return false;
                }
                return name.substring(lastIndexOf).equals(EXAMPLE_FILE_EXTENSION);
            }).forEach(examplePaths::add);
        } catch(IOException e) {
            System.err.println("Failed to read examplesPath: " + e.getMessage());
            return false;
        }

        if (examplePaths.isEmpty()) {
            System.err.println("No example files found in '" + examplesPath + "'. Done.");
            return true;
        }

        var matchedExamples = new HashSet<Path>();

        var typesWithExamples = new ArrayList<Type>();

        for (TypeElement t : ElementFilter.typesIn(docEnv.getIncludedElements())) {
            var hasExample = false;
            var type = makeType(t);
            var typePath = examplesPath.resolve(type.expectedPath);
            if (examplePaths.contains(typePath)) {
                matchedExamples.add(typePath);
                hasExample = true;
            }

            for (var method : type.methods) {
                var methodPath = examplesPath.resolve(method.expectedPath);
                if (examplePaths.contains(methodPath)) {
                    matchedExamples.add(methodPath);
                    hasExample = true;
                }
            }

            if (hasExample) {
                typesWithExamples.add(type);
            }
        }

        // Emit set difference, so users are aware of matched and unmatched examples.
        var unmatchedExamples = new HashSet<>(examplePaths);
        unmatchedExamples.removeAll(matchedExamples);
        for (var example : unmatchedExamples) {
            System.err.println("No matching entity for example: " + example);
        }

        for (var example : matchedExamples) {
            System.out.println("Matched example: " + example);
        }

        // Manually create json to avoid dependencies?
        var objects = new ArrayList<String>();
        for (var type : typesWithExamples) {
            var properties = new ArrayList<String>();

            var classExamplePath = "null";
            var typePath = examplesPath.resolve(type.expectedPath);
            if (examplePaths.contains(typePath)) {
                classExamplePath = "\"" + typePath.toString() + "\"";
            }
            properties.add("\"name\": \"" + type.element.getQualifiedName().toString() + "\"");
            properties.add("\"examplePath\": " + classExamplePath);
            properties.add("\"htmlPath\": \"" + type.expectedPath.getParent().resolve(type.element.getSimpleName() + ".html") + "\"");

            var methods = new ArrayList<String>();
            for (var method : type.methods) {
                var methodProperties = new ArrayList<String>();
                var methodPath = examplesPath.resolve(method.expectedPath);
                if (examplePaths.contains(methodPath)) {
                    methodProperties.add("\"name\": \"" + method.element.getSimpleName() + "\"");
                    methodProperties.add("\"anchorName\": \"" + method.anchorName + "\"");
                    methodProperties.add("\"examplePath\": \"" + methodPath + "\"");
                    methods.add("{" + String.join(", ", methodProperties) + "}");
                }
            }
            properties.add("\"methods\": [" + String.join(",", methods) + "]");
            objects.add("{" + String.join(", ", properties) + "}");
        }

        try {
            // Write to manifest file.
            Files.write(manifestPath, Arrays.asList("[", String.join(",\n", objects), "]"), Charset.forName("UTF-8"));
        } catch(IOException e) {
            System.err.println("Failed to write to output file '" + manifestPath + "': " + e.getMessage());
            return false;
        }

        return doclet.run(docEnv);
    }
}