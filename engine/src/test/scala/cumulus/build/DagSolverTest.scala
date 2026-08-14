package cumulus.build

import munit.FunSuite

class DagSolverTest extends FunSuite:

  test("compute topological sort for linear dependency chain A -> B -> C") {
    val dependencies = Map(
      "A" -> Set("B"),
      "B" -> Set("C"),
      "C" -> Set[String]()
    )
    val result = DagSolver.computeTopologicalSort(dependencies)

    assert(result.cycles.isEmpty)
    assert(result.sorted.length == 3)
    // C should come first (no dependencies), then B, then A
    assert(result.sorted == Seq("C", "B", "A"))
  }

  test("compute topological sort for diamond dependency A -> (B, C), B -> D, C -> D") {
    val dependencies = Map(
      "A" -> Set("B", "C"),
      "B" -> Set("D"),
      "C" -> Set("D"),
      "D" -> Set[String]()
    )
    val result = DagSolver.computeTopologicalSort(dependencies)

    assert(result.cycles.isEmpty)
    assert(result.sorted.length == 4)
    // D must come first, then B and C (in some order), then A
    assert(result.sorted.head == "D")
    assert(result.sorted.last == "A")
    assert(result.sorted.contains("B"))
    assert(result.sorted.contains("C"))
  }

  test("detect circular dependency A -> B -> C -> A") {
    val dependencies = Map(
      "A" -> Set("B"),
      "B" -> Set("C"),
      "C" -> Set("A")
    )
    val result = DagSolver.computeTopologicalSort(dependencies)

    assert(result.cycles.nonEmpty)
    assert(result.sorted.length < 3) // Not all nodes can be sorted with cycle
  }

  test("single module with no dependencies") {
    val dependencies = Map(
      "core" -> Set[String]()
    )
    val result = DagSolver.computeTopologicalSort(dependencies)

    assert(result.cycles.isEmpty)
    assert(result.sorted == Seq("core"))
  }

  test("multiple independent modules (no dependencies)") {
    val dependencies = Map(
      "A" -> Set[String](),
      "B" -> Set[String](),
      "C" -> Set[String]()
    )
    val result = DagSolver.computeTopologicalSort(dependencies)

    assert(result.cycles.isEmpty)
    assert(result.sorted.length == 3)
    assert(result.sorted.toSet == Set("A", "B", "C"))
  }

  test("compute build order for linear chain returns topological order") {
    val modules = Seq("A", "B", "C")
    val dependencies = Map(
      "A" -> Set("B"),
      "B" -> Set("C"),
      "C" -> Set[String]()
    )
    val (ordered, warnings) = DagSolver.computeBuildOrder(modules, dependencies)

    assert(warnings.isEmpty)
    assert(ordered == Seq("C", "B", "A"))
  }

  test("compute build order with cycle returns declaration order with warnings") {
    val modules = Seq("A", "B", "C")
    val dependencies = Map(
      "A" -> Set("B"),
      "B" -> Set("C"),
      "C" -> Set("A")
    )
    val (ordered, warnings) = DagSolver.computeBuildOrder(modules, dependencies)

    assert(warnings.isDefined)
    assert(warnings.get.nonEmpty)
    assert(ordered == modules) // Falls back to declaration order
  }

  test("complex DAG with multiple levels") {
    // Build structure:
    //  E (no deps)
    //  D -> E
    //  C -> D, E
    //  B -> D
    //  A -> B, C
    val dependencies = Map(
      "A" -> Set("B", "C"),
      "B" -> Set("D"),
      "C" -> Set("D", "E"),
      "D" -> Set("E"),
      "E" -> Set[String]()
    )
    val result = DagSolver.computeTopologicalSort(dependencies)

    assert(result.cycles.isEmpty)
    assert(result.sorted.length == 5)

    // E should be first (no deps)
    assert(result.sorted.head == "E")
    // A should be last (depends on everything)
    assert(result.sorted.last == "A")

    // Check partial ordering constraints
    assert(result.sorted.indexOf("E") < result.sorted.indexOf("D"))
    assert(result.sorted.indexOf("D") < result.sorted.indexOf("C"))
    assert(result.sorted.indexOf("D") < result.sorted.indexOf("B"))
    assert(result.sorted.indexOf("C") < result.sorted.indexOf("A"))
    assert(result.sorted.indexOf("B") < result.sorted.indexOf("A"))
  }

  test("empty dependencies returns empty sorted list") {
    val dependencies = Map[String, Set[String]]()
    val result = DagSolver.computeTopologicalSort(dependencies)

    assert(result.cycles.isEmpty)
    assert(result.sorted.isEmpty)
  }

  test("disconnected components are all sorted") {
    val dependencies = Map(
      "A" -> Set("B"),
      "B" -> Set[String](),
      "C" -> Set("D"),
      "D" -> Set[String]()
    )
    val result = DagSolver.computeTopologicalSort(dependencies)

    assert(result.cycles.isEmpty)
    assert(result.sorted.length == 4)
    // B before A, D before C
    assert(result.sorted.indexOf("B") < result.sorted.indexOf("A"))
    assert(result.sorted.indexOf("D") < result.sorted.indexOf("C"))
  }

  test("handle module with dependency on non-declared module") {
    // A depends on B, but B is not in the module list
    // This should still work - B is added to the graph
    val dependencies = Map(
      "A" -> Set("B")
    )
    val result = DagSolver.computeTopologicalSort(dependencies)

    assert(result.cycles.isEmpty)
    assert(result.sorted.length == 2)
    assert(result.sorted.head == "B")
    assert(result.sorted.last == "A")
  }

  test("compute build order with valid DAG maintains order") {
    val modules = Seq("core", "web", "services", "api")
    val dependencies = Map(
      "core" -> Set[String](),
      "web" -> Set("core"),
      "services" -> Set("core"),
      "api" -> Set("core", "web", "services")
    )
    val (ordered, warnings) = DagSolver.computeBuildOrder(modules, dependencies)

    assert(warnings.isEmpty)
    // core should be first
    assert(ordered.head == "core")
    // api should be last (depends on all others)
    assert(ordered.last == "api")
  }

  test("detect two-node cycle") {
    val dependencies = Map(
      "A" -> Set("B"),
      "B" -> Set("A")
    )
    val result = DagSolver.computeTopologicalSort(dependencies)

    assert(result.cycles.nonEmpty)
  }

  test("self-loop cycle A -> A") {
    val dependencies = Map(
      "A" -> Set("A")
    )
    val result = DagSolver.computeTopologicalSort(dependencies)

    assert(result.cycles.nonEmpty)
    // A cannot be sorted when it depends on itself
    assert(result.sorted.isEmpty)
  }

