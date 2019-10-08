import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class App {
     final static String BPMN_FILE_URL = "https://elxkoom6p4.execute-api.eu-central-1.amazonaws.com/prod/engine-rest/process-definition/key/invoice/xml";
     final static String TMP_FILE = "example_bpmn.json";
     final static int REQUEST_TIMEOUT_MS = 10000;

     public static void main(String[] args) {
        try {
            if(args.length < 2) {
                throw new Error("Missing argument(s). You need to provide two node IDs.");
            }

            String xmlBpmn = fetchXmlFromUrl(BPMN_FILE_URL);
            BpmnModelInstance instance = Bpmn.readModelFromStream(IOUtils.toInputStream(xmlBpmn, "UTF-8"));
            List<String> path = findPath(instance, args[0], args[1]);

            outputPath(path);
        } catch(MalformedURLException e) {
            System.out.println("URL malformed");
        } catch (IOException e) {
            System.out.println("Error occured: " + e.toString());
        } catch (Error e) {
            System.out.println("Error occurred: " + e.toString());
        }

    }
    /**
     * Extracts the BPMN XML data from a JSON at the given URL
     * @param url URL
     * @return String containing XML
     * @throws IOException In case writing the JSON content to a tmp file fails
     */
    private static String fetchXmlFromUrl(String url) throws IOException {
        File tmpFile = new File(TMP_FILE);
        FileUtils.copyURLToFile(new URL(BPMN_FILE_URL), tmpFile, REQUEST_TIMEOUT_MS, REQUEST_TIMEOUT_MS);
        JsonObject json = new JsonParser().parse(new FileReader(TMP_FILE)).getAsJsonObject();
        String xmlString = json.get("bpmn20Xml").getAsString();
        tmpFile.delete();

        return xmlString;
    }

    /**
     * Finds a path in a BPMN graph between the given start and end nodes
     * @param instance The BPMN graph
     * @param startNodeId ID of the start node
     * @param targetNodeId ID of the end node
     * @return A list of node IDs representing the path from start node to end node
     */
    private static List<String> findPath(BpmnModelInstance instance, String startNodeId, String targetNodeId) {
        // Light version of https://en.wikipedia.org/wiki/Dijkstra%27s_algorithm#Pseudocode
        // Traverses the graph node by node until the target node is reached
        // For each node, the predecessing node is stored and marked as visited (to avoid cycles)
        // Once the target node is reached, the path to the start node can be reconstructed by going
        //      up the predecessor chain

        FlowNode startNode = instance.getModelElementById(startNodeId);
        FlowNode endNode = instance.getModelElementById(targetNodeId);
        Set<FlowNode> visitedNodes = new HashSet<>();
        List<FlowNode> nodesToProcess = new ArrayList<>();
        Map<FlowNode, FlowNode> predecessorMap = new HashMap<>();

        nodesToProcess.add(startNode);
        FlowNode currNode;
        while(!nodesToProcess.isEmpty()) {
            currNode = nodesToProcess.get(0);
            nodesToProcess.remove(0);
            visitedNodes.add(currNode);

            if(currNode.getId().equals(targetNodeId))
                return reconstructPath(predecessorMap, startNode, endNode); // Reached the target

            for (FlowNode succeedingNode: currNode.getSucceedingNodes().list()) {
                if(!visitedNodes.contains(succeedingNode)) {
                    nodesToProcess.add(succeedingNode);
                    predecessorMap.put(succeedingNode, currNode);
                }
            }
        }

        throw new Error("Target node " + targetNodeId + " not found in graph. Invalid ID or order?");
    }

    /**
     * Reconstruct a path from start node to end node using a predecessor map
     * @param predecessorMap Mapping of visited nodes to their respective predecessor in the graph
     * @param startNode Start node of the path
     * @param endNode End node of the path
     * @return List of node IDs from start to end node
     */
    private static List<String> reconstructPath(Map<FlowNode, FlowNode> predecessorMap, FlowNode startNode, FlowNode endNode) {
        ArrayList<String> path = new ArrayList<>();
        FlowNode currNode = endNode;
        while(!currNode.getId().equals(startNode.getId())) {
            path.add(currNode.getId());
            currNode = predecessorMap.get(currNode);
        }
        path.add(startNode.getId());

        Collections.reverse(path); // We want the list to start with the start node
        return path;
    }

    /**
     * Helper method to output the path list
     * @param path Path list
     */
    private static void outputPath(List<String> path) {
         String startNode = path.get(0);
         String endNode = path.get(path.size() - 1);
         StringBuilder pathString = new StringBuilder("[" + startNode);
         for(String node: path.subList(1, path.size())) {
             pathString.append(", " + node);
         }
         pathString.append("]");

         System.out.println("The path from " + startNode + " to " + endNode + " is:");
         System.out.println(pathString.toString());
    }
}
