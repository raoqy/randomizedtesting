package com.carrotsearch.ant.tasks.junit4.listeners;

import java.io.File;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.junit.runner.Description;
import org.simpleframework.xml.core.Persister;

import com.carrotsearch.ant.tasks.junit4.JUnit4;
import com.carrotsearch.ant.tasks.junit4.events.aggregated.AggregatedSuiteResultEvent;
import com.carrotsearch.ant.tasks.junit4.events.aggregated.AggregatedTestResultEvent;
import com.carrotsearch.ant.tasks.junit4.events.aggregated.TestStatus;
import com.carrotsearch.ant.tasks.junit4.events.mirrors.FailureMirror;
import com.carrotsearch.ant.tasks.junit4.listeners.antxml.FailureModel;
import com.carrotsearch.ant.tasks.junit4.listeners.antxml.PropertyModel;
import com.carrotsearch.ant.tasks.junit4.listeners.antxml.TestCaseModel;
import com.carrotsearch.ant.tasks.junit4.listeners.antxml.TestSuiteModel;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.eventbus.Subscribe;

/**
 * A report listener that produces XML files compatible with those produced by
 * ANT's default <code>junit</code> task. These files do not include full
 * information but many tools can parse them.
 */
public class AntXmlReport implements AggregatedEventListener {
  private JUnit4 junit4;
  private File dir;

  /**
   * Output directory to write reports to.
   */
  public void setDir(File dir) {
    this.dir = dir;
  }
  
  /*
   * 
   */
  @Override
  public void setOuter(JUnit4 junit4) {
    this.junit4 = junit4;
    
    if (this.dir == null) {
      throw new BuildException("'dir' attribute is required (target folder for reports).");
    }
  }

  /**
   * Emit information about all of suite's tests. 
   */
  @Subscribe
  public void onSuiteResult(AggregatedSuiteResultEvent e) {
    Description suiteDescription = e.getDescription();
    String displayName = suiteDescription.getDisplayName();
    if (displayName.trim().isEmpty()) {
      junit4.log("Could not emit XML report for suite (null description).", 
          Project.MSG_WARN);
      return;
    }
    
    try {
      File reportFile = new File(dir, "TEST-" + displayName + ".xml");
      Persister persister = new Persister();
      persister.write(buildModel(e), reportFile);
    } catch (Exception x) {
      junit4.log("Could not serialize report for suite "
          + displayName + ": " + x.toString(), Project.MSG_WARN);
    }
  }

  /**
   * Build data model for serialization.
   */
  private TestSuiteModel buildModel(AggregatedSuiteResultEvent e) {
    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    TestSuiteModel suite = new TestSuiteModel();

    suite.hostname = "nohost.nodomain";
    suite.name = e.getDescription().getDisplayName();
    suite.properties = buildModel(e.getSlave().getSystemProperties());
    suite.time = e.getExecutionTime() / 1000.0;
    suite.timestamp = df.format(new Date(e.getStartTimestamp()));

    suite.testcases = buildModel(e.getTests());
    suite.tests = suite.testcases.size();

    // Suite-level failures and errors are simulated as test cases.
    for (FailureMirror m : e.getFailures()) {
      TestCaseModel model = new TestCaseModel();
      model.classname = "junit.framework.TestSuite"; // empirical ANT output.
      model.name = m.getDescription().getClassName();
      model.time = 0;
      if (m.isAssertionViolation()) {
        model.failures.add(buildModel(m));
      } else {
        model.errors.add(buildModel(m));
      }
      suite.testcases.add(model);
    }

    // Calculate test numbers that match limited view (no ignored tests, 
    // faked suite-level errors).
    for (TestCaseModel tc : suite.testcases) {
      suite.errors += tc.errors.size();
      suite.failures += tc.failures.size();
    }

    StringWriter sysout = new StringWriter();
    StringWriter syserr = new StringWriter();
    e.getSlave().decodeStreams(e.getEventStream(), sysout, syserr);
    suite.sysout = sysout.toString();
    suite.syserr = syserr.toString();

    return suite;
  }

  /* */
  private List<TestCaseModel> buildModel(List<AggregatedTestResultEvent> testEvents) {
    List<TestCaseModel> tests = Lists.newArrayList();
    for (AggregatedTestResultEvent e : testEvents) {
      if (e.getStatus() == TestStatus.IGNORED ||
          e.getStatus() == TestStatus.IGNORED_ASSUMPTION) {
        // No way to report these in ANT XML.
        continue;
      }

      TestCaseModel model = new TestCaseModel();
      model.name = e.getDescription().getMethodName();
      model.classname = e.getDescription().getClassName();
      model.time = e.getExecutionTime() / 1000.0;

      for (FailureMirror m : e.getFailures()) {
        if (m.isAssertionViolation()) {
          model.failures.add(buildModel(m));
        } else {
          model.errors.add(buildModel(m));
        }
      }

      tests.add(model);
    }
    return tests;
  }

  /* */
  private FailureModel buildModel(FailureMirror f) {
    FailureModel model = new FailureModel();
    model.message = Strings.nullToEmpty(f.getMessage());
    model.text = f.getTrace();
    model.type = f.getThrowableClass();
    return model;
  }

  /* */
  private List<PropertyModel> buildModel(Map<String,String> properties) {
    List<PropertyModel> props = Lists.newArrayList();
    for (Map.Entry<String,String> e : properties.entrySet()) {
      props.add(new PropertyModel(e.getKey(), e.getValue()));
    }
    return props;
  }
}
