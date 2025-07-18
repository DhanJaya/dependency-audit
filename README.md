# DEPINSIGHT: Dependency Visualization Tool for Maven Projects

This tool is designed to analyze and visualize all dependencies both direct and transitive, within a Maven project. It displays the resolved library versions used during build time in an interactive graph format. Each node in the graph represents a dependency, making it easy to understand the structure of your project.

## Key Features

### 📌 Dependency Graph Visualization
- All dependencies are represented in a graph:
- Each node corresponds to a unique library.
- Duplicate libraries (the same library appearing via multiple transitive dependencies) are color-coded with the same shade.
- If there are duplicate libraries the version resolved by Maven is highlighted.
- Version conflicts are marked distinctly.

### Test Scope Handling
- You can optionally include the test-scope dependencies and by defualt they are excluded.
- When included, test dependencies can be easily identified by the edge styles connecting their nodes.

### Transitive Functionality Detection
- Transitive dependencies used by default are highlighted with red-colored edges.
- Use an optional parameter to show which specific functionalities of the transitive dependencies are actively used.

### Export Options
- All dependencies (direct and transitive), along with the used functionalities, can be exported to:
  - **HTML**
  - **CSV**

---

## How to Use the Tool

### Prerequisites
- Java 21 must be installed.

### Running the Tool
You can either:
- **Download** the `dependency-audit.jar` file from the [releases](#) section  
**OR**
- **Clone** the repository and build it locally:

```bash
mvn clean package -DskipTests
```
## Input Parameters

Specify the following parameters when running the tool:

| Parameter              | Description                                                              | Required | Default |
|------------------------|--------------------------------------------------------------------------|----------|---------|
| `-input`               | **Absolute path** to the `pom.xml` file                                  | Yes   | –       |
| `-excludetestscope`    | Excludes test-scope dependencies from the graph                          | No    | `false` |
| `-transitivefunctions` | Displays which functionalities of transitive dependencies are in use     | No    | `false` |

### 📘 Sample Input

```bash
-input D:\documents\DepTool\Repos\google_guava\guava\pom.xml -excludetestscope true -transitivefunctions true
```
