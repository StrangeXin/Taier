package com.dtstack.rdos.engine.execution.base.queue;

import com.dtstack.rdos.commom.exception.RdosException;
import com.dtstack.rdos.common.config.ConfigParse;
import com.dtstack.rdos.engine.execution.base.CustomThreadFactory;
import com.dtstack.rdos.engine.execution.base.JobClient;
import com.dtstack.rdos.engine.execution.base.JobSubmitProcessor;
import com.dtstack.rdos.engine.execution.base.constrant.ConfigConstant;
import com.dtstack.rdos.engine.execution.base.enums.EngineType;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 管理任务执行队列
 * 当groupExeQueue为空之后回收
 * Date: 2018/1/11
 * Company: www.dtstack.com
 * @author xuchao
 */

public class ExeQueueMgr {

    private static final Logger LOG = LoggerFactory.getLogger(ExeQueueMgr.class);

    private Map<String, EngineTypeQueue> engineTypeQueueMap = Maps.newConcurrentMap();

    private ExecutorService executorService;

    private ExecutorService jobPool;

    private String localAddress = ConfigParse.getLocalAddress();

    private static ExeQueueMgr exeQueueMgr = new ExeQueueMgr();

    private ClusterQueueInfo clusterQueueInfo = ClusterQueueInfo.getInstance();

    private ExeQueueMgr(){

        //根据配置的引擎类型初始化engineTypeQueueMap
        List<String> typeList = Lists.newArrayList();
        ConfigParse.getEngineTypeList().forEach( info ->{
            String engineTypeStr = (String) info.get(ConfigParse.TYPE_NAME_KEY);
            engineTypeStr = EngineType.getEngineTypeWithoutVersion(engineTypeStr);
            typeList.add(engineTypeStr);
            engineTypeQueueMap.put(engineTypeStr, new EngineTypeQueue(engineTypeStr));
        });
        executorService =new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(), new CustomThreadFactory("timerClear"));
        executorService.submit(new TimerClear(typeList));
        jobPool = new ThreadPoolExecutor(3, 10, 60L, TimeUnit.SECONDS,
                new SynchronousQueue<>(true), new CustomThreadFactory("jobExecutor"));
    }

    public static ExeQueueMgr getInstance(){
        return exeQueueMgr;
    }

    public boolean add(JobClient jobClient) {
        boolean check = checkCanAddToWaitQueue(jobClient.getEngineType(),jobClient.getGroupName());
        if (check){
            EngineTypeQueue engineTypeQueue = engineTypeQueueMap.get(jobClient.getEngineType());
            engineTypeQueue.add(jobClient);
        }
        return check;
    }

    public boolean remove(String engineType, String groupName, String taskId){
        EngineTypeQueue engineTypeQueue = engineTypeQueueMap.get(engineType);
        if(engineTypeQueue == null){
            throw new RdosException("not support engineType:" + engineType);
        }

        return engineTypeQueue.remove(groupName, taskId);
    }

    /**
     * 获取当前节点的队列信息
     */
    public Map<String, Map<String, Integer>> getEngineTypePriorityInfo(){
        Map<String, Map<String, Integer>> engineTypePriority = Maps.newHashMap();
        engineTypeQueueMap.forEach((engineType, queue) -> engineTypePriority.put(engineType, queue.getGroupPriorityInfo()));
        return engineTypePriority;
    }


    private boolean checkCanAddToWaitQueue(String engineType, String groupName){
        if(engineType == null){
            return false;
        }
        EngineTypeQueue engineTypeQueue = engineTypeQueueMap.computeIfAbsent(engineType, k->new EngineTypeQueue(engineType));
        return engineTypeQueue.checkCanAddToWaitQueue(groupName);
    }

    private boolean checkLocalPriorityIsMax(String engineType, String groupName, String localAddress) {
        if(clusterQueueInfo.isEmpty()){
            //等待第一次从zk上获取信息
            return false;
        }

        ClusterQueueInfo.EngineTypeQueueInfo engineTypeQueueInfo = clusterQueueInfo.getEngineTypeQueueInfo(engineType);
        if(engineTypeQueueInfo == null){
            return true;
        }

        EngineTypeQueue engineTypeQueue = engineTypeQueueMap.get(engineType);
        if(engineTypeQueue == null){
            throw new RdosException("not support engineType:" + engineType);
        }

        return engineTypeQueue.checkLocalPriorityIsMax(groupName, localAddress, engineTypeQueueInfo);
    }

    public void checkQueueAndSubmit(){
        out:for(EngineTypeQueue engineTypeQueue : engineTypeQueueMap.values()){
            String engineType = engineTypeQueue.getEngineType();
            Map<String, GroupExeQueue> engineTypeQueueMap = engineTypeQueue.getGroupExeQueueMap();
            for (GroupExeQueue gq:engineTypeQueueMap.values()){
                JobClient jobClient = gq.getTop();
                try {
                    //队列为空
                    if (jobClient == null) {
                        return;
                    }
                    //判断该队列在集群里面是不是可以执行的--->保证同一个groupName的执行顺序一致
                    if (!checkLocalPriorityIsMax(engineType, gq.getGroupName(), localAddress)) {
                        return;
                    }
                    gq.remove(jobClient.getTaskId());
                    jobPool.submit(new JobSubmitProcessor(jobClient, ()-> gq.addJobClient(jobClient)));
                } catch (RejectedExecutionException e){
                    gq.addJobClient(jobClient);
                    break out;
                } catch (Exception e){
                    LOG.error("", e);
                }
            }
        }
    }

    class TimerClear implements Runnable{

        /**连续3次检查队列为空则回收*/
        private static final int FAILURE_RATE = 3;

        /**5s 检查一次队列*/
        private static final long CHECK_INTERVAL = 5 * 1000;

        /**TODO 调整成对象(engineType, groupName, value)*/
        private Map<String, Map<String, Integer>> cache = Maps.newHashMap();

        public TimerClear(List<String> engineTypeList){
            engineTypeList.forEach(type -> cache.put(type, Maps.newHashMap()));
        }

        @Override
        public void run() {

            LOG.info("timer clear start up...");

            while (true){

                try{
                    engineTypeQueueMap.forEach((engineType, engineTypeQueue) -> {
                        Map<String, Integer> engineTypeCache = cache.computeIfAbsent(engineType, key -> Maps.newHashMap());

                        engineTypeQueue.getGroupExeQueueMap().forEach((name, queue) ->{
                            int currVal = 0;
                            if(queue == null || queue.size() == 0){
                                currVal = engineTypeCache.getOrDefault(name, 0);
                                currVal++;
                            }

                            engineTypeCache.put(name, currVal);

                            //清理空的队列
                            Iterator<Map.Entry<String, Integer>> iterator = engineTypeCache.entrySet().iterator();
                            for( ;iterator.hasNext(); ){
                                Map.Entry<String, Integer> entry = iterator.next();
                                String groupName = entry.getKey();
                                if(groupName.equals(ConfigConstant.DEFAULT_GROUP_NAME)){
                                    continue;
                                }

                                if(entry.getValue() >= FAILURE_RATE){

                                    engineTypeQueue.remove(groupName);
                                    iterator.remove();
                                }
                            }
                        });

                    });
                }catch (Throwable t){
                    LOG.error("", t);
                }finally {
                    try {
                        Thread.sleep(CHECK_INTERVAL);
                    } catch (InterruptedException e) {
                        LOG.error("", e);
                    }
                }
            }
        }
    }

}
