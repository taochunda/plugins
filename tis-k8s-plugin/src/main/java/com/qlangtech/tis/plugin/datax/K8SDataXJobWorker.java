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

import com.alibaba.citrus.turbine.Context;
import com.qlangtech.tis.TIS;
import com.qlangtech.tis.config.k8s.HorizontalpodAutoscaler;
import com.qlangtech.tis.config.k8s.ReplicasSpec;
import com.qlangtech.tis.coredefine.module.action.RcDeployment;
import com.qlangtech.tis.datax.job.DataXJobWorker;
import com.qlangtech.tis.extension.Descriptor;
import com.qlangtech.tis.extension.TISExtension;
import com.qlangtech.tis.manage.common.TisUTF8;
import com.qlangtech.tis.plugin.PluginStore;
import com.qlangtech.tis.plugin.annotation.FormField;
import com.qlangtech.tis.plugin.annotation.FormFieldType;
import com.qlangtech.tis.plugin.annotation.Validator;
import com.qlangtech.tis.plugin.incr.WatchPodLog;
import com.qlangtech.tis.plugin.k8s.EnvVarsBuilder;
import com.qlangtech.tis.plugin.k8s.K8SController;
import com.qlangtech.tis.plugin.k8s.K8sImage;
import com.qlangtech.tis.runtime.module.misc.IFieldErrorHandler;
import com.qlangtech.tis.trigger.jst.ILogListener;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AutoscalingV2beta1Api;
import io.kubernetes.client.openapi.models.*;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author: 百岁（baisui@qlangtech.com）
 * @create: 2021-04-23 18:16
 **/
public class K8SDataXJobWorker extends DataXJobWorker {

    private static final Logger logger = LoggerFactory.getLogger(K8SDataXJobWorker.class);

    public static final String KEY_FIELD_NAME = "k8sImage";

    private static final String K8S_INSTANCE_NAME = "datax-worker";

    @FormField(ordinal = 0, type = FormFieldType.SELECTABLE, validate = {Validator.require})
    public String k8sImage;

    @FormField(ordinal = 1, type = FormFieldType.INPUTTEXT, validate = {Validator.require})
    public String zkAddress;

    @FormField(ordinal = 2, type = FormFieldType.INPUTTEXT, validate = {Validator.require})
    public String zkQueuePath;

    @Override
    public String getZkQueuePath() {
        return this.zkQueuePath;
    }

    // private transient CuratorFramework client;
    // private transient CoreV1Api k8sV1Api;
    private transient ApiClient apiClient;
    private transient K8SController k8SController;

    @Override
    public void remove() {
        K8SController k8SController = getK8SController();
        //  ApiClient k8SApi = getK8SApi();
        k8SController.removeInstance(K8S_INSTANCE_NAME);
        try {
            if (supportHPA()) {
                K8sImage k8SImage = this.getK8SImage();
                AutoscalingV2beta1Api hpaApi = new AutoscalingV2beta1Api(this.getK8SApi());
                //            String name,
                //            String namespace,
                //            String pretty,
                //            String dryRun,
                //            Integer gracePeriodSeconds,
                //            Boolean orphanDependents,
                //            String propagationPolicy,
                //            V1DeleteOptions body
                hpaApi.deleteNamespacedHorizontalPodAutoscaler(this.getHpaName(), k8SImage.getNamespace(), K8SController.resultPrettyShow
                        , null, null, null, null, null);

            }
        } catch (ApiException e) {
            throw new RuntimeException("code:" + e.getCode() + ",reason:" + e.getResponseBody(), e);
        }
        File launchToken = this.getServerLaunchTokenFile();
        FileUtils.deleteQuietly(launchToken);
    }

    private K8SController getK8SController() {
        if (k8SController == null) {
            k8SController = new K8SController(this.getK8SImage(), this.getK8SApi());
        }
        return k8SController;
    }

    private ApiClient getK8SApi() {
        if (this.apiClient == null) {
            K8sImage k8SImage = this.getK8SImage();
            this.apiClient = k8SImage.createApiClient();
        }

        return this.apiClient;
    }

    @Override
    public void relaunch() {
        getK8SController().relaunch(K8S_INSTANCE_NAME);
    }

    @Override
    public RcDeployment getRCDeployment() {
        // ApiClient api = getK8SApi();//, K8sImage config, String tisInstanceName
        // return K8sIncrSync.getK8SDeploymentMeta(new CoreV1Api(getK8SApi()), this.getK8SImage(), K8S_INSTANCE_NAME);
        return getK8SController().getK8SDeploymentMeta(K8S_INSTANCE_NAME);
    }

    @Override
    public WatchPodLog listPodAndWatchLog(String podName, ILogListener listener) {
        return null;
    }

    @Override
    public void launchService() {
        if (inService()) {
            throw new IllegalStateException("k8s instance of:" + KEY_FIELD_NAME + " is running can not relaunch");
        }
        try {
            // 启动服务
//            ExponentialBackoffRetry retryPolicy = new ExponentialBackoffRetry(1000, 3);
//            CuratorFrameworkFactory.Builder curatorBuilder = CuratorFrameworkFactory.builder();
//            curatorBuilder.retryPolicy(retryPolicy);
            // this.client = curatorBuilder.connectString(this.zkAddress).build();




            K8sImage k8sImage = this.getK8SImage();
            // this.k8sClient = k8SImage.createApiClient();

            ReplicasSpec replicasSpec = this.getReplicasSpec();
            Objects.requireNonNull(replicasSpec, "replicasSpec can not be null");

            EnvVarsBuilder varsBuilder = new EnvVarsBuilder("tis-datax-executor") {
                @Override
                public String getAppOptions() {
                    // return "-D" + DataxUtils.DATAX_QUEUE_ZK_PATH + "=" + getZkQueuePath() + " -D" + DataxUtils.DATAX_ZK_ADDRESS + "=" + getZookeeperAddress();
                    return getZookeeperAddress() + " " + getZkQueuePath();
                }
            };
            //  K8sImage config, CoreV1Api api, String name, ReplicasSpec incrSpec, List< V1EnvVar > envs
            // CoreV1Api k8sV1Api = new CoreV1Api(k8sClient);
            //  K8sImage k8sImage = this.getK8SImage();
            this.getK8SController().createReplicationController(K8S_INSTANCE_NAME, replicasSpec, varsBuilder.build());

            if (supportHPA()) {
                HorizontalpodAutoscaler hap = this.getHpa();
                createHorizontalpodAutoscaler(k8sImage, hap);
            }

            File launchToken = this.getServerLaunchTokenFile();
            SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            FileUtils.write(launchToken, timeFormat.format(new Date()), TisUTF8.get());

        } catch (ApiException e) {
            logger.error(e.getResponseBody(), e);
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void createHorizontalpodAutoscaler(K8sImage k8sImage, HorizontalpodAutoscaler hap) throws Exception {
        Objects.requireNonNull(hap, "param HorizontalpodAutoscaler can not be null");

        AutoscalingV2beta1Api apiInstance = new AutoscalingV2beta1Api(this.getK8SApi());


        // String namespace = "namespace_example"; // String | object name and auth scope, such as for teams and projects
        V2beta1HorizontalPodAutoscaler body = new V2beta1HorizontalPodAutoscaler(); // V2beta1HorizontalPodAutoscaler |
        V1ObjectMeta meta = new V1ObjectMeta();
        meta.setName(getHpaName());
        body.setMetadata(meta);
        V2beta1CrossVersionObjectReference objectReference = null;
        V2beta1HorizontalPodAutoscalerSpec spec = new V2beta1HorizontalPodAutoscalerSpec();
        spec.setMaxReplicas(hap.getMaxPod());
        spec.setMinReplicas(hap.getMinPod());
        objectReference = new V2beta1CrossVersionObjectReference();
        objectReference.setApiVersion("extensions/v1beta1");
        objectReference.setKind("ReplicationController");
        objectReference.setName(K8S_INSTANCE_NAME);
        spec.setScaleTargetRef(objectReference);

        V2beta1MetricSpec monitorResource = new V2beta1MetricSpec();
        V2beta1ResourceMetricSource cpuResource = new V2beta1ResourceMetricSource();
        cpuResource.setName("cpu");
        cpuResource.setTargetAverageUtilization(hap.getCpuAverageUtilization());
        monitorResource.setResource(cpuResource);
        monitorResource.setType("Resource");
        spec.setMetrics(Collections.singletonList(monitorResource));
        body.setSpec(spec);


        String pretty = "pretty_example"; // String | If 'true', then the output is pretty printed.
        String dryRun = "dryRun_example"; // String | When present, indicates that modifications should not be persisted. An invalid or unrecognized dryRun directive will result in an error response and no further processing of the request. Valid values are: - All: all dry run stages will be processed
        String fieldManager = null; // String | fieldManager is a name associated with the actor or entity that is making these changes. The value must be less than or 128 characters long, and only contain printable characters, as defined by https://golang.org/pkg/unicode/#IsPrint.
        try {
            V2beta1HorizontalPodAutoscaler result = apiInstance.createNamespacedHorizontalPodAutoscaler(k8sImage.getNamespace(), body, null, null, null);
            // System.out.println(result);
            logger.info("NamespacedHorizontalPodAutoscaler created");
            logger.info(result.toString());
        } catch (ApiException e) {
            logger.error("Exception when calling AutoscalingV2beta1Api#createNamespacedHorizontalPodAutoscaler");
            logger.error("Status code: " + e.getCode());
            logger.error("Reason: " + e.getResponseBody());
            logger.error("Response headers: " + e.getResponseHeaders());
            // e.printStackTrace();
            throw e;
        }

    }

    private String getHpaName() {
        return K8S_INSTANCE_NAME + "-hpa";
    }


    @Override
    public String getZookeeperAddress() {
        return this.zkAddress;
    }

    @Override
    public K8sImage getK8SImage() {
        K8sImage k8sImage = TIS.getPluginStore(K8sImage.class).find(this.k8sImage);
        Objects.requireNonNull(k8sImage, "k8sImage:" + this.k8sImage + " can not be null");
        return k8sImage;
    }

    @Override
    public boolean inService() {
        File launchToken = this.getServerLaunchTokenFile();
        return launchToken.exists();
    }

    private File getServerLaunchTokenFile() {
        PluginStore<DataXJobWorker> worderStore = TIS.getPluginStore(DataXJobWorker.class);
        File target = worderStore.getTargetFile();
        return new File(target.getParentFile(), (target.getName() + ".launch_token"));
    }

    public static final Pattern zkhost_pattern = Pattern.compile("[\\da-z]{1}[\\da-z.]+:\\d+(/[\\da-z_\\-]{1,})*");
    public static final Pattern zk_path_pattern = Pattern.compile("(/[\\da-z]{1,})+");

    @TISExtension()
    public static class DescriptorImpl extends Descriptor<DataXJobWorker> {

        public DescriptorImpl() {
            super();
            this.registerSelectOptions(KEY_FIELD_NAME, () -> {
                PluginStore<K8sImage> images = TIS.getPluginStore(K8sImage.class);
                return images.getPlugins();
            });
        }

        public boolean validateZkQueuePath(IFieldErrorHandler msgHandler, Context context, String fieldName, String value) {
            Matcher matcher = zk_path_pattern.matcher(value);
            if (!matcher.matches()) {
                msgHandler.addFieldError(context, fieldName, "不符合规范:" + zk_path_pattern);
                return false;
            }
            return true;
        }

        public boolean validateZkAddress(IFieldErrorHandler msgHandler, Context context, String fieldName, String value) {
            Matcher matcher = zkhost_pattern.matcher(value);
            if (!matcher.matches()) {
                msgHandler.addFieldError(context, fieldName, "不符合规范:" + zkhost_pattern);
                return false;
            }
            return true;
        }

        @Override
        public String getDisplayName() {
            return "DataX-Worker";
        }
    }

}