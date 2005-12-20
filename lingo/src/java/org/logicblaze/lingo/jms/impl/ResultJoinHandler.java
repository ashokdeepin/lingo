/** 
 * 
 * Copyright 2005 LogicBlaze, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License. 
 * 
 **/
package org.logicblaze.lingo.jms.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.logicblaze.lingo.jms.ReplyHandler;
import org.logicblaze.lingo.jms.marshall.Marshaller;
import org.springframework.remoting.support.RemoteInvocationResult;

import javax.jms.JMSException;
import javax.jms.Message;

/**
 * A {@link org.logicblaze.lingo.jms.ReplyHandler} which can handle join
 * multiple results to the same request which are then aggregated together into
 * a single value.
 * 
 * @version $Revision$
 */
public class ResultJoinHandler implements ReplyHandler {

    private static final Log log = LogFactory.getLog(ResultJoinHandler.class);

    private Marshaller marshaller;
    private ResultJoinStrategy joinStrategy;
    private Object lock = new Object();
    private int responseCount;
    private RemoteInvocationResult result;
    private long timeout = 2000;

    public ResultJoinHandler(Marshaller marshaller, ResultJoinStrategy joinStrategy) {
        this.marshaller = marshaller;
        this.joinStrategy = joinStrategy;
    }

    public boolean handle(Message message) throws JMSException {
        RemoteInvocationResult newResult = marshaller.extractInvocationResult(message);
        synchronized (lock) {
            ++responseCount;
            if (result == null) {
                result = newResult;
            }
            else {
                result = joinStrategy.mergeResponses(result, newResult, responseCount);
            }
            if (joinStrategy.unblockCallerThread(result, responseCount)) {
                lock.notifyAll();
            }
        }
        return joinStrategy.removeHandler(result, responseCount);
    }

    /**
     * This method will block the calling thread until the result is available.
     */
    public RemoteInvocationResult waitForResult() {
        while (true) {
            synchronized (lock) {
                if (result != null) {
                    return result;
                }
                try {
                    lock.wait(timeout);
                }
                catch (InterruptedException e) {
                    log.debug("Ignored interrupt exception: " + e, e);
                }
            }
        }
    }

    public RemoteInvocationResult pollResult() {
        synchronized (lock) {
            return result;
        }
    }

    public int getResponseCount() {
        synchronized (lock) {
            return responseCount;
        }
    }

    public long getTimeout() {
        return timeout;
    }

    /**
     * Sets the {@link #wait(long)} method timeout period before resuming the
     * wait.
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

}
