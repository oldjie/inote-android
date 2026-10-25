package com.personal.inote;

import android.app.Instrumentation;
import android.os.Bundle;
import android.test.AndroidTestCase;
import junit.framework.*;
import java.io.PrintWriter;
import java.io.StringWriter;

/** Minimal JUnit 3 runner for devices without android.test.runner.jar. */
public final class PersonalTestRunner extends Instrumentation {
    public static Instrumentation instrumentation;
    private Bundle arguments;
    @Override public void onCreate(Bundle arguments){this.arguments=arguments==null?new Bundle():arguments;instrumentation=this;start();}
    @Override public void onStart(){
        Bundle output=new Bundle();StringBuilder log=new StringBuilder();
        try{
            TestSuite suite=new TestSuite("Personal iNote");
            Class<?>[] available={NoteStoreTest.class,MainActivitySmokeTest.class,NoteExporterTest.class,OriginalInoteImporterTest.class,RichTextSecurityTest.class};
            String selected=arguments.getString("class","");
            for(Class<?> type:available){
                if(selected.isEmpty()){suite.addTestSuite(type.asSubclass(TestCase.class));continue;}
                for(String spec:selected.split(",")){String[] parts=spec.split("#",2);if(parts[0].equals(type.getName())||parts[0].equals(type.getSimpleName())){
                    if(parts.length==1)suite.addTestSuite(type.asSubclass(TestCase.class));else {TestCase test=(TestCase)type.getDeclaredConstructor().newInstance();test.setName(parts[1]);suite.addTest(test);}
                }}
            }
            if(suite.countTestCases()==0)throw new IllegalArgumentException("No matching test classes: "+selected);
            TestResult result=new TestResult();result.addListener(new TestListener(){
                public void startTest(Test test){if(test instanceof AndroidTestCase)((AndroidTestCase)test).setContext(getTargetContext());Bundle status=new Bundle();status.putString("stream","\n"+test+" ... ");sendStatus(1,status);}
                public void endTest(Test test){}
                public void addError(Test test,Throwable error){failure(test,error);}
                public void addFailure(Test test,AssertionFailedError error){failure(test,error);}
                private void failure(Test test,Throwable error){StringWriter trace=new StringWriter();error.printStackTrace(new PrintWriter(trace));log.append("\n").append(test).append("\n").append(trace);}
            });
            suite.run(result);
            log.append(result.wasSuccessful()?"\nOK ("+result.runCount()+" tests)\n":"\nFAILURES!!! Tests run: "+result.runCount()+", Failures: "+result.failureCount()+", Errors: "+result.errorCount()+"\n");
            output.putString("stream",log.toString());output.putInt("tests",result.runCount());output.putInt("failures",result.failureCount());output.putInt("errors",result.errorCount());finish(result.wasSuccessful()?-1:0,output);
        }catch(Throwable error){StringWriter trace=new StringWriter();error.printStackTrace(new PrintWriter(trace));output.putString("stream",log+"\nRUNNER ERROR\n"+trace);finish(0,output);}
    }
}
