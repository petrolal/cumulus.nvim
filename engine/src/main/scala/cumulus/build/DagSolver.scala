package cumulus.build

import scala.collection.mutable

/**
 * DagSolver implements Kahn's topological sort algorithm for multi-module build ordering.
 *
 * A dependency graph is represented as Map[ModuleName, Set[Dependencies]],
 * where each module maps to the set of modules it depends on (must be built after).
 *
 * Kahn's Algorithm:
 * 1. Count in-degrees of all nodes (how many dependencies each module has).
 * 2. Enqueue all nodes with in-degree 0 (no dependencies).
 * 3. While queue not empty: dequeue node, add to sorted order, decrement in-degree of dependents, enqueue new in-degree 0 nodes.
 * 4. If sorted order length != total nodes, a cycle exists.
 */
object DagSolver:

  /**
   * Data structure representing the result of topological sorting.
   *
   * @param sorted The topologically sorted list of module names
   * @param cycles List of cycle descriptions if cycles were detected
   */
  case class TopologicalSortResult(
    sorted: Seq[String],
    cycles: Seq[String] = Seq()
  )

  /**
   * Compute topological sort of a module dependency graph using Kahn's algorithm.
   *
   * @param dependencies Map[moduleName, Set[modulesDependedUpon]]
   *                    where module A -> Set(B, C) means A depends on (must be built after) B and C
   * @return TopologicalSortResult with sorted modules and any detected cycles
   */
  def computeTopologicalSort(
    dependencies: Map[String, Set[String]]
  ): TopologicalSortResult =
    if dependencies.isEmpty then
      return TopologicalSortResult(sorted = Seq())

    // Validate that all referenced dependencies are known modules
    val allModules = dependencies.keySet
    val allReferencedModules = dependencies.values.flatten.toSet

    // Build a complete graph including modules that may only be dependencies
    val completeModules = allModules ++ allReferencedModules
    val completeDependencies = dependencies ++ allReferencedModules.diff(allModules).map(_ -> Set[String]()).toMap

    // Kahn's Algorithm:
    // Step 1: Compute in-degrees (count how many modules each module depends on)
    val inDegree = mutable.Map[String, Int]()
    for module <- completeModules do
      inDegree(module) = completeDependencies.getOrElse(module, Set()).size

    // Step 2: Create a reverse dependency map (dependents of each module)
    // If A depends on B, then B has A as a dependent
    val dependents = mutable.Map[String, mutable.Set[String]]()
    for module <- completeModules do
      dependents(module) = mutable.Set()

    for (module, deps) <- completeDependencies do
      for dep <- deps do
        dependents.getOrElseUpdate(dep, mutable.Set()) += module

    // Step 3: Enqueue all nodes with in-degree 0
    val queue = mutable.Queue[String]()
    for module <- completeModules do
      if inDegree(module) == 0 then
        queue.enqueue(module)

    // Step 4: Process nodes
    val sorted = mutable.ListBuffer[String]()
    while queue.nonEmpty do
      val module = queue.dequeue()
      sorted += module

      // Decrement in-degree of all dependents and enqueue if in-degree becomes 0
      for dependent <- dependents.getOrElse(module, mutable.Set()) do
        inDegree(dependent) -= 1
        if inDegree(dependent) == 0 then
          queue.enqueue(dependent)

    // Step 5: Check for cycles
    val cycles = mutable.ListBuffer[String]()
    val unreachedModules = mutable.Set[String]()
    for module <- completeModules do
      if !sorted.contains(module) then
        unreachedModules += module

    if unreachedModules.nonEmpty then
      // Detect cycles: try to find cycles among unreached modules
      cycles += s"Circular dependencies detected involving modules: ${unreachedModules.mkString(", ")}"

    TopologicalSortResult(sorted = sorted.toSeq, cycles = cycles.toSeq)

  /**
   * Compute a safe build order with fallback to declaration order on cycles.
   *
   * @param modules List of module names in declaration order
   * @param dependencies Map[moduleName, Set[dependedUponModules]]
   * @return Sequence of ordered module names (either topologically sorted or declaration order on cycle)
   *         and optional warnings about detected cycles
   */
  def computeBuildOrder(
    modules: Seq[String],
    dependencies: Map[String, Set[String]]
  ): (Seq[String], Option[Seq[String]]) =
    val result = computeTopologicalSort(dependencies)

    if result.cycles.nonEmpty then
      // Cycle detected: fall back to declaration order but include warnings
      (modules, Some(result.cycles))
    else
      // No cycle: return topological order
      (result.sorted, None)
