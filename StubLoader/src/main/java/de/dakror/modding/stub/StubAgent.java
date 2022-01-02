package de.dakror.modding.stub;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;

public class StubAgent {
    static final Map<String, IAgent> agents = findModLoader();
    
    private static final String AGENT_CLASS = System.getProperty("de.dakror.modding.agent.class", "de.dakror.modding.agent.ModAgent");
    private static final String AGENT_URL = System.getProperty("de.dakror.modding.agent.url", "ModLoader.jar");

    public static void premain(String agentArgs, Instrumentation inst) throws Exception {
        for (IAgent agent: agents.values()) {
            agent.premain(agentArgs, inst);
        }
    }

    public static void agentmain(String agentArgs, Instrumentation inst) throws Exception {
        for (IAgent agent: agents.values()) {
            agent.agentmain(agentArgs, inst);
        }
    }

    private static Map<String, IAgent> findModLoader() {
        final URL stubLocation = StubClassLoader.class.getProtectionDomain().getCodeSource().getLocation();
        Map<String, IAgent> agents = new HashMap<>();
        
        try {
            // Try to find a loader in an adjacent file called (by default) ModLoader.jar
            tryLoading(stubLocation.toURI().resolve(AGENT_URL).toURL(), AGENT_CLASS, agents);
    
            // Try to find a loader just using the current classpath
            tryLoading(null, AGENT_CLASS, agents);
    
            // Try to find a loader from the location of the stub
            tryLoading(stubLocation, AGENT_CLASS, agents);
        } catch (Throwable e) {
            System.err.print("While finding modloader agents: ");
            e.printStackTrace();
        }
        return agents;
    }

    public static void tryLoading(URL url, String agentName, Map<String, IAgent> agents) {
        ClassLoader loader = url == null ? StubAgent.class.getClassLoader() : new StubLoader(new URL[] {url}, ClassLoader.getPlatformClassLoader());

        try {
            Class<? extends IAgent> agentClass = loader.loadClass(agentName).asSubclass(IAgent.class);
            IAgent agent = agentClass.getConstructor().newInstance();
            agents.putIfAbsent(agent.getName(), agent); // prioritize the first one loaded of that name
        } catch (ClassNotFoundException cnfe) {
            // This is not surprising, don't be noisy
        } catch (Throwable e) {
            if (e instanceof InvocationTargetException) {
                e = ((InvocationTargetException)e).getTargetException();
            }
            System.err.print("While loading agent "+agentName+" from "+url+": ");
            e.printStackTrace();
        }
    }

    public interface IAgent {
        String getName(); // just a name for this agent to make sure we don't load two of the same agent
        void premain(String agentArgs, Instrumentation inst) throws Exception;
        void agentmain(String agentArgs, Instrumentation inst) throws Exception;
    }

    private static class StubLoader extends URLClassLoader {
        public StubLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }
    }
}
