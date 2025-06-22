# dependency-audit


This tool is designed to identify all dependencies, both direct and indirect, within a Maven project and display the resolved library versions used during build time. It highlights the specific library versions and indirect dependencies included in the final build. Te tool can further detect the code dependency tree (TODO: update this based on the findings)

*The tool Input*: Maven project's location and Artifact name

*The tool Utilizes*:

  - The mvn dependency:tree command to analyze all dependencies and their hierarchy within the project.
  - The mvn dependency:build-classpath command to determine the dependency resolution order.

*To run the tool*
Build the project and use the jar file generated and pass in the following commands
-input {path to the pom file}\pom.xml -output {fileName} -format md/json -testscope true
