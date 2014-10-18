package org.jolokia.docker.maven.util;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.*;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author roland
 * @since 14.10.14
 */
public class EnvUtilTest {

    static HttpServer server;
    static int port;
    static String httpPingUrl;

    @Test
    public void httpFail() {
        long waited = EnvUtil.httpPingWait(httpPingUrl,500);
        assertTrue("Waited only " + waited + " instead of min. 500ms", waited >= 500);
    }

    @Test
    public void httpSuccess() {
        server.start();
        System.out.println("Check URL " + httpPingUrl);
        long waited = EnvUtil.httpPingWait(httpPingUrl,700);
        assertTrue("Waited longer than 500ms: " + waited,waited < 700);
        server.stop(10);
    }

    @Test
    public void splitPath() {
        List<String[]> res = EnvUtil.splitLinks(Arrays.asList("db", "postgres:9:db", "postgres:db"));
        assertEquals(3,res.size());
        String[][] expected = new String[][] {
                { "db", "db"},
                { "postgres:9","db"},
                { "postgres", "db"}
        };
        for (int i = 0; i < res.size(); i++) {
            String[] got = res.get(i);
            assertEquals(2,got.length);
            assertEquals(expected[i][0],got[0]);
            assertEquals(expected[i][1],got[1]);
        }
    }

    @Test
    public void splitOnSpace() {
        Object[] data = new Object[] {
                "bla blub", new String[] { "bla", "blub"},
                "bla\\ blub", new String[] {"bla blub"},
                "bla blub\\ blubber", new String[] { "bla", "blub blubber"}
        };
        for (int i = 0; i < data.length; i += 2) {
            String[] result = EnvUtil.splitWOnSpaceWithEscape((String) data[i]);
            String[] expected = (String[]) data[i+1];
            assertEquals(expected.length, result.length);
            for (int j = 0; j < expected.length; j++) {
                assertEquals(expected[j],result[j]);
            }
        }
    }

    @Test
    public void writePortProperties() throws IOException, MojoExecutionException {
        File propFile = File.createTempFile("dmpl-",".properties");
        propFile.deleteOnExit();

        Properties origProps = new Properties();
        origProps.setProperty("test1","bla");
        origProps.setProperty("test2","blub");
        EnvUtil.writePortProperties(origProps,propFile.getAbsolutePath());
        assertTrue(propFile.exists());

        Properties newProps = new Properties();
        newProps.load(new FileInputStream(propFile));

        assertEquals(2,newProps.size());
        assertEquals(newProps.get("test1"),"bla");
        assertEquals(newProps.get("test2"),"blub");
    }

    @BeforeClass
    public static void createServer() throws IOException {
        port = getRandomPort();
        System.out.println("Created .... " + port);
        InetAddress address = InetAddress.getLoopbackAddress();
        InetSocketAddress socketAddress = new InetSocketAddress(address,port);
        server = HttpServer.create(socketAddress, 10);

        // Prepare executor pool
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.createContext("/test/",new HttpHandler() {
            @Override
            public void handle(HttpExchange httpExchange) throws IOException {
                String method = httpExchange.getRequestMethod();
                assertEquals("HEAD", method);
                httpExchange.sendResponseHeaders(200, -1);
            }
        });
        httpPingUrl = "http://127.0.0.1:" + port + "/test/";
    }

    private static int getRandomPort() throws IOException {
        for (int port = 22332; port < 22500;port++) {
            if (trySocket(port)) {
                return port;
            }
        }
        throw new IllegalStateException("Cannot find a single free port");
    }

    private static boolean trySocket(int port) throws IOException {
        InetAddress address = Inet4Address.getByName("localhost");
        ServerSocket s = null;
        try {
            s = new ServerSocket();
            s.bind(new InetSocketAddress(address,port));
            return true;
        } catch (IOException exp) {
            System.err.println("Port " + port + " already in use, tying next ...");
            // exp.printStackTrace();
            // next try ....
        } finally {
            if (s != null) {
                s.close();
            }
        }
        return false;
    }
}
