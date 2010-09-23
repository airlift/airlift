#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package};

import com.proofpoint.configuration.Config;

public class HelloConfig
{
    @Config("hello.language")
    public String getLanguage()
    {
        return "en";
    }
}
