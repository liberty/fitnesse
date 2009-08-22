// Copyright (C) 2003-2009 by Object Mentor, Inc. All rights reserved.
// Released under the terms of the CPL Common Public License version 1.0.
package fitnesse.components;

import java.lang.reflect.Method;

public class ThreadCommandRunner extends CommandRunner {
   private Class serverClass;
	private String args;
	private Thread runnerThread;
	public Method mainMethod;

	public ThreadCommandRunner(String command, String args) {
		this.args = args + " -x"; // -x = don't call System.exit()
		initServerMainMethod(command);
   }

	private void initServerMainMethod(String command) {
		try {
			this.serverClass = Class.forName(getServerClassName(command));
			this.mainMethod = serverClass.getMethod("main", new Class[]{String[].class});
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private String getServerClassName(String command) {
		int classNameStart = command.lastIndexOf(" ");
		return classNameStart==-1?command:command.substring(classNameStart +1);
	}

	public void asynchronousStart() throws Exception {
		runnerThread = createRunnerThread();
		runnerThread.start();
  }

  public void join() throws Exception {
	  runnerThread.join();
  }

  public void kill() throws Exception {
	  runnerThread.stop();
  }

	public void exceptionOccurred(Exception e) {
		e.printStackTrace();
	}

	Thread createRunnerThread() throws Exception {
	  Runnable runnable = new Runnable() {
	    public void run() {
	      try {
	        while (!tryCreateFitServer(args))
	          Thread.sleep(10);
	      } catch (Exception e) {

	      }
	    }
	  };
	  return new Thread(runnable, "ThreadCommandRunner");
	}

	private boolean tryCreateFitServer(String args) throws Exception {
	  try {
		  mainMethod.invoke(null, new Object[]{args.trim().split(" ")});
	    return true;
	  } catch (Exception e) {
	    return false;
	  }
	}


}