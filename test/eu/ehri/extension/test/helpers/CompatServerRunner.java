package eu.ehri.extension.test.helpers;

import com.tinkerpop.blueprints.TransactionalGraph;
import com.tinkerpop.blueprints.impls.neo4j.Neo4jGraph;
import com.tinkerpop.frames.FramedGraph;
import com.tinkerpop.frames.FramedGraphFactory;
import com.tinkerpop.frames.modules.javahandler.JavaHandlerModule;
import eu.ehri.project.test.utils.GraphCleaner;
import eu.ehri.project.utils.fixtures.FixtureLoader;
import eu.ehri.project.utils.fixtures.FixtureLoaderFactory;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.kernel.GraphDatabaseAPI;
import org.neo4j.kernel.logging.ConsoleLogger;
import org.neo4j.kernel.logging.BufferingConsoleLogger;
import org.neo4j.server.CommunityNeoServer;
import org.neo4j.server.WrappingNeoServerBootstrapper;
import org.neo4j.server.configuration.Configurator;
import org.neo4j.server.configuration.ServerConfigurator;

import java.io.File;


/**
 * Utility class to obtain a server. This only exists
 * due to a Scala problem that completely breaks front-end
 * testing. I *think* the problem is this Scala compile bug,
 * due to be fixed in 2.10.4:
 * <p/>
 * https://issues.scala-lang.org/browse/SI-7439
 *
 * When that fix is in we should be able to use the backend's
 * server runner class, which uses a non-deprecated and somewhat
 * more elegant ServerBuilder method, and a proper impermanent
 * database.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
public class CompatServerRunner extends WrappingNeoServerBootstrapper {

    // Graph factory.
    final static FramedGraphFactory graphFactory = new FramedGraphFactory(new JavaHandlerModule());


    private static FramedGraph<Neo4jGraph> framedGraph;
    private static FixtureLoader fixtureLoader = null;
    private static GraphCleaner<? extends TransactionalGraph> graphCleaner = null;
    private boolean isRunning = false;

    public CompatServerRunner(GraphDatabaseAPI graphDatabase, ServerConfigurator config) {
        super(graphDatabase, config);
        framedGraph = graphFactory.create((new Neo4jGraph(graphDatabase)));
        fixtureLoader = FixtureLoaderFactory.getInstance(framedGraph);
        graphCleaner = new GraphCleaner(framedGraph);
    }

    /**
     * Initialise a new Neo4j Server with the given db name and port.
     */
    public static CompatServerRunner getInstance(String dbName, Integer dbPort) {
        // TODO: Work out a better way to configure the path
        final String dbPath = "target/tmpdb_" + dbName;
        GraphDatabaseAPI graphDatabase = (GraphDatabaseAPI) new GraphDatabaseFactory()
                .newEmbeddedDatabase(dbPath);

        // Initialize the fixture loader and cleaner
        // Server configuration. TODO: Work out how to disable server startup
        // and load logging so the test output isn't so noisy...
        ServerConfigurator config = new ServerConfigurator(graphDatabase);
        config.configuration().setProperty("org.neo4j.server.webserver.port",
                dbPort);
        config.configuration().setProperty("org.neo4j.dbpath", dbPath);

        // FIXME: Work out how to turn off server logging. The config below
        // doesn't
        // work but I'm leaving it in place so I know what's been tried!
        config.configuration().setProperty(
                "java.util.logging.ConsoleHandler.level", "OFF");
        config.configuration().setProperty("org.neo4j.server.logging.level",
                "ERROR");

        return new CompatServerRunner(graphDatabase, config);
    }


    public boolean isRunning() {
        return isRunning;
    }

    @Override
    public Integer start() {
        isRunning = true;
        return super.start();
    }

    @Override
    public int stop(int stopArg) {
        isRunning = false;
        return super.stop(stopArg);
    }

    public void setUpData() {
        if (fixtureLoader != null) {
            fixtureLoader.loadTestData();
        }
    }

    public void tearDownData() {
        if (graphCleaner != null) {
            graphCleaner.clean();
        }
    }

    /**
     * Get the configurator for the test db. This allows adjusting config before
     * starting it up.
     */
    public Configurator getConfigurator() {
        return createConfigurator(new BufferingConsoleLogger());
    }

    @Override
    protected void addShutdownHook() {
        Runtime.getRuntime()
                .addShutdownHook(new Thread() {
                    @Override
                    public void run() {
                        if (server != null && isRunning) {
                            server.stop();
                        }
                        deleteFolder(
                                new File(
                                        getConfigurator()
                                                .configuration()
                                                .getString("org.neo4j.dbpath")));
                    }
                });
    }

    /**
     * Function for deleting an entire database folder. USE WITH CARE!!!
     */
    private static void deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if (files != null) { // some JVMs return null for empty dirs
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteFolder(f);
                } else {
                    f.delete();
                }
            }
        }
        folder.delete();
    }
}
