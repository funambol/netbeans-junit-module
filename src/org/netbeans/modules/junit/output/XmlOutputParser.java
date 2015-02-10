/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Contributor(s):
 *
 * The Original Software is NetBeans. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2008 Sun
 * Microsystems, Inc. All Rights Reserved.
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 */

package org.netbeans.modules.junit.output;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.netbeans.modules.gsf.testrunner.api.Status;
import org.netbeans.modules.gsf.testrunner.api.TestSession;
import org.netbeans.modules.gsf.testrunner.api.Testcase;
import org.netbeans.modules.gsf.testrunner.api.Trouble;
import org.openide.ErrorManager;
import org.openide.util.NbBundle;
import org.openide.xml.XMLUtil;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Parser of XML-output generated by the JUnit XML formatter.
 *
 * @author Marian Petras
 */
final class XmlOutputParser extends DefaultHandler {

    /** */
    private static final int STATE_OUT_OF_SCOPE = 1;
    /** */
    private static final int STATE_TESTSUITE = 2;
    /** */
    private static final int STATE_PROPERTIES = 3;
    /** */
    private static final int STATE_PROPERTY = 4;
    /** */
    private static final int STATE_TESTCASE = 8;
    /** */
    private static final int STATE_FAILURE = 12;
    /** */
    private static final int STATE_ERROR = 13;
    /** */
    private static final int STATE_SKIPPED = 14;
    /** */
    private static final int STATE_OUTPUT_STD = 16;
    /** */
    private static final int STATE_OUTPUT_ERR = 17;
    
    /** */
    private int state = STATE_OUT_OF_SCOPE;
    /** */
    int unknownElemNestLevel = 0;
    
    /** */
    private final XMLReader xmlReader;
    /** */
    private JUnitTestSuite suite;
    /** */
    private Testcase testcase;
    /** */
    private Trouble trouble;
    /** */
    private StringBuffer charactersBuf;
    
    /** */
    private final RegexpUtils regexp;

    private TestSession testSession;
    /**
     *
     * @exception  org.xml.sax.SAXException
     *             if initialization of the parser failed
     */
    static JUnitTestSuite parseXmlOutput(Reader reader, TestSession session) throws SAXException,
                                                       IOException {
        XmlOutputParser parser = new XmlOutputParser(session);
        try {
           parser.xmlReader.parse(new InputSource(reader));
        } catch (SAXException ex) {
            String message = ex.getMessage();
            int severity = ErrorManager.INFORMATIONAL;
            if ((message != null)
                    && ErrorManager.getDefault().isLoggable(severity)) {
                ErrorManager.getDefault().log(
                       severity,
                       "Exception while parsing XML output from JUnit: "//NOI18N
                           + message);
            }
            throw ex;
        } catch (IOException ex) {
            assert false;            /* should never happen */
        } finally {
            reader.close();          //throws IOException
        }
        return parser.suite;
    }
    
    /** Creates a new instance of XMLOutputParser */
    private XmlOutputParser(TestSession session) throws SAXException {
        testSession = session;
        xmlReader = XMLUtil.createXMLReader();
        xmlReader.setContentHandler(this);
        
        regexp = RegexpUtils.getInstance();
    }
    
    /**
     */
    @Override
    public void startElement(String uri,
                             String localName,
                             String qName,
                             Attributes attrs) throws SAXException {
        switch (state) {
            //<editor-fold defaultstate="collapsed" desc="STATE_PROPERTIES">
            case STATE_PROPERTIES:
                if (qName.equals("property")) {
                    state = STATE_PROPERTY;
                } else {
                    startUnknownElem();
                }
                break;  //</editor-fold>
            //<editor-fold defaultstate="collapsed" desc="STATE_TESTSUITE">
            case STATE_TESTSUITE:
                if (qName.equals("testcase")) {
                    testcase = createTestcaseReport(
                            attrs.getValue("classname"),
                            attrs.getValue("name"),
                            attrs.getValue("time"));
                    state = STATE_TESTCASE;
                } else if (qName.equals("system-out")) {
                    state = STATE_OUTPUT_STD;
                } else if (qName.equals("system-err")) {
                    state = STATE_OUTPUT_ERR;
                } else if (qName.equals("properties")) {
                    state = STATE_PROPERTIES;
                } else {
                    startUnknownElem();
                }
                break;  //</editor-fold>
            //<editor-fold defaultstate="collapsed" desc="STATE_TESTCASE">
            case STATE_TESTCASE:
                if (qName.equals("failure")) {
                    state = STATE_FAILURE;
                } else if (qName.equals("error")) {
                    state = STATE_ERROR;
                }  else if (qName.equals("skipped")) {
                    state = STATE_SKIPPED;
                } else {
                    startUnknownElem();
                }
                if (state >= 0 && state != 14) {     //i.e. the element is "failure" or "error"
                    assert testcase != null;
                    trouble = new Trouble(state == STATE_ERROR);

                    String attrValue;
                    attrValue = attrs.getValue("message");              //NOI18N
                    if (attrValue != null) {
                        addStackTraceLine(trouble, attrValue, false);
                    }

                    attrValue = attrs.getValue("type");                 //NOI18N
                    if (attrValue != null) {
                        addStackTraceLine(trouble, attrValue, false);
                    }

                    /*
                     * TODO!!!!!!!
                     * Comparison failures are incorrectly reported as errors
                     * (Ant 1.7 + JUnit 4.1) so there is a workaround here:
                     * If the failure/error's is of type ComparisonFailure,
                     * then set the status to "FAILURE", even though it is
                     * reported to be "ERROR":
                     */

                    /*
                     * TODO!!!!
                     * Another hack-workaround:
                     * When run with Ant 1.7 + JUnit 4.1, comparison failures
                     * with no given failure message are reported as if they
                     * had failure message "null". The following workaround
                     * removes this fake message:
                     */
                }
                break;  //</editor-fold>
            //<editor-fold defaultstate="collapsed" desc="STATE_OUT_OF_SCOPE">
            case STATE_OUT_OF_SCOPE:
                if (qName.equals("testsuite")) {
                    suite = createSuite(attrs.getValue("name"),
                                          attrs.getValue("tests"),
                                          attrs.getValue("failures"),
                                          attrs.getValue("errors"),
                                          attrs.getValue("time"));
                    state = STATE_TESTSUITE;
                } else {
                    startUnknownElem();
                }
                break;  //</editor-fold>
            //<editor-fold defaultstate="collapsed" desc="STATE_xxx (other)">
            case STATE_PROPERTY:
            case STATE_FAILURE:
            case STATE_ERROR:
            case STATE_OUTPUT_STD:
            case STATE_OUTPUT_ERR:
                startUnknownElem();
                break;  //</editor-fold>
            //<editor-fold defaultstate="collapsed" desc="default">
            default:
                assert state < 0;
                unknownElemNestLevel++;
                break;  //</editor-fold>
        }
    }
    
    /**
     */
    @Override
    public void endElement(String uri,
                           String localName,
                           String qName) throws SAXException {
        switch (state) {
            //<editor-fold defaultstate="collapsed" desc="STATE_PROPERTIES">
            case STATE_PROPERTIES:
                assert qName.equals("properties");
                state = STATE_TESTSUITE;
                break;                                          //</editor-fold>
            //<editor-fold defaultstate="collapsed" desc="STATE_TESTSUITE">
            case STATE_TESTSUITE:
                assert qName.equals("testsuite");
                state = STATE_OUT_OF_SCOPE;
                break;                                          //</editor-fold>
            //<editor-fold defaultstate="collapsed" desc="STATE_TESTCASE">
            case STATE_TESTCASE:
                assert qName.equals("testcase");
                
                assert testcase != null;
                suite.getTestcases().add(testcase);
                testcase = null;
                state = STATE_TESTSUITE;
                break;                                          //</editor-fold>
            //<editor-fold defaultstate="collapsed" desc="STATE_OUT_OF_SCOPE">
            case STATE_OUT_OF_SCOPE:
                assert false;
                break;                                          //</editor-fold>
            //<editor-fold defaultstate="collapsed" desc="STATE_PROPERTY">
            case STATE_PROPERTY:
                assert qName.equals("property");
                state = STATE_PROPERTIES;
                break;                                          //</editor-fold>
            //<editor-fold defaultstate="collapsed" desc="STATE_FAILURE or STATE_ERROR or STATE_SKIPPED">
            case STATE_FAILURE:
		assert state == STATE_FAILURE && qName.equals("failure");
                handleFailureError();
                break;
            case STATE_ERROR:
                assert state == STATE_ERROR && qName.equals("error");                
                handleFailureError();
                break;
            case STATE_SKIPPED:
		assert testcase != null;
		testcase.setStatus(Status.SKIPPED);
                state = STATE_TESTCASE;
                break;                                          //</editor-fold>
            //<editor-fold defaultstate="collapsed" desc="STATE_OUTPUT_STD or STATE_OUTPUT_ERR">
            case STATE_OUTPUT_STD:
            case STATE_OUTPUT_ERR:
                assert (state == STATE_OUTPUT_STD && qName.equals("system-out"))
                   || (state == STATE_OUTPUT_ERR && qName.equals("system-err"));
                if (charactersBuf != null) {
                    String[] output = getOutput(charactersBuf.toString());
                    if (state == STATE_OUTPUT_STD) {
//kaktus                        report.outputStd = output;
                    } else {
//kaktus                        report.outputErr = output;
                    }
                    charactersBuf = null;
                }
                state = STATE_TESTSUITE;
                break;                                          //</editor-fold>
            //<editor-fold defaultstate="collapsed" desc="default">
            default:
                assert state < 0;
                if (--unknownElemNestLevel == 0) {
                    state = -state;
                }
                break;                                          //</editor-fold>
        }
    }

    private void handleFailureError() {
	assert testcase != null;
	assert trouble != null;
	if (charactersBuf != null) {
	    LineNumberReader lnr = new LineNumberReader(new StringReader(charactersBuf.toString()));
	    try {
		String line = lnr.readLine();
		while (line != null) {
		    addStackTraceLine(trouble, line, true);
		    line = lnr.readLine();
		}
	    } catch (IOException e) {
	    }
	    charactersBuf = null;
	}
	testcase.setTrouble(trouble);
	trouble = null;
	state = STATE_TESTCASE;
    }
    
    /**
     */
    private void startUnknownElem() {
        state = -state;
        unknownElemNestLevel++;
    }
    
    /**
     */
    private JUnitTestSuite createSuite(String suiteName,
                                String testsCountStr,
                                String failuresStr,
                                String errorsStr,
                                String timeStr) {
        /* Parse the testsuite name: */
        if (suiteName == null) {
            suiteName = NbBundle.getMessage(XmlOutputParser.class,
                                            "UNNKOWN_NAME");            //NOI18N
        }
        
        /* Parse the test counts: */
/*
        final String[] numberStrings = new String[] { testsCountStr,
                                                      failuresStr,
                                                      errorsStr };
        final int[] numbers = new int[numberStrings.length];
        for (int i = 0; i < numberStrings.length; i++) {
            boolean ok;
            String numberStr = numberStrings[i];
            if (numberStr == null) {
                ok = false;
            } else {
                try {
                    numbers[i] = Integer.parseInt(numberStrings[i]);
                    ok = (numbers[i] >= 0);
                } catch (NumberFormatException ex) {
                    ok = false;
                }
            }
            if (!ok) {
                numbers[i] = -1;
            }
        }
*/
        /* Parse the elapsed time: */
        int timeMillis = regexp.parseTimeMillisNoNFE(timeStr);
        
        JUnitTestSuite testSuite = new JUnitTestSuite(suiteName, testSession);
        testSuite.setElapsedTime(timeMillis);

        return testSuite;
    }
    
    /**
     */
    private Testcase createTestcaseReport(String className,
                                                 String name,
                                                 String timeStr) {
        JUnitTestcase tc = new JUnitTestcase(name, "JUnit test", testSession);
        tc.setClassName(className);
        tc.setTimeMillis(regexp.parseTimeMillisNoNFE(timeStr));
        
        return tc;
    }
    
    /**
     */
    @Override
    public void characters(char[] ch,
                           int start,
                           int length) throws SAXException {
        switch (state) {
            case STATE_FAILURE:
            case STATE_ERROR:
            case STATE_OUTPUT_STD:
            case STATE_OUTPUT_ERR:
                if (charactersBuf == null) {
                    charactersBuf = new StringBuffer(512);
                }
                charactersBuf.append(ch, start, length);
                break;
        }
    }
    
    /**
     */
    private String[] getOutput(String string) {
        String[] lines = string.split("(?:\\r|\\r\\n|\\n)");            //NOI18N
        
        /*
         * The XML output produces an extra empty line at the end of the output:
         */
        if ((lines.length >= 1) && (lines[lines.length - 1].length() == 0)) {
            String[] temp = lines;
            lines = new String[lines.length - 1];
            if (lines.length > 0) {
                System.arraycopy(temp, 0, lines, 0, lines.length);
            }
        }
        return lines;
    }

    private void addStackTraceLine(Trouble tr, String line, boolean validateST){
        if ((tr == null) || (line == null) || (line.length() == 0) || (line.equals("null"))){ //NOI18N
            return;
        }

        if (validateST){
            boolean valid = false;
            Pattern[] patterns = new Pattern[]{regexp.getCallstackLinePattern(),
                                               regexp.getComparisonHiddenPattern(),
                                               regexp.getFullJavaIdPattern()};
            for(Pattern pattern: patterns){
                Matcher matcher = pattern.matcher(line);
                if (matcher.matches()){
                    valid = true;
                    break;
                }
            }
            if (!valid){
                return;
            }
        }
 
        String[] stArray = tr.getStackTrace();
        if (stArray == null){
            tr.setStackTrace(new String[]{line});
            setComparisonFailure(tr, line);
        } else {
            List<String> stList = new ArrayList(Arrays.asList(trouble.getStackTrace()));
            if (!line.startsWith(stList.get(stList.size()-1))){
                stList.add(line);
                tr.setStackTrace(stList.toArray(new String[stList.size()]));
            }
        }
    }

    private void setComparisonFailure(Trouble tr, String line) {
        // #190267: exclude "big" log (if any) from the matching
        int logPos = line.indexOf("Log:"); // NOI18N
        if(logPos > 0) {
            line = line.substring(0, logPos);
        }

        Matcher matcher = regexp.getComparisonPattern().matcher(line.replaceAll("\n", "")); // NOI18N
        if (matcher.matches()){
            String startExpected = "expected:<"; // NOI18N
            String startActual = "> but was:<"; // NOI18N
            tr.setComparisonFailure(
                    new Trouble.ComparisonFailure(
                        line.substring(line.indexOf(startExpected) + startExpected.length(), line.indexOf(startActual)),
                        line.substring(line.indexOf(startActual) + startActual.length(), line.length() - 1))
            );
            return;
        }
        matcher = regexp.getComparisonHiddenPattern().matcher(line);
        if (matcher.matches()){
            tr.setComparisonFailure(
                    new Trouble.ComparisonFailure(
                        matcher.group(1),
                        matcher.group(2))
            );
            return;
        }
    }

}
