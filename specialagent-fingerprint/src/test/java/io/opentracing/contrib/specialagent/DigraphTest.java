package io.opentracing.contrib.specialagent;

import org.junit.Test;

public class DigraphTest {
  @Test
  public void test() {
    // Create a Graph with Integer nodes
    final Digraph<Integer> graph = new Digraph<>();
    graph.add(0, 1);
    graph.add(0, 2);
    graph.add(0, 3);
    graph.add(1, 2);
    graph.add(1, 3);
    graph.add(2, 3);
    graph.add(2, 4);
    graph.add(4, 5);
    graph.add(5, 6); // Tetrahedron with tail
    System.out.println("The current graph: " + graph);
    System.out.println("In-degrees: " + graph.inDegree());
    System.out.println("Out-degrees: " + graph.outDegree());
    System.out.println("A topological sort of the vertices: " + graph.topSort());
    System.out.println("The graph " + (graph.isDag() ? "is" : "is not") + " a dag");
    System.out.println("BFS distances starting from " + 0 + ": " + graph.bfsDistance(0));
    System.out.println("BFS distances starting from " + 1 + ": " + graph.bfsDistance(1));
    System.out.println("BFS distances starting from " + 2 + ": " + graph.bfsDistance(2));
    graph.add(4, 1); // Create a cycle
    System.out.println("Cycle created");
    System.out.println("The current graph: " + graph);
    System.out.println("A topological sort of the vertices: " + graph.topSort());
    System.out.println("The graph " + (graph.isDag() ? "is" : "is not") + " a dag");
    System.out.println("BFS distances starting from " + 2 + ": " + graph.bfsDistance(2));
  }
}
