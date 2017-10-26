// Required to serialize results
:install com.opencsv opencsv 4.0

import com.opencsv.CSVWriter
import org.janusgraph.core.JanusGraphFactory
import org.apache.commons.configuration.Configuration
import org.apache.commons.configuration.PropertiesConfiguration
import org.apache.commons.io.FileUtils
import org.apache.commons.io.LineIterator
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource
import org.apache.tinkerpop.gremlin.process.traversal.util.DefaultTraversalMetrics
import org.apache.tinkerpop.gremlin.process.traversal.util.TraversalMetrics
import org.apache.tinkerpop.gremlin.structure.Graph


import java.util.concurrent.TimeUnit

/**
 * Created by apacaci on 2/5/17.
 *
 * Helper Groovy script to run 2-hop friendship query over JanusGraph
 * It measures the time to retrieve 2-hop friendship
 */
class PartitioningTwoHopTest {

    private static String[] CSV_HEADERS = ["IID", "VERTEX_PARTITION", "TARGET_ID", "TOTAL_DURATION" ]

    static void run(Graph graph, String parametersFile, String outputFile) {

        GraphTraversalSource g = graph.traversal()

        LineIterator it = FileUtils.lineIterator(new File(parametersFile))

        // skip the first line
        it.next()

        // create the CSV Writer
        FileWriter fileWriter = new FileWriter(outputFile)
        CSVWriter csvPrinter = new CSVWriter(fileWriter)
        csvPrinter.writeNext(CSV_HEADERS)


        while(it.hasNext()) {
            // we know that query_1_param.txt has iid as first parameter
	        String current_line = it.nextLine()
            String iid = current_line.split('\\|')[0]
	        String targetId = current_line.split('\\|')[1]
            List<String> queryRecord = new ArrayList();

            try {
                DefaultTraversalMetrics metrics = g.V().has('iid', 'person:' + iid).repeat(out('knows').simplePath()).until(has('iid', 'person:' + targetId).or().loops().is(5)).limit(1).path().count(local).profile().next()
                Long vertexId = (Long) g.V().has('iid', 'person:' + iid).next().id()
                Long partitionId = getPartitionId(vertexId)

                long totalQueryDurationInMicroSeconds = metrics.getDuration(TimeUnit.MICROSECONDS)
                // index 2 corresponds to valueMap step, where properties of each neighbour is actually retrieved from backend

                queryRecord.add(iid)
                queryRecord.add(partitionId)
                queryRecord.add(targetId)
                queryRecord.add(totalQueryDurationInMicroSeconds.toString())
            } catch(Exception e) {
                // means that query failed, we still add corresponding entry to the csv
                queryRecord.add(iid)
                queryRecord.add(e.getMessage())
                queryRecord.add("NA")
                queryRecord.add("NA")
                log.warn("Query for vertex: " + iid + " could not be executed: " + e.getMessage())
            }
            // add record to CSV
            csvPrinter.writeNext(queryRecord.toArray(new String[0]))

            // since it is read-only rollback is a less expensive option
            g.tx().rollback()
        }

        // close csvWriter
        csvPrinter.close()
    }

    static void run(String graphConfigurationFile, String parametersFile, String outputFile) {
        Configuration graphConfig = new PropertiesConfiguration(graphConfigurationFile)
        Graph graph = JanusGraphFactory.open(graphConfig)
        run(graph, parametersFile, outputFile)
    }

    static Long getPartitionId(Long id) {
        // because normal user vertex padding is 3 and partitionId bound is 3
        return ( id >>> 3 ) & 3
    }

}
