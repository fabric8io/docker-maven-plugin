package io.fabric8.maven.docker.util;
/*
 *
 * Copyright 2016 Roland Huss
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



import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import org.apache.maven.monitor.logging.DefaultLog;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.fusesource.jansi.Ansi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemOut;

/**
 * @author roland
 * @since 07/10/16
 */
@ExtendWith(SystemStubsExtension.class)
class AnsiLoggerTest {

    @SystemStub
    private SystemOut systemOut;

    @TempDir
    private Path tempDir;

    // AnsiLogger's constructor calls Ansi.setEnabled(), which is global state shared by every test
    // in the JVM, so it has to be put back the way it was found.
    private boolean ansiRestore;

    @BeforeEach
    void rememberAnsiState() {
        ansiRestore = Ansi.isEnabled();
    }

    @AfterEach
    void restoreAnsiState() {
        Ansi.setEnabled(ansiRestore);
    }

    @Test
    void emphasizeDebug() {
        TestLog testLog = new TestLog() {
            @Override
            public boolean isDebugEnabled() {
                return true;
            }
        };

        AnsiLogger logger = new AnsiLogger(testLog, true, null, false, "T>");
        logger.debug("Debug messages do not interpret [[*]]%s[[*]]", "emphasis");
        Assertions.assertEquals("T>Debug messages do not interpret [[*]]emphasis[[*]]",
                testLog.getMessage());
    }

    @Test
    void emphasizeInfoWithDebugEnabled() {
        TestLog testLog = new TestLog() {
            @Override
            public boolean isDebugEnabled() {
                return true;
            }
        };

        AnsiLogger logger = new AnsiLogger(testLog, true, null, false, "T>");
        logger.info("Info messages do not apply [[*]]%s[[*]] when debug is enabled", "color codes");
        Assertions.assertEquals("T>Info messages do not apply color codes when debug is enabled",
                testLog.getMessage());
    }

    @Test
    void verboseEnabled() {
        String[] data = {
            "build", "Test",
            "api", null,
            "bla", "log: Unknown verbosity group bla. Ignoring...",
            "all", "Test",
            "", "Test",
            "true", "Test",
            "false", null
        };
        for (int i = 0; i < data.length; i += 2) {
            TestLog testLog = new TestLog();
            AnsiLogger logger = new AnsiLogger(testLog, false, data[i], false, "");
            logger.verbose(Logger.LogVerboseCategory.BUILD, "Test");
            Assertions.assertEquals(data[i+1], testLog.getMessage());
        }
    }
    @Test

    void emphasizeInfo() {
        TestLog testLog = new TestLog();
        AnsiLogger logger = new AnsiLogger(testLog, true, "build", false, "T>");
        Ansi ansi = Ansi.ansi();
        logger.info("Yet another [[*]]Test[[*]] %s", "emphasis");
        Assertions.assertEquals(ansi.fg(AnsiLogger.COLOR_INFO)
                         .a("T>")
                         .a("Yet another ")
                         .fgBright(AnsiLogger.COLOR_EMPHASIS)
                         .a("Test")
                         .fg(AnsiLogger.COLOR_INFO)
                         .a(" emphasis")
                         .reset().toString(),
                     testLog.getMessage());
    }

    @Test
    void emphasizeInfoSpecificColor() {
        TestLog testLog = new TestLog();
        AnsiLogger logger = new AnsiLogger(testLog, true, null, false, "T>");
        Ansi ansi = new Ansi();
        logger.info("Specific [[C]]color[[C]] %s","is possible");
        Assertions.assertEquals(ansi.fg(AnsiLogger.COLOR_INFO)
                        .a("T>")
                        .a("Specific ")
                        .fg(Ansi.Color.CYAN)
                        .a("color")
                        .fg(AnsiLogger.COLOR_INFO)
                        .a(" is possible")
                        .reset().toString(),
                testLog.getMessage());
    }

    @Test
    void emphasizeInfoIgnoringEmpties() {
        TestLog testLog = new TestLog();
        AnsiLogger logger = new AnsiLogger(testLog, true, null, false, "T>");
        Ansi ansi = new Ansi();
        // Note that the closing part of the emphasis does not need to match the opening.
        // E.g. [[b]]Blue[[*]] works just like [[b]]Blue[[b]]
        logger.info("[[b]][[*]]Skip[[*]][[*]]ping [[m]]empty strings[[/]] %s[[*]][[c]][[c]][[*]]","is possible");
        Assertions.assertEquals(ansi.fg(AnsiLogger.COLOR_INFO)
                        .a("T>")
                        .a("Skipping ")
                        .fgBright(Ansi.Color.MAGENTA)
                        .a("empty strings")
                        .fg(AnsiLogger.COLOR_INFO)
                        .a(" is possible")
                        .reset().toString(),
                testLog.getMessage());
    }

    @Test
    void emphasizeInfoSpecificBrightColor() {
        TestLog testLog = new TestLog();
        AnsiLogger logger = new AnsiLogger(testLog, true, null, false, "T>");
        Ansi ansi = new Ansi();
        logger.info("Lowercase enables [[c]]bright version[[c]] of %d colors",Ansi.Color.values().length - 1);
        Assertions.assertEquals(ansi.fg(AnsiLogger.COLOR_INFO)
                        .a("T>")
                        .a("Lowercase enables ")
                        .fgBright(Ansi.Color.CYAN)
                        .a("bright version")
                        .fg(AnsiLogger.COLOR_INFO)
                        .a(" of 8 colors")
                        .reset().toString(),
                testLog.getMessage());
    }

    @Test
    void emphasizeInfoWithoutColor() {
        TestLog testLog = new TestLog();
        AnsiLogger logger = new AnsiLogger(testLog, false, null, false, "T>");
        logger.info("Disabling color causes logger to [[*]]interpret and remove[[*]] %s","emphasis");
        Assertions.assertEquals("T>Disabling color causes logger to interpret and remove emphasis",
                     testLog.getMessage());
    }

    @Test
    void emphasizeWarning() {
        TestLog testLog = new TestLog();
        AnsiLogger logger = new AnsiLogger(testLog, true, null, false, "T>");
        Ansi ansi = new Ansi();
        logger.warn("%s messages support [[*]]emphasis[[*]] too","Warning");
        Assertions.assertEquals(ansi.fg(AnsiLogger.COLOR_WARNING)
                         .a("T>")
                         .a("Warning messages support ")
                         .fgBright(AnsiLogger.COLOR_EMPHASIS)
                         .a("emphasis")
                         .fg(AnsiLogger.COLOR_WARNING)
                         .a(" too")
                         .reset().toString(),
                     testLog.getMessage());
    }

    @Test
    void emphasizeError() {
        TestLog testLog = new TestLog();
        AnsiLogger logger = new AnsiLogger(testLog, true, null, false, "T>");
        Ansi ansi = new Ansi();
        logger.error("Error [[*]]messages[[*]] could emphasise [[*]]%s[[*]]","many things");
        Assertions.assertEquals(ansi.fg(AnsiLogger.COLOR_ERROR)
                         .a("T>")
                         .a("Error ")
                         .fgBright(AnsiLogger.COLOR_EMPHASIS)
                         .a("messages")
                         .fg(AnsiLogger.COLOR_ERROR)
                         .a(" could emphasise ")
                         .fgBright(AnsiLogger.COLOR_EMPHASIS)
                         .a("many things")
                         .reset()
                         .toString(),
                     testLog.getMessage());
    }


    // ---------------------------------------------------------------------------------------------
    // Progress bar
    //
    // These write straight to System.out, so they use QuietLog rather than TestLog: TestLog delegates
    // to a ConsoleLogger, which would print "[INFO] ..." into the very stream being captured.

    @Test
    void progressStartWritesNothingItself() {
        AnsiLogger logger = new AnsiLogger(new QuietLog(), true, null, false, "T>");

        logger.progressStart();

        Assertions.assertEquals("", systemOut.getText());
    }

    @Test
    void progressUpdateAnsiPrintsColouredLineForNewLayer() {
        AnsiLogger logger = new AnsiLogger(new QuietLog(), true, null, false, "T>");
        logger.progressStart();

        logger.progressUpdate("l1", "Downloading", "[===>   ]");

        String expected = Ansi.ansi()
            .fg(AnsiLogger.COLOR_PROGRESS_ID).a("l1").reset().a(": ")
            .fg(AnsiLogger.COLOR_PROGRESS_STATUS).a("Downloading ")
            .fg(AnsiLogger.COLOR_PROGRESS_BAR).a("[===>   ]").toString();
        Assertions.assertEquals(expected + System.lineSeparator(), systemOut.getText());
    }

    @Test
    void progressUpdateAnsiMovesTheCursorForAKnownLayer() {
        AnsiLogger logger = new AnsiLogger(new QuietLog(), true, null, false, "T>");
        logger.progressStart();
        logger.progressUpdate("l1", "Downloading", "[===>   ]");
        systemOut.clear();

        // Second update for the same layer: the line already exists, so the cursor is moved up over
        // it, the line is redrawn and the cursor moved back down again.
        logger.progressUpdate("l1", "Extracting", "[=====> ]");

        String output = systemOut.getText();
        Assertions.assertTrue(output.startsWith(Ansi.ansi().cursorUp(1).eraseLine(Ansi.Erase.ALL).toString()),
            "Expected the cursor to be moved up over the existing line, but got: " + output);
        Assertions.assertTrue(output.endsWith(Ansi.ansi().cursorDown(0).toString()),
            "Expected the cursor to be moved back down, but got: " + output);
    }

    @Test
    void progressUpdateAnsiTreatsAMissingProgressMessageAsEmpty() {
        AnsiLogger logger = new AnsiLogger(new QuietLog(), true, null, false, "T>");
        logger.progressStart();

        logger.progressUpdate("l1", "Extracting", null);

        String expected = Ansi.ansi()
            .fg(AnsiLogger.COLOR_PROGRESS_ID).a("l1").reset().a(": ")
            .fg(AnsiLogger.COLOR_PROGRESS_STATUS).a("Extracting  ")
            .fg(AnsiLogger.COLOR_PROGRESS_BAR).a("").toString();
        Assertions.assertEquals(expected + System.lineSeparator(), systemOut.getText());
    }

    @Test
    void progressFinishedAnsiResetsTheColour() {
        AnsiLogger logger = new AnsiLogger(new QuietLog(), true, null, false, "T>");
        logger.progressStart();
        systemOut.clear();

        logger.progressFinished();

        Assertions.assertEquals(Ansi.ansi().reset().toString(), systemOut.getText());
    }

    @Test
    void progressUpdateWithoutAnsiPrintsAHashPerPeriod() {
        AnsiLogger logger = new AnsiLogger(new QuietLog(), false, null, false, "T>");
        logger.progressStart();

        // Only the first update of each period prints, so two updates still produce a single hash.
        logger.progressUpdate("l1", "Downloading", "ignored");
        logger.progressUpdate("l1", "Downloading", "ignored");

        Assertions.assertEquals("#", systemOut.getText());
    }

    @Test
    void progressUpdateWithoutAnsiWrapsTheLine() {
        AnsiLogger logger = new AnsiLogger(new QuietLog(), false, null, false, "T>");
        logger.progressStart();

        // A hash every 80 updates, a line break every 80 hashes: 6401 updates is one past the wrap.
        for (int i = 0; i <= 6400; i++) {
            logger.progressUpdate("l1", "Downloading", "ignored");
        }

        String output = systemOut.getText();
        Assertions.assertEquals(81, output.chars().filter(c -> c == '#').count());
        Assertions.assertEquals(1, output.chars().filter(c -> c == '\n').count());
    }

    @Test
    void progressFinishedWithoutAnsiEndsTheLine() {
        AnsiLogger logger = new AnsiLogger(new QuietLog(), false, null, false, "T>");
        logger.progressStart();
        systemOut.clear();

        logger.progressFinished();

        Assertions.assertEquals(System.lineSeparator(), systemOut.getText());
    }

    @Test
    void progressUpdateWithoutAStartDoesNotFail() {
        AnsiLogger ansi = new AnsiLogger(new QuietLog(), true, null, false, "T>");
        AnsiLogger nonAnsi = new AnsiLogger(new QuietLog(), false, null, false, "T>");

        // progressStart() is what used to populate the ThreadLocals, so skipping it used to NPE.
        Assertions.assertDoesNotThrow(() -> ansi.progressUpdate("l1", "Downloading", "[===>   ]"));
        Assertions.assertDoesNotThrow(() -> nonAnsi.progressUpdate("l1", "Downloading", "[===>   ]"));
    }

    @Test
    void progressFinishedResetsTheUpdateCounter() {
        AnsiLogger logger = new AnsiLogger(new QuietLog(), false, null, false, "T>");
        logger.progressStart();
        logger.progressUpdate("l1", "Downloading", "ignored");
        logger.progressFinished();
        systemOut.clear();

        // The counter is back to zero, so this first update of the new run prints its hash again.
        logger.progressUpdate("l1", "Downloading", "ignored");

        Assertions.assertEquals("#", systemOut.getText());
    }

    @Test
    void progressIsSuppressedInBatchMode() {
        AnsiLogger logger = new AnsiLogger(new QuietLog(), true, null, true, "T>");

        logger.progressStart();
        logger.progressUpdate("l1", "Downloading", "[===>   ]");
        logger.progressFinished();

        Assertions.assertEquals("", systemOut.getText());
    }

    @Test
    void progressIsSuppressedWhenInfoIsDisabled() {
        AnsiLogger logger = new AnsiLogger(new QuietLog(false), true, null, false, "T>");

        logger.progressStart();
        logger.progressUpdate("l1", "Downloading", "[===>   ]");
        logger.progressFinished();

        Assertions.assertEquals("", systemOut.getText());
    }

    @Test
    void progressUpdateIgnoresAnEmptyLayerId() {
        AnsiLogger logger = new AnsiLogger(new QuietLog(), true, null, false, "T>");
        logger.progressStart();

        logger.progressUpdate("", "Downloading", "[===>   ]");
        logger.progressUpdate(null, "Downloading", "[===>   ]");

        Assertions.assertEquals("", systemOut.getText());
    }

    // ---------------------------------------------------------------------------------------------
    // Output file

    @Test
    void writesPlainMessagesToTheOutputFile() throws IOException {
        File outputFile = tempDir.resolve("build.log").toFile();
        AnsiLogger logger = new AnsiLogger(new QuietLog(), true, null, false, "T>", outputFile);

        logger.info("Info %s", "message");
        logger.warn("Warn message");
        logger.error("Error message");
        // PrintWriter buffers, so nothing reaches the file until the logger is closed.
        logger.close();

        // Neither the prefix nor the colouring is applied on the way to the file.
        Assertions.assertEquals(Arrays.asList("Info message", "Warn message", "Error message"),
            Files.readAllLines(outputFile.toPath()));
    }

    @Test
    void anOutputFileForcesBatchMode() throws IOException {
        File outputFile = tempDir.resolve("build.log").toFile();
        AnsiLogger logger = new AnsiLogger(new QuietLog(), true, null, false, "T>", outputFile);

        logger.progressStart();
        logger.progressUpdate("l1", "Downloading", "[===>   ]");
        logger.progressFinished();
        logger.close();

        Assertions.assertEquals("", systemOut.getText());
    }

    @Test
    void debugAndVerboseAlsoGoToTheOutputFile() throws IOException {
        File outputFile = tempDir.resolve("build.log").toFile();
        AnsiLogger logger = new AnsiLogger(new QuietLog(true, true), true, "all", false, "T>", outputFile);

        logger.debug("Debug message");
        logger.verbose(Logger.LogVerboseCategory.BUILD, "Verbose message");
        logger.close();

        Assertions.assertEquals(Arrays.asList("Debug message", "Verbose message"),
            Files.readAllLines(outputFile.toPath()));
    }

    @Test
    void nothingIsWrittenToTheOutputFileWhenTheLevelIsDisabled() throws IOException {
        File outputFile = tempDir.resolve("build.log").toFile();
        AnsiLogger logger = new AnsiLogger(new QuietLog(false), true, null, false, "T>", outputFile);

        logger.info("Info message");
        logger.close();

        Assertions.assertEquals(Collections.emptyList(), Files.readAllLines(outputFile.toPath()));
    }

    @Test
    void aMissingParentDirectoryOfTheOutputFileIsCreated() throws IOException {
        File outputFile = tempDir.resolve("logs").resolve("nested").resolve("build.log").toFile();
        AnsiLogger logger = new AnsiLogger(new QuietLog(), true, null, false, "T>", outputFile);

        logger.info("Info message");
        logger.close();

        Assertions.assertEquals(Collections.singletonList("Info message"), Files.readAllLines(outputFile.toPath()));
    }

    @Test
    void anUnusableOutputFileIsReportedRatherThanSwallowed() {
        // A directory can never be opened for writing, so this stands in for any unusable path.
        File outputFile = tempDir.toFile();

        UncheckedIOException exception = Assertions.assertThrows(UncheckedIOException.class,
            () -> new AnsiLogger(new QuietLog(), true, null, false, "T>", outputFile));

        Assertions.assertInstanceOf(FileNotFoundException.class, exception.getCause(),
            "Expected the original FileNotFoundException to be kept as the cause");
    }

    @Test
    void anUnusableOutputFileLeavesTheGlobalAnsiStateAlone() {
        Ansi.setEnabled(!Ansi.isEnabled());
        boolean before = Ansi.isEnabled();

        Assertions.assertThrows(UncheckedIOException.class,
            () -> new AnsiLogger(new QuietLog(), true, null, false, "T>", tempDir.toFile()));

        // The output file is opened before the colour setup, so a failure must not switch this global.
        Assertions.assertEquals(before, Ansi.isEnabled());
    }

    @Test
    void closeIsSafeWithoutAnOutputFile() {
        AnsiLogger logger = new AnsiLogger(new QuietLog(), true, null, false, "T>");

        Assertions.assertDoesNotThrow(logger::close);
    }

    // ---------------------------------------------------------------------------------------------
    // Delegation, formatting and verbosity

    @Test
    void debugIsSuppressedWhenDisabled() {
        TestLog testLog = new TestLog();

        new AnsiLogger(testLog, true, null, false, "T>").debug("Not logged");

        Assertions.assertEquals(null, testLog.getMessage());
    }

    @Test
    void errorMessageIsColouredWithoutThePrefix() {
        AnsiLogger logger = new AnsiLogger(new TestLog(), true, null, false, "T>");
        Ansi ansi = new Ansi();

        String message = logger.errorMessage("Boom");

        Assertions.assertEquals(ansi.fg(AnsiLogger.COLOR_ERROR).a("Boom").reset().toString(), message);
    }

    @ParameterizedTest
    @CsvSource(value = {
        "NIL, false",
        "false, false",
        "'', true",
        "true, true",
        "all, true",
        "build, true"
    }, nullValues = "NIL")
    void isVerboseEnabledReflectsTheConfiguration(String verbose, boolean expected) {
        AnsiLogger logger = new AnsiLogger(new TestLog(), false, verbose, false, "T>");

        Assertions.assertEquals(expected, logger.isVerboseEnabled());
    }

    @Test
    void aSingleThrowableParameterIsAppendedToTheMessage() {
        TestLog testLog = new TestLog();
        AnsiLogger logger = new AnsiLogger(testLog, false, null, false, "T>");

        logger.info("Failed", new IllegalStateException("boom"));

        Assertions.assertEquals("T>Failed: java.lang.IllegalStateException: boom", testLog.getMessage());
    }

    @Test
    void theShorterConstructorsApplyTheDefaults() {
        TestLog threeArgs = new TestLog();
        TestLog fourArgs = new TestLog();

        // Neither overload takes a prefix, so both fall back to the default one.
        new AnsiLogger(threeArgs, false, null).info("Three");
        new AnsiLogger(fourArgs, false, null, false).info("Four");

        Assertions.assertEquals("DOCKER> Three", threeArgs.getMessage());
        Assertions.assertEquals("DOCKER> Four", fourArgs.getMessage());
    }

    @Test
    void anUnknownEmphasisColourIsDropped() {
        TestLog testLog = new TestLog();
        AnsiLogger logger = new AnsiLogger(testLog, false, null, false, "T>");

        // "/" is not a colour id, so the emphasis contributes no colour at all.
        logger.info("Unknown [[/]]colour[[/]] ids are ignored");

        Assertions.assertEquals("T>Unknown colour ids are ignored", testLog.getMessage());
    }

    @Test
    void severalParametersAreFormattedIntoTheMessage() {
        TestLog testLog = new TestLog();
        AnsiLogger logger = new AnsiLogger(testLog, false, null, false, "T>");

        logger.info("%s-%s", "a", "b");

        Assertions.assertEquals("T>a-b", testLog.getMessage());
    }

    /**
     * A log that records nothing and, unlike {@link TestLog}, never writes to System.out - which
     * matters for the tests that capture what AnsiLogger itself prints there.
     */
    private static class QuietLog extends DefaultLog {
        private final boolean infoEnabled;
        private final boolean debugEnabled;

        QuietLog() {
            this(true, false);
        }

        QuietLog(boolean infoEnabled) {
            this(infoEnabled, false);
        }

        QuietLog(boolean infoEnabled, boolean debugEnabled) {
            super(new ConsoleLogger());
            this.infoEnabled = infoEnabled;
            this.debugEnabled = debugEnabled;
        }

        @Override
        public boolean isDebugEnabled() {
            return debugEnabled;
        }

        @Override
        public boolean isInfoEnabled() {
            return infoEnabled;
        }

        @Override
        public boolean isWarnEnabled() {
            return infoEnabled;
        }

        @Override
        public boolean isErrorEnabled() {
            return infoEnabled;
        }

        @Override
        public void debug(CharSequence content) {
            // Deliberately silent
        }

        @Override
        public void info(CharSequence content) {
            // Deliberately silent
        }

        @Override
        public void warn(CharSequence content) {
            // Deliberately silent
        }

        @Override
        public void error(CharSequence content) {
            // Deliberately silent
        }
    }

    private class TestLog extends DefaultLog {
        private String message;

        public TestLog() {
            super(new ConsoleLogger());
        }

        @Override
        public void debug(CharSequence content) {
            this.message = content.toString();
            super.debug(content);
        }

        @Override
        public void info(CharSequence content) {
            this.message = content.toString();
            super.info(content);
        }

        @Override
        public void warn(CharSequence content) {
            this.message = content.toString();
            super.warn(content);
        }

        @Override
        public void error(CharSequence content) {
            this.message = content.toString();
            super.error(content);
        }

        void reset() {
            message = null;
        }

        public String getMessage() {
            return message;
        }
    }

}
