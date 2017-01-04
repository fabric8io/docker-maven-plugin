/*
 *
 * Copyright 2014 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.maven.docker.util;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.Properties;

/**
 * Basic tests for File Interpolator
 *
 * @author pgier
 * @since 06.01.17
 */
public class FileInterpolatorTest {

    @Test
    public void testBasicFileInterpolation() throws Exception {
        Properties vars = new Properties();
        final String prop1Name = "testProp1";
        final String prop1Value = "value1";
        vars.setProperty(prop1Name, prop1Value);
        String content = "testing a %%" + prop1Name + "%% b testing\n";

        File dockerFileTemplate = File.createTempFile("DockerfileTest", ".template");
        dockerFileTemplate.deleteOnExit();
        FileUtils.writeStringToFile(dockerFileTemplate, content);
        File dockerFile = File.createTempFile("DockerfileTest", null);
        dockerFile.deleteOnExit();
        FileInterpolator.interpolate(dockerFileTemplate, dockerFile, vars);
        String updatedFileContent = FileUtils.readFileToString(dockerFile);

        Assert.assertTrue(updatedFileContent.contains("a " + prop1Value + " b"));
    }

    @Test
    public void testFileInterpolationMultiLine() throws Exception {
        Properties vars = new Properties();
        final String prop1Name = "test.prop.1";
        final String prop1Value = "value1";
        vars.setProperty(prop1Name, prop1Value);
        final String prop2Name = "test_prop-2.";
        final String prop2Value = "prop 2 value";
        vars.setProperty(prop2Name, prop2Value);

        String content = "testing a %%" + prop1Name + "%% b testing\n"
                + "second line junk %^&*( \n"
                + "third line %%test_prop-2.%% more stuff \n";

        File dockerFileTemplate = File.createTempFile("DockerfileTest", ".template");
        dockerFileTemplate.deleteOnExit();
        FileUtils.writeStringToFile(dockerFileTemplate, content);
        File dockerFile = File.createTempFile("DockerfileTest", null);
        dockerFile.deleteOnExit();
        FileInterpolator.interpolate(dockerFileTemplate, dockerFile, vars);
        String updatedFileContent = FileUtils.readFileToString(dockerFile);

        Assert.assertTrue(updatedFileContent.contains("a " + prop1Value + " b"));
        Assert.assertTrue(updatedFileContent.contains("line " + prop2Value + " more"));
    }

}
