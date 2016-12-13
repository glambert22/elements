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
package net.e6tech.elements.common.interceptor;

import java.lang.reflect.Method;

/**
 * Created by futeh on 1/20/16.
 */
public interface InterceptorHandler {
    /**
     *
     * @param interceptorInstance the intercepting instance that wraps a target.
     * @param thisMethod the intercepting method
     * @param target can be null if the interceptor is created using newInstance.
     * @param proceed the intercepted method
     * @param args arguments
     * @return return value of the call.
     * @throws Throwable general exception
     */
    Object invoke(Object interceptorInstance, Method thisMethod, Object target, Method proceed, Object[] args) throws Throwable;
}
