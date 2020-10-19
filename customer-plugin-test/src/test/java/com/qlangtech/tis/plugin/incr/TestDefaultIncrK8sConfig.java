package com.qlangtech.tis.plugin.incr;

import com.qlangtech.tis.TIS;
import com.qlangtech.tis.coredefine.module.action.IIncrSync;
import com.qlangtech.tis.coredefine.module.action.IncrSpec;
import com.qlangtech.tis.coredefine.module.action.Specification;
import com.qlangtech.tis.plugin.BaiscPluginTest;
import com.qlangtech.tis.plugin.PluginStore;

/**
 * 跑这个单元测试需要事先部署k8s集群
 *
 * @author: baisui 百岁
 * @create: 2020-08-11 11:05
 **/
public class TestDefaultIncrK8sConfig extends BaiscPluginTest {

    private static final String s4totalpay = "search4totalpay";
    private IncrStreamFactory incrFactory;

    @Override
    public void setUp() throws Exception {
        //super.setUp();
        PluginStore<IncrStreamFactory> s4totalpayIncr = TIS.getPluginStore(s4totalpay, IncrStreamFactory.class);
        incrFactory = s4totalpayIncr.getPlugin();
        assertNotNull(incrFactory);
    }

    public void testCreateIncrDeployment() throws Exception {

        IIncrSync incr = incrFactory.getIncrSync();
        assertNotNull(incr);
        assertFalse(s4totalpay + " shall have not deploy incr instance in k8s", incr.getRCDeployment(s4totalpay) != null);

        IncrSpec incrSpec = new IncrSpec();
        incrSpec.setCpuLimit(Specification.parse("1"));
        incrSpec.setCpuRequest(Specification.parse("500m"));
        incrSpec.setMemoryLimit(Specification.parse("1G"));
        incrSpec.setMemoryRequest(Specification.parse("500M"));
        incrSpec.setReplicaCount(1);

        long timestamp = 20190820171040l;

        try {
            incr.deploy(s4totalpay, incrSpec, timestamp);
        } catch (Exception e) {
            throw e;
        }

    }


    public void testDeleteIncrDeployment() throws Exception {
        try {
            incrFactory.getIncrSync().removeInstance(s4totalpay);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }
}