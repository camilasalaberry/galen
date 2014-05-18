/*******************************************************************************
* Copyright 2014 Ivan Shubin http://mindengine.net
* 
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
* 
*   http://www.apache.org/licenses/LICENSE-2.0
* 
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
******************************************************************************/
package net.mindengine.galen;

import static java.util.Arrays.asList;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import net.mindengine.galen.browser.SeleniumBrowserFactory;
import net.mindengine.galen.config.GalenConfig;
import net.mindengine.galen.reports.ConsoleReportingListener;
import net.mindengine.galen.reports.GalenTestInfo;
import net.mindengine.galen.reports.HtmlReportBuilder;
import net.mindengine.galen.reports.TestNgReportBuilder;
import net.mindengine.galen.reports.TestReport;
import net.mindengine.galen.runner.CombinedListener;
import net.mindengine.galen.runner.CompleteListener;
import net.mindengine.galen.runner.GalenArguments;
import net.mindengine.galen.runner.JsTestCollector;
import net.mindengine.galen.runner.SuiteListener;
import net.mindengine.galen.runner.TestListener;
import net.mindengine.galen.suite.GalenPageAction;
import net.mindengine.galen.suite.GalenPageTest;
import net.mindengine.galen.suite.actions.GalenPageActionCheck;
import net.mindengine.galen.suite.reader.GalenSuiteReader;
import net.mindengine.galen.tests.GalenBasicTest;
import net.mindengine.galen.tests.GalenTest;
import net.mindengine.galen.tests.TestSession;
import net.mindengine.galen.validation.FailureListener;

import org.apache.commons.cli.ParseException;
import org.apache.commons.io.IOUtils;

public class GalenMain {
    
    private CompleteListener listener;

    public void execute(GalenArguments arguments) throws IOException, SecurityException, IllegalArgumentException, ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        if (arguments.getAction() != null) {
            
            FailureListener failureListener = new FailureListener();
            CombinedListener combinedListener = createListeners(arguments);
            combinedListener.add(failureListener);
            if (listener != null) {
                combinedListener.add(listener);
            }
            
            if ("test".equals(arguments.getAction())) {
                runTests(arguments, combinedListener);
            }
            else if ("check".equals(arguments.getAction())) {
                performCheck(arguments, combinedListener);
            }
            else if ("config".equals(arguments.getAction())) {
                performConfig();
            }
            combinedListener.done();
            
            if (GalenConfig.getConfig().getUseFailExitCode()){
                if (failureListener.hasFailures()) {
                    System.exit(1);
                }
            }
        }
        else {
            if (arguments.getPrintVersion()) {
                
                System.out.println("Galen Framework");
                
                String version = getClass().getPackage().getImplementationVersion();
                if (version == null) {
                    version = "unknown";
                }
                else {
                    version = version.replace("-SNAPSHOT", "");
                }
                System.out.println("Version: " + version);
            }
        }
    }

    public void performConfig() throws IOException {
        File file = new File("config");
        
        if (!file.exists()) {
            file.createNewFile();
            FileOutputStream fos = new FileOutputStream(file);
            
            StringWriter writer = new StringWriter();
            IOUtils.copy(getClass().getResourceAsStream("/config-template.conf"), writer, "UTF-8");
            IOUtils.write(writer.toString(), fos, "UTF-8");
            fos.flush();
            fos.close();
            System.out.println("Created config file");
        }
        else {
            System.out.println("Config file already exists");
        }
    }

    private CombinedListener createListeners(GalenArguments arguments) throws IOException, SecurityException, IllegalArgumentException, ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        CombinedListener combinedListener = new CombinedListener();
        combinedListener.add(new ConsoleReportingListener(System.out, System.out));
        
        //Adding all user defined listeners
        List<CompleteListener> configuredListeners = getConfiguredListeners();
        for (CompleteListener configuredListener : configuredListeners) {
            combinedListener.add(configuredListener);
        }
        return combinedListener;
    }
    
    @SuppressWarnings("unchecked")
    public List<CompleteListener> getConfiguredListeners() throws ClassNotFoundException, SecurityException, NoSuchMethodException, IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException {
        List<CompleteListener> configuredListeners = new LinkedList<CompleteListener>();
        List<String> classNames = GalenConfig.getConfig().getReportingListeners();
        
        for (String className : classNames) {
            Constructor<CompleteListener> constructor = (Constructor<CompleteListener>) Class.forName(className).getConstructor();
            configuredListeners.add(constructor.newInstance());
        }
        return configuredListeners;
    }

    private void performCheck(GalenArguments arguments, CombinedListener listener) throws IOException {
        verifyArgumentsForPageCheck(arguments);
        
        List<GalenTest> galenTests = new LinkedList<GalenTest>();
        
        for (String pageSpecPath : arguments.getPaths()) {
            GalenBasicTest test = new GalenBasicTest();
            test.setName(pageSpecPath);
            test.setPageTests(asList(new GalenPageTest()
                .withUrl(arguments.getUrl())
                .withSize(arguments.getScreenSize())
                .withBrowserFactory(new SeleniumBrowserFactory(SeleniumBrowserFactory.FIREFOX))
                .withActions(asList((GalenPageAction)new GalenPageActionCheck()
                    .withSpecs(asList(pageSpecPath))
                    .withIncludedTags(arguments.getIncludedTags())
                    .withExcludedTags(arguments.getExcludedTags())
                    .withOriginalCommand(arguments.getOriginal()))
                )));
            galenTests.add(test);
        }
        
        runTests(arguments, galenTests, listener);
    }

    private void verifyArgumentsForPageCheck(GalenArguments arguments) {
        if (arguments.getUrl() == null) {
            throw new IllegalArgumentException("Url is not specified");
        }
        
        if (arguments.getScreenSize() == null) {
            throw new IllegalArgumentException("Screen size is not specified");
        }
        
        if (arguments.getPaths().size() < 1) {
            throw new IllegalArgumentException("There are no specs specified");
        }
        
    }

    public static void main(String[] args) throws ParseException, IOException, SecurityException, IllegalArgumentException, ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        new GalenMain().execute(GalenArguments.parse(args));
    }

    private void runTests(GalenArguments arguments, CompleteListener listener) throws IOException {
        List<File> basicTestFiles = new LinkedList<File>();
        List<File> jsTestFiles = new LinkedList<File>();
        
        for (String path : arguments.getPaths()) {
            File file = new File(path);
            if (file.exists()) {
                if (file.isDirectory()) {
                    searchForTests(file, arguments.getRecursive(), basicTestFiles, jsTestFiles);
                }
                else if (file.isFile()) {
                    String name = file.getName().toLowerCase();
                    if (name.endsWith(".test")) {
                        basicTestFiles.add(file);
                    }
                    else if (name.endsWith(".test.js")) {
                        jsTestFiles.add(file);
                    } 
                }
            }
            else {
                throw new FileNotFoundException(path);
            }
        }
        
        if (basicTestFiles.size() > 0 || jsTestFiles.size() > 0) {
            runTestFiles(basicTestFiles, jsTestFiles, listener, arguments);
        }
        else {
            throw new RuntimeException("Couldn't find any test files");
        }
    }

    private void runTestFiles(List<File> basicTestFiles, List<File> jsTestFiles, CompleteListener listener, GalenArguments arguments) throws IOException {
        GalenSuiteReader reader = new GalenSuiteReader();
        
        List<GalenTest> tests = new LinkedList<GalenTest>();
        for (File file : basicTestFiles) {
            tests.addAll(reader.read(file));
        }
        
        JsTestCollector testCollector = new JsTestCollector(tests);
        for (File jsFile: jsTestFiles) {
            testCollector.execute(jsFile);
        }
        
        
        runTests(arguments, tests, listener);
    }

    private void runTests(GalenArguments arguments, List<GalenTest> tests, CompleteListener listener) {
        
        if (arguments.getParallelSuites() > 1) {
            runTestsInThreads(tests, arguments, listener, arguments.getParallelSuites());
        }
        else {
            runTestsInThreads(tests, arguments, listener, 1);
        }
    }

    private void runTestsInThreads(List<GalenTest> tests, GalenArguments arguments, final CompleteListener listener, int amountOfThreads) {
        ExecutorService executor = Executors.newFixedThreadPool(amountOfThreads);
        
        Pattern filterPattern = createTestFilter(arguments.getFilter());
        final List<GalenTestInfo> testInfos = new LinkedList<GalenTestInfo>();
        
        tellBeforeTestSuite(listener, tests);
        
        for (final GalenTest test : tests) {
            if (matchesPattern(test.getName(), filterPattern)) {
                Runnable thread = new Runnable() {
                    @Override
                    public void run() {
                        
                        GalenTestInfo info = new GalenTestInfo();
                        testInfos.add(info);
                        info.setName(test.getName());
                        
                        info.setStartedAt(new Date());
                        TestReport report = new TestReport();
                        info.setReport(report);
                        
                        TestSession.register(info);
                        
                        tellTestStarted(listener, test);
                        
                        try {
                            test.execute(report, listener);
                        }
                        catch(Throwable ex) {
                            info.setException(ex);
                            report.error(ex);
                        }
                        info.setEndedAt(new Date());
                        
                        tellTestFinished(listener, test);
                        
                        TestSession.clear();
                    }
                };
                executor.execute(thread);
            }
        }
        executor.shutdown();
        while (!executor.isTerminated()) {
        }
        
        tellAfterTestSuite(listener, testInfos);
        
        createAllReports(testInfos, arguments);
    }
    
    private void tellBeforeTestSuite(CompleteListener listener, List<GalenTest> tests) {
        if (listener != null) {
            try {
                listener.beforeTestSuite(tests);
            }
            catch(Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private void tellAfterTestSuite(SuiteListener listener, List<GalenTestInfo> testInfos) {
        if (listener != null) {
            try {
                listener.afterTestSuite(testInfos);
            }
            catch(Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private void tellTestFinished(TestListener testListener, GalenTest test) {
        try {
            if (testListener != null) {
                testListener.onTestFinished(test);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void tellTestStarted(TestListener testListener, GalenTest test) {
        try {
            if (testListener != null) {
                testListener.onTestStarted(test);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createAllReports(List<GalenTestInfo> testInfos, GalenArguments arguments) {
        if (arguments.getTestngReport() != null) {
            createTestngReport(arguments.getTestngReport(), testInfos);
        }
        if (arguments.getHtmlReport() != null) {
            createHtmlReport(arguments.getHtmlReport(), testInfos);
        }
    }

    private void createHtmlReport(String htmlReportPath, List<GalenTestInfo> testInfos) {
        try {
            new HtmlReportBuilder().build(testInfos, htmlReportPath);
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void createTestngReport(String testngReport, List<GalenTestInfo> testInfos) {
        try {
            new TestNgReportBuilder().build(testInfos, testngReport);
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    

    private boolean matchesPattern(String name, Pattern filterPattern) {
        if (filterPattern != null) {
            return filterPattern.matcher(name).matches();
        }
        else return true;
    }
    
    private Pattern createTestFilter(String filter) {
        return filter != null ? Pattern.compile(filter.replace("*", ".*")) : null;
    }


    private void searchForTests(File file, boolean recursive, List<File> files, List<File> jsFiles) {
        
        String fileName = file.getName().toLowerCase();
        if (file.isFile()) {
            if (fileName.endsWith(".test")) {
                files.add(file);
            }
            else if (fileName.endsWith(".test.js")) {
                jsFiles.add(file);
            }
        }
        else if (file.isDirectory()) {
            for (File childFile : file.listFiles()) {
                searchForTests(childFile, recursive, files, jsFiles);
            }
        }
    }

    public CompleteListener getListener() {
        return listener;
    }

    public void setListener(CompleteListener listener) {
        this.listener = listener;
    }

}
