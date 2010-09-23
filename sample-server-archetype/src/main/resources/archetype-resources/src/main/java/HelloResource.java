#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package};        
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

@Path("/")
@Produces("text/plain")
public class HelloResource
{
    private final HelloConfig config;

    @Inject
    public HelloResource(HelloConfig config)
    {
        this.config = config;
    }

    @GET
    public String get()
    {
        if ("es".equals(config.getLanguage())) {
            return "hola mundo";
        }
        else {
            return "hello world";
        }
    }

    @GET
    public String get()
    {
        return "hello world";
    }
}
