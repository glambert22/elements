/*
Copyright 2015 Futeh Kao

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package net.e6tech.elements.common.logging;

import net.e6tech.elements.common.util.Rethrowable;
import net.e6tech.elements.common.util.SystemException;
import org.apache.logging.log4j.ThreadContext;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Created by futeh.
 */
@SuppressWarnings({"squid:S106", "squid:S1604", "squid:S1188", "squid:S2696", "squid:MethodCyclomaticComplexity"}) // we need to use System.out
public class LogHandler implements InvocationHandler {

    private static final String LOG_DIR = "logDir";
    private static String logDir;

    static {
       System.setProperty("java.util.logging.manager", "net.e6tech.elements.common.logging.jul.LogManager");
    }

    private static ConsoleLogger consoleLogger = new ConsoleLogger();

    private org.slf4j.Logger slf4jLogger;
    private Class loggingClass;
    private String loggingName;
    private LogLevel level = LogLevel.ERROR;

    private ExceptionLogger exceptionLogger = new ExceptionLogger() {
        @Override
        public Logger exceptionLogger(LogLevel level) {
            LogHandler handler = new LogHandler(slf4jLogger);
            handler.level = level;
            return (Logger) Proxy.newProxyInstance(Logger.class.getClassLoader(), new Class[]{Logger.class},
                    handler);
        }
    };

    private Rethrowable rethrowable = new Rethrowable() {
        @Override
        public SystemException systemException(String msg, Throwable th) {
            SystemException t = Rethrowable.super.systemException(msg, th);
            log(msg, th);
            return t;
        }

        @Override
        public <T extends Throwable> T exception(Class<T> exceptionClass, String msg, Throwable e) {
            T t = Rethrowable.super.exception(exceptionClass, msg, e);
            log(msg, e);
            return t;
        }

        protected void log(String msg, Throwable e) {
            if (getLogger() != null) {
                switch (level) {
                    case FATAL:
                    case ERROR:
                        getLogger().error(msg, e);
                        break;
                    case WARN:
                        getLogger().warn(msg, e);
                        break;
                    case INFO:
                        getLogger().info(msg, e);
                        break;
                    case DEBUG:
                        getLogger().debug(msg, e);
                        break;
                    case TRACE:
                        getLogger().trace(msg, e);
                        break;
                    default:
                        getLogger().warn(msg, e);
                        break;
                }
            } else {
                System.out.println(msg);
                e.printStackTrace(System.out);
            }
        }
    };

    public LogHandler(org.slf4j.Logger slf4jLogger) {
        this.slf4jLogger = slf4jLogger;
    }

    public LogHandler(Class cls) {
        loggingClass = cls;
    }

    public LogHandler(String name) {
        loggingName = name;
    }

    // This level of indirection is needed, otherwise, Log4j will look at the system property
    // prematurely.
    // this method should only be called when the caller starts to log output.
    protected org.slf4j.Logger getLogger() {

        if (slf4jLogger != null)
            return slf4jLogger;

        if (loggingClass == null && slf4jLogger == null && loggingName == null)
            return null;

        if (System.getProperty("log4j.configurationFile") == null) {
            return consoleLogger;
        }

        // if logDir is not configured we should just use consoleLogger.
        if (ThreadContext.get(LOG_DIR) == null) {
            if (logDir == null) {
                if (System.getProperty(LOG_DIR) != null)
                    logDir = System.getProperty(LOG_DIR);
                else if (System.getProperty(Logger.logDir) != null)
                    logDir = System.getProperty(Logger.logDir);
            }
            if (logDir == null)
                return consoleLogger;
            else ThreadContext.put(LOG_DIR, logDir);
        }

        // calling LoggerFactory.getLogger will trigger log4j being initialized.
        if (loggingClass != null)
            slf4jLogger = LoggerFactory.getLogger(loggingClass);
        else if (loggingName != null)
            slf4jLogger = LoggerFactory.getLogger(loggingName);
        loggingClass = null;
        loggingName = null;
        return slf4jLogger;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass().equals(Rethrowable.class)) {
            return method.invoke(rethrowable, args);
        } else if (method.getDeclaringClass().equals(ExceptionLogger.class)) {
            return method.invoke(exceptionLogger, args);
        } else {
            org.slf4j.Logger logger = getLogger();
            if (logger == null) {
                return null;
            } else {
                return method.invoke(logger, args);
            }
        }
    }

    private static class ConsoleLogger extends NullLogger {

        @Override
        public String getName() {
            return "ConsoleLogger";
        }

        @Override
        public void info(String format, Object... arguments) {
            if (arguments != null && arguments.length > 0) {
                System.out.println(MessageFormatter.arrayFormat(format, arguments).getMessage());
            } else {
                System.out.println(format);
            }
        }

        @Override
        public void info(String msg, Throwable t) {
            System.out.println(msg);
            if (t != null) {
                t.printStackTrace(System.out);
                System.out.println();
            }
        }
    }
}
