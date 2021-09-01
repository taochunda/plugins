/**
 * Copyright (c) 2020 QingLang, Inc. <baisui@qlangtech.com>
 * <p>
 * This program is free software: you can use, redistribute, and/or modify
 * it under the terms of the GNU Affero General Public License, version 3
 * or later ("AGPL"), as published by the Free Software Foundation.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.
 * <p>
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.qlangtech.tis.plugin.datax;

import com.qlangtech.tis.datax.DataXJobSingleProcessorExecutor;

/**
 * @author: 百岁（baisui@qlangtech.com）
 * @create: 2021-08-29 13:49
 **/
public class LocalDataXJobMainEntrypoint {
    static int executeCount = 0;

    public static void main(String[] args) {
        executeCount++;
        //System.out.println("===============hello" + args[0] + "\n" + args[1] + "\n" + args[2] + "\n" + args[3]);
        if (5 != args.length) {
            throw new AssertionError("5 != args.length");
        }

        if (Boolean.parseBoolean(System.getProperty("env_props"))) {
            throw new IllegalStateException("env_props must be false");
        }

        //  CenterResource.notFetchFromCenterRepository()
        if (!Boolean.parseBoolean(System.getProperty("notFetchFromCenterRepository"))) {
            throw new IllegalStateException("must be notFetchFromCenterRepository");
        }

        assertEquals(String.valueOf(TestLocalDataXJobSubmit.TaskId), args[0]);
        assertEquals(TestLocalDataXJobSubmit.dataXfileName, args[1]);// = "customer_order_relation_0.json";
        assertEquals(TestLocalDataXJobSubmit.dataXName, args[2]);//= "baisuitestTestcase";
        assertEquals(TestLocalDataXJobSubmit.statusCollectorHost, args[3]);// = "127.0.0.1:3489";
        assertEquals("local", args[4]);

        assertEquals(DataXJobSingleProcessorExecutor.SYSTEM_KEY_LOGBACK_PATH_VALUE, System.getProperty(DataXJobSingleProcessorExecutor.SYSTEM_KEY_LOGBACK_PATH_KEY));
    }

    private static void assertEquals(String expect, String actual) {
        if (!expect.equals(actual)) {
            throw new AssertionError("is not equal,expect:" + expect + ",actual:" + actual);
        }
    }

}
