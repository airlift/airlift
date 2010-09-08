#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package};
import com.google.inject.Injector;
import com.proofpoint.bootstrap.Bootstrap;
import com.proofpoint.jersey.JerseyModule;
import com.proofpoint.jetty.JettyModule;
import com.proofpoint.jmx.JMXAgent;
import com.proofpoint.jmx.JMXModule;
import org.mortbay.jetty.Server;

public class Main
{
    public static void main(String[] args)
            throws Exception
    {
        Bootstrap app = new Bootstrap(new JettyModule(),
                                      new JerseyModule(),
                                      new JMXModule(),
                                      new MainModule());

        Injector injector = app.initialize();

        final Server server = injector.getInstance(Server.class);
        final JMXAgent jmxAgent = injector.getInstance(JMXAgent.class);

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable()
        {
            public void run()
            {
                try {
                    jmxAgent.stop();
                    server.stop();
                }
                catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }));

        try {
            jmxAgent.start();
            server.start();
        }
        catch (Exception e) {
            server.stop();
        }
    }
}
