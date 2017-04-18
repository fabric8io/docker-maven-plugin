package io.fabric8.dmp.samples.dockerfile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;

/**
 * @author roland
 * @since 18.04.17
 */
public class HelloWorldServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String txt = FileUtils.readFileToString(new File("/welcome.txt"), Charset.defaultCharset());
        resp.getWriter().append(txt).flush();
        resp.setHeader("Content-Type", "plain/text");
    }
}
